package com.example.starter.file.service;

import com.example.starter.audit.service.AccessAudit;
import com.example.starter.audit.service.AuditWriter;
import com.example.starter.auth.model.AuthContext;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.common.web.ListQuery;
import com.example.starter.common.web.PageData;
import com.example.starter.common.web.UtcTimes;
import com.example.starter.file.mapper.FileMapper;
import com.example.starter.file.model.FileError;
import com.example.starter.file.model.FileInfo;
import com.example.starter.file.model.StoredFile;
import com.example.starter.file.storage.FileStore;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** 본인 파일만 공개하며 DB commit과 디스크 실패를 각각 확인한다. */
@Service
public class FileService {
    private static final Logger LOG = LoggerFactory.getLogger(FileService.class);
    private final FileMapper files;
    private final FileStore store;
    private final ImageFiles images;
    private final UploadAdmission admission;
    private final AuditWriter audit;
    private final Clock clock;
    private final TransactionTemplate transaction;

    /** 이미지/파일 처리는 DB transaction 밖에서 하고 짧은 DB 단계만 별도로 실행한다. */
    public FileService(
            FileMapper files,
            FileStore store,
            ImageFiles images,
            UploadAdmission admission,
            AuditWriter audit,
            Clock clock,
            PlatformTransactionManager manager) {
        this.files = files;
        this.store = store;
        this.images = images;
        this.admission = admission;
        this.audit = audit;
        this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
    }

    /** 파일 입력에 owner/key/status를 받지 않고 검증된 현재 주체로만 저장한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public FileInfo upload(MultipartFile input) {
        long owner = AuthContext.require().userId();

        try (var permit = admission.enter()) {
            var image = images.prepare(input);
            String id = UUID.randomUUID().toString();
            var row =
                    new StoredFile(
                            id,
                            owner,
                            id + "." + image.extension(),
                            image.originalName(),
                            image.contentType(),
                            image.bytes().length,
                            image.width(),
                            image.height(),
                            UtcTimes.millis(clock.instant()),
                            "PENDING");
            Completion reserved = new Completion();

            try {
                inTransaction(
                        reserved,
                        () -> {
                            files.ensureQuota(owner);

                            if (files.reserve(owner, row.size()) != 1) {
                                throw new ApiException(FileError.FILE_QUOTA_EXCEEDED);
                            }

                            if (files.insert(row) != 1) {
                                throw new ApiException(FileError.FILE_METADATA_UNAVAILABLE);
                            }

                            return null;
                        });
            } catch (RuntimeException failure) {
                if (!reserved.rolledBack()) {
                    uncertain(id, failure);
                }

                throw metadata(failure);
            }

            try {
                store.save(row.storageKey(), image.bytes());
            } catch (FileStore.WriteFailure failure) {
                if (!failure.residue()) {
                    cancel(row, failure);
                } else {
                    uncertain(id, failure);
                }

                throw new ApiException(FileError.FILE_STORAGE_UNAVAILABLE, failure);
            } catch (RuntimeException failure) {
                uncertain(id, failure);

                throw new ApiException(FileError.FILE_STORAGE_UNAVAILABLE, failure);
            }

            Completion completed = new Completion();

            try {
                inTransaction(
                        completed,
                        () -> {
                            if (files.ready(id, owner) != 1) {
                                throw new ApiException(FileError.FILE_METADATA_UNAVAILABLE);
                            }

                            audit.accessInTransaction("FILE", "CREATE", id);

                            return null;
                        });
            } catch (RuntimeException failure) {
                if (completed.rolledBack()) {
                    try {
                        store.delete(row.storageKey());
                        cancel(row, failure);
                    } catch (RuntimeException cleanup) {
                        failure.addSuppressed(cleanup);
                        uncertain(id, failure);
                    }
                } else {
                    uncertain(id, failure);
                }

                throw metadata(failure);
            }

            return FileInfo.from(row);
        }
    }

    /** 목록과 count 모두 서버가 확인한 소유자와 READY 상태만 사용한다. */
    @AccessAudit(
            resource = AccessAudit.Resource.FILE,
            action = AccessAudit.Action.SEARCH,
            policy = AccessAudit.Policy.REQUIRED)
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public PageData<FileInfo> list(int page, int size) {
        long owner = AuthContext.require().userId();
        var query = new ListQuery(null, page, size, ListQuery.Sort.idAsc);

        return PageData.of(
                files.selectList(owner, size, query.offset()).stream().map(FileInfo::from).toList(),
                page,
                size,
                files.count(owner));
    }

