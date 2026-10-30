package com.example.starter.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.TestPolicies;
import com.example.starter.audit.mapper.AuditMapper;
import com.example.starter.audit.model.AuditEvent;
import com.example.starter.audit.service.AuditWriter;
import com.example.starter.auth.model.AuthLogin;
import com.example.starter.common.web.ApiError;
import com.example.starter.common.web.ApiException;
import com.example.starter.file.mapper.FileMapper;
import com.example.starter.file.model.FileError;
import com.example.starter.file.model.StoredFile;
import com.example.starter.file.service.FileService;
import com.example.starter.file.service.ImageFiles;
import com.example.starter.file.service.UploadAdmission;
import com.example.starter.file.storage.FileStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

/** DB 없는 실패 주입이며 실제 PostgreSQL commit/경쟁의 증거와 구분한다. */
class FileServiceTest {
    /** 시험에서 사용한 인증 주체를 다음 실행에 남기지 않는다. */
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** 현재 주체만 목록/바이트/삭제에 접근하며 다른 사용자는404다. */
    @Test
    void readyFilesAreOwnedAndDeletingReturnsQuotaOnlyOnce() throws Exception {
        var f = new Fixture();
        actor(1);
        var file = f.service.upload(f.input());
        assertEquals(3, file.size());
        assertEquals(3, f.db.used);
        assertEquals(1, f.service.list(0, 20).totalElements());
        actor(2);
        assertEquals(0, f.service.list(0, 20).totalElements());
        assertEquals(
                ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> f.service.download(file.fileId())).error());
        assertEquals(
                ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> f.service.delete(file.fileId())).error());
        actor(1);
        var download = f.service.download(file.fileId());
        assertEquals(1, download.stream().read());

        try {
            download.stream().close();
        } catch (IOException ignored) {
        }

        f.service.delete(file.fileId());
        assertEquals(0, f.db.used);
        assertTrue(f.store.data.isEmpty());
        assertEquals(
                ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> f.service.delete(file.fileId())).error());
    }

    /** 입력 거부는 메타데이터나 용량을 사용하지 않는다. */
    @Test
    void invalidImageNeverTouchesDatabaseOrStorage() {
        var f = new Fixture(new ImageFiles());
        actor(1);
        assertThrows(
                ApiException.class,
                () ->
                        f.service.upload(
                                new MockMultipartFile(
                                        "file", "fake.png", "image/png", "<svg>".getBytes())));
        assertEquals(0, f.db.calls);
        assertTrue(f.store.data.isEmpty());
    }

    /** 확정 rollback과 잔류 없는 저장 실패는 예약량을 돌려준다. */
    @Test
    void storageFailureAndReadyRollbackLeaveNoPublicFileOrQuota() {
        for (String failure : List.of("write", "ready", "insert")) {
            var f = new Fixture();
            actor(1);
            f.store.failWrite = failure.equals("write");
            f.db.failReady = failure.equals("ready");
            f.db.failInsert = failure.equals("insert");
            assertThrows(ApiException.class, () -> f.service.upload(f.input()));
            assertEquals(0, f.db.used, failure);
            assertTrue(f.db.rows.isEmpty(), failure);
            assertTrue(f.store.data.isEmpty(), failure);
        }
    }

    /** 잔류 또는 commit 불명에서는 추측으로 파일이나 용량을 지우지 않는다. */
    @Test
    void residueAndUnknownCommitRemainConsistentForRecovery() {
        var f = new Fixture();
        actor(1);
        f.store.residue = true;
        assertThrows(ApiException.class, () -> f.service.upload(f.input()));
        assertEquals(3, f.db.used);
        assertEquals("PENDING", f.db.rows.values().iterator().next().status());
        assertEquals(0, f.service.list(0, 20).totalElements());
        Fixture committed = new Fixture();
        committed.tx.failCommitAt = 2;
        assertThrows(ApiException.class, () -> committed.service.upload(committed.input()));
        assertEquals(3, committed.db.used);
        assertEquals("READY", committed.db.rows.values().iterator().next().status());
        assertEquals(1, committed.store.data.size());
    }

    /** 실패한 삭제는 DELETING 상태로 재시도할 수 있다. */
    @Test
    void deleteFailureIsNotSuccessAndCanBeRetriedWithoutDoubleRelease() {
        var f = new Fixture();
        actor(1);
        var file = f.service.upload(f.input());
        f.store.failDelete = true;
        assertThrows(ApiException.class, () -> f.service.delete(file.fileId()));
        assertEquals("DELETING", f.db.rows.get(file.fileId()).status());
        assertEquals(3, f.db.used);
        assertEquals(0, f.service.list(0, 20).totalElements());
        f.store.failDelete = false;
        f.db.failRelease = true;
        assertThrows(ApiException.class, () -> f.service.delete(file.fileId()));
        assertEquals(3, f.db.used);
        assertEquals("DELETING", f.db.rows.get(file.fileId()).status());
        f.db.failRelease = false;
        f.service.delete(file.fileId());
        assertEquals(0, f.db.used);
        assertTrue(f.db.rows.isEmpty());
    }

    /** 필수 종료 감사 실패 시 열린 스트림을 닫는다. */
    @Test
    void failedTerminalAuditClosesDownloadHandle() {
        var f = new Fixture();
        actor(1);
        var file = f.service.upload(f.input());
        f.failAudit = true;
        assertThrows(ApiException.class, () -> f.service.download(file.fileId()));
        assertTrue(f.store.closed);
    }

    /** 동일 소유자의 두 예약이 조건부 갱신을 모사한 잠금에서도 상한을 넘지 않는다. */
    @Test
    void concurrentQuotaReservationsAndGlobalProcessingAreBounded() throws Exception {
        var f =
                new Fixture(
                        new ImageFiles() {
                            /** 시험용 이미지 크기와 바이트만 공급하며 실제 디코더를 대신한다. */
                            @Override
                            public Prepared prepare(MultipartFile input) {
                                return new Prepared(
                                        "sample.png", "image/png", "png", 1, 1, new byte[5242880]);
                            }
                        });
        f.db.used = 95L * 1024 * 1024;

        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> upload =
                    () -> {
                        actor(1);
                        start.await();

                        try {
                            f.service.upload(f.input());

                            return 201;
                        } catch (ApiException denied) {
                            return denied.error().status().value();
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    };
            var a = pool.submit(upload);
            var b = pool.submit(upload);
            start.countDown();
            assertEquals(List.of(201, 409), Stream.of(a.get(), b.get()).sorted().toList());
            assertEquals(100L * 1024 * 1024, f.db.used);
        }

        var gate = new UploadAdmission();

        try (var nested = gate.enter();
                var same = gate.enter()) {}

        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            Runnable task =
                    () -> {
                        try (var lease = gate.enter()) {
                            entered.countDown();

                            try {
                                release.await();
                            } catch (InterruptedException stop) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    };
            var a = pool.submit(task);
            var b = pool.submit(task);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(
                    429, assertThrows(ApiException.class, gate::enter).error().status().value());
            release.countDown();
            a.get();
            b.get();
        }

        try (var again = gate.enter()) {}
    }

    /** 시험 주체의 rawsid 등은 로그로 출력하지 않는다. */
    static void actor(long id) {
        var login =
                new AuthLogin(
                        UUID.randomUUID().toString(),
                        id,
                        0,
                        0,
                        0,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        null,
                        null,
                        "[]",
                        "testhash");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(login, null, List.of()));
    }

    /** 실행 순서와 복구 상태를 관찰하는 메모리 시험 fixture다. */
    static class Fixture {
        final Db db = new Db();
        final Tx tx = new Tx(db);
        final Store store = new Store();
        final ConcurrentLinkedQueue<AuditEvent> events = new ConcurrentLinkedQueue<>();
        final FileService service;
        boolean failAudit;

        /** DB 없이 서비스와 감사·저장소 대역을 연결한다. */
        Fixture() {
            this(
                    new ImageFiles() {
                        /** 시험용 이미지 크기와 바이트만 공급하며 실제 디코더를 대신한다. */
                        @Override
                        public Prepared prepare(MultipartFile input) {
                            return new Prepared(
                                    "sample.png", "image/png", "png", 1, 1, new byte[] {1, 2, 3});
                        }
                    });
        }

        /** DB 없이 서비스와 감사·저장소 대역을 연결한다. */
        Fixture(ImageFiles images) {
            AuditMapper mapper =
                    (AuditMapper)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {AuditMapper.class},
                                    (target, method, args) -> {
                                        if (failAudit
                                                && args[0] instanceof AuditEvent event
                                                && event.outcome().equals("SUCCESS")) {
                                            throw new IllegalStateException(
                                                    "Synthetic audit failure");
                                        }

                                        if (args[0] instanceof AuditEvent event) {
                                            events.add(event);
                                        }

                                        return 1;
                                    });
            var audit =
                    new AuditWriter(
                            mapper,
                            Clock.systemUTC(),
                            TestPolicies.local(),
                            tx,
                            TestPolicies.references());
            service =
                    new FileService(
                            db, store, images, new UploadAdmission(), audit, Clock.systemUTC(), tx);
        }

        /** 서버 내부 경로가 없는 합성 multipart 파일을 만든다. */
        MockMultipartFile input() {
            return new MockMultipartFile("file", "sample.png", "image/png", new byte[] {1});
        }
    }

    /** 저장 실패의 잔류 여부와 스트림 닫힘을 관찰한다. */
    static class Store implements FileStore {
        final Map<String, byte[]> data = new ConcurrentHashMap<>();
        int opened;
        int closeCount;
        boolean failWrite, residue, failDelete, closed;

        /** 메모리 저장 결과와 잔류 가능성이 있는 실패를 모사한다. */
        public void save(String key, byte[] bytes) {
            if (failWrite) {
                throw new WriteFailure(false, new IOException());
            }

            data.put(key, bytes);

            if (residue) {
                throw new WriteFailure(true, new IOException());
            }
        }

        /** 메모리 파일을 열고 닫힘 여부를 관찰한다. */
        public InputStream open(String key, long size) {
            opened++;
            return new ByteArrayInputStream(data.get(key)) {
                /** 시험에서 열린 핸들이 해제됐음을 기록한다. */
                @Override
                public void close() {
                    closed = true;
                    closeCount++;
                }
            };
        }

        /** 소유자·상태 조건의 삭제 또는 디스크 삭제 실패를 모사한다. */
        public void delete(String key) {
            if (failDelete) {
                throw new ApiException(FileError.FILE_STORAGE_UNAVAILABLE);
            }

            data.remove(key);
        }
    }

    /** 조건 갱신과 상태를 재현하며 PostgreSQL 시험을 대신하지 않는다. */
    static class Db implements FileMapper {
        long used;
        int calls;
        boolean failReady, failInsert, failRelease;
        Map<String, StoredFile> rows = new HashMap<>();

        /** 예약 단계 진입을 기록하며 최초 quota 행을 모사한다. */
        public int ensureQuota(long owner) {
            calls++;

            return 1;
        }

        /** 조건부 갱신과 같은 상한 검사 뒤 예약량을 늘린다. */
        public int reserve(long owner, long size) {
            if (used > 104857600 - size) {
                return 0;
            }

            used += size;

            return 1;
        }

        /** 잔여량과 실패 조건을 검사한 뒤 예약량을 한 번 반환한다. */
        public int release(long owner, long size) {
            if (failRelease) {
                throw new IllegalStateException("Synthetic DB failure");
            }

            if (used < size) {
                return 0;
            }

            used -= size;

            return 1;
        }

        /** 시험용 PENDING 행을 보존하며 저장 실패를 주입할 수 있다. */
        public int insert(StoredFile row) {
            if (failInsert) {
                throw new IllegalStateException("Synthetic insert failure");
            }

            rows.put(row.fileId(), row);

            return 1;
        }

        /** 현재 소유자가 같은 시험 행만 반환한다. */
        public StoredFile selectOwned(String id, long owner) {
            var row = rows.get(id);

            return row != null && row.ownerUserId() == owner ? row : null;
        }

        /** 본인의 READY 행만 페이지 범위에서 반환한다. */
        public List<StoredFile> selectList(long owner, int size, long offset) {
            return rows.values().stream()
                    .filter(row -> row.ownerUserId() == owner && row.status().equals("READY"))
                    .skip(offset)
                    .limit(size)
                    .toList();
        }

        /** 동일 소유자·완료 조건의 시험 행 수를 반환한다. */
        public long count(long owner) {
            return selectList(owner, Integer.MAX_VALUE, 0).size();
        }

        /** 실패 주입 또는 PENDING의 공개 상태 전이를 모사한다. */
        public int ready(String id, long owner) {
            if (failReady) {
                throw new IllegalStateException("Synthetic ready failure");
            }

            return change(id, owner, "PENDING", "READY");
        }

        /** READY 파일을 삭제 진행 상태로 숨긴다. */
        public int deleting(String id, long owner) {
            return change(id, owner, "READY", "DELETING");
        }

        /** 소유자와 이전 상태가 일치할 때만 시험 행을 교체한다. */
        int change(String id, long owner, String before, String after) {
            var row = selectOwned(id, owner);

            if (row == null || !row.status().equals(before)) {
                return 0;
            }

            rows.put(
                    id,
                    new StoredFile(
                            id,
                            owner,
                            row.storageKey(),
                            row.originalName(),
                            row.contentType(),
                            row.size(),
                            row.width(),
                            row.height(),
                            row.createdAt(),
                            after));

            return 1;
        }

        /** 소유자·상태 조건의 삭제 또는 디스크 삭제 실패를 모사한다. */
        public int delete(String id, long owner, String status) {
            var row = selectOwned(id, owner);

            if (row == null || !row.status().equals(status)) {
                return 0;
            }

            rows.remove(id);

            return 1;
        }
    }

    /** 실제 Spring 동기화로 callback과 확정 rollback/commit 불명을 시험한다. */
    static class Tx extends AbstractPlatformTransactionManager {
        final Db db;
        final ReentrantLock lock = new ReentrantLock();
        int commits, failCommitAt;

        /** 실제 Spring transaction callback을 메모리 snapshot에 연결한다. */
        Tx(Db db) {
            this.db = db;
        }

        static class Snapshot {
            long used;
            Map<String, StoredFile> rows;
        }

        /** 이번 transaction의 복구 snapshot을 만든다. */
        protected Object doGetTransaction() {
            return new Snapshot();
        }

        /** 동시 예약을 직렬화하고 rollback용 상태를 보관한다. */
        protected void doBegin(Object obj, TransactionDefinition definition) {
            lock.lock();
            var s = (Snapshot) obj;
            s.used = db.used;
            s.rows = new HashMap<>(db.rows);
        }

        /** 지정한 commit 단계에서 결과 불명을 재현한다. */
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;

            if (commits == failCommitAt) {
                throw new TransactionSystemException("Synthetic unknown commit");
            }
        }

        /** 메모리 메타데이터와 quota를 시작 상태로 되돌린다. */
        protected void doRollback(DefaultTransactionStatus status) {
            var s = (Snapshot) status.getTransaction();
            db.used = s.used;
            db.rows = s.rows;
        }

        /** 종료 callback 이후 시험 잠금을 해제한다. */
        protected void doCleanupAfterCompletion(Object obj) {
            lock.unlock();
        }
    }
}