    /** 필수 종료 감사에 실패하면 이미 연 스트림도 닫고 결과를 보내지 않는다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public Download download(String fileId) {
        String id = id(fileId);
        long owner = AuthContext.require().userId();
        String operation = UUID.randomUUID().toString();
        audit.access("FILE", "READ", null, "STARTED", null, operation);
        InputStream stream = null;

        try {
            var row = owned(id, owner);

            if (!row.status().equals("READY")) {
                throw new ApiException(ApiError.NOT_FOUND);
            }

            ImageFiles.filename(row.originalName());
            stream = store.open(row.storageKey(), row.size());
            audit.access("FILE", "READ", null, "SUCCESS", null, operation);

            return new Download(FileInfo.from(row), stream);
        } catch (RuntimeException failure) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException close) {
                    failure.addSuppressed(close);
                }
            }

            try {
                audit.access("FILE", "READ", null, "FAILURE", "FILE_READ_FAILED", operation);
            } catch (RuntimeException unavailable) {
                failure.addSuppressed(unavailable);

                throw unavailable;
            }

            throw failure;
        }
    }

    /** 파일 삭제와 quota 반환이 모두 확인돼야204를 반환하며 DELETING은 재시도할 수 있다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public void delete(String fileId) {
        String id = id(fileId);
        long owner = AuthContext.require().userId();
        var row = owned(id, owner);

        if (!Set.of("READY", "DELETING").contains(row.status())) {
            throw new ApiException(ApiError.NOT_FOUND);
        }

        try {
            inTransaction(
                    new Completion(),
                    () -> {
                        if (files.deleting(id, owner) != 1) {
                            var latest = files.selectOwned(id, owner);

                            if (latest != null && !latest.status().equals("DELETING")) {
                                throw new ApiException(FileError.FILE_METADATA_UNAVAILABLE);
                            }
                        }

                        return null;
                    });
        } catch (RuntimeException failure) {
            throw metadata(failure);
        }

        try {
            store.delete(row.storageKey());
        } catch (RuntimeException failure) {
            uncertain(id, failure);

            throw new ApiException(FileError.FILE_STORAGE_UNAVAILABLE, failure);
        }

        try {
            inTransaction(
                    new Completion(),
                    () -> {
                        int count = files.delete(id, owner, "DELETING");

                        if (count == 1) {
                            if (files.release(owner, row.size()) != 1) {
                                throw new ApiException(FileError.FILE_METADATA_UNAVAILABLE);
                            }

                            audit.accessInTransaction("FILE", "DELETE", id);
                        } else if (files.selectOwned(id, owner) != null) {
                            throw new ApiException(FileError.FILE_METADATA_UNAVAILABLE);
                        }

                        return null;
                    });
        } catch (RuntimeException failure) {
            uncertain(id, failure);

            throw metadata(failure);
        }
    }

    /** UUID 축약 표현이나 경로 조각을 받지 않는다. */
    public static String id(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException();
            }

            return value;
        } catch (RuntimeException bad) {
            throw new ApiException(ApiError.VALIDATION_FAILED);
        }
    }

    /** 다른 소유자와 존재하지 않는 파일을 같은404로 처리한다. */
    private StoredFile owned(String id, long owner) {
        var row = files.selectOwned(id, owner);

        if (row == null) {
            throw new ApiException(ApiError.NOT_FOUND);
        }

        return row;
    }

    /** 미공개 행을 삭제했을 때만 같은 transaction에서 예약량을 반환한다. */
    private void cancel(StoredFile row, RuntimeException original) {
        try {
            inTransaction(
                    new Completion(),
                    () -> {
                        if (files.delete(row.fileId(), row.ownerUserId(), "PENDING") == 1
                                && files.release(row.ownerUserId(), row.size()) != 1) {
                            throw new ApiException(FileError.FILE_METADATA_UNAVAILABLE);
                        }

                        return null;
                    });
        } catch (RuntimeException cleanup) {
            original.addSuppressed(cleanup);
            uncertain(row.fileId(), original);
        }
    }

    /** afterCompletion으로 rollback 확정과 commit 불명을 구분하며 추측으로 파일을 지우지 않는다. */
    private <T> T inTransaction(Completion completion, Supplier<T> action) {
        return transaction.execute(
                status -> {
                    completion.started = true;
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                                /** 실제 종료 상태만 기록하고 commit 성공을 추정하지 않는다. */
                                @Override
                                public void afterCompletion(int state) {
                                    completion.state.set(state);
                                }
                            });

                    return action.get();
                });
    }

    /** 기존 공개 오류는 유지하고 나머지 DB 예외는 안전한503으로 바꾼다. */
    private RuntimeException metadata(RuntimeException failure) {
        return failure instanceof ApiException
                ? failure
                : new ApiException(FileError.FILE_METADATA_UNAVAILABLE, failure);
    }

    /** 원본명·경로·내용 대신 확인할 생성ID만 기록한다. */
    private void uncertain(String id, Throwable failure) {
        LOG.error("file_state_requires_verification operationId={}", id, failure);
    }

    /** 단계 시작 전 실패와 확정rollback만 보상 가능으로 판단한다. */
    private static final class Completion {
        boolean started;
        final AtomicInteger state = new AtomicInteger(TransactionSynchronization.STATUS_UNKNOWN);

        /** 시작 전 실패 또는 확정된 rollback에서만 보상을 허용한다. */
        boolean rolledBack() {
            return !started || state.get() == TransactionSynchronization.STATUS_ROLLED_BACK;
        }
    }

    /** 열린 핸들은 Spring Resource converter가 정상/실패 모두에서 닫는다. */
    public record Download(FileInfo info, InputStream stream) {
        /** 파일 이름과 열린 핸들을 진단 문자열에 노출하지 않는다. */
        @Override
        public String toString() {
            return "FileDownload[redacted]";
        }
    }
}
