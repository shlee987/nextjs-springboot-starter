'use client';

import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { getAuthenticationGeneration } from '@/api/client';
import { deleteFile, downloadFile, uploadFile, useSearchFiles } from '@/api/generated/client';
import type { FileInfo } from '@/api/generated/models';
import { normalizeApiError, type ApiError } from '@/api/errors';
import { saveFileDownload } from '@/api/fileDownload';
import { Button } from '@/components/ui/Button';
import { FileUpload, type UploadRequest } from '@/components/ui/FileUpload';
import { EmptyState, ErrorState, Spinner } from '@/components/ui/Feedback';
import { ConfirmDialog } from '@/components/ui/Overlays';
import { Pagination } from '@/components/ui/Search';
import { PermissionGuard, useSession } from '@/features/auth/AuthProvider';
import { pageSchema, readEnvelope } from '@/features/data/contracts';
import { useAction } from '@/providers/ActionProvider';
import { toDisplayDate } from '@/lib/dates';
import { fileInfoSchema } from './fileContracts';
import styles from '@/components/ui/ui.module.css';

/** 본인 파일만 조회하며 인증 경계의 재마운트 없이 선택·미리보기·페이지·캐시를 세대별로 분리한다. */
export function FilesPanel() {
  const { generation, status } = useSession();
  const queryClient = useQueryClient();
  const [view, setView] = useState({ generation, page: 0, size: 20 });
  const paging = {
    page: view.generation === generation ? view.page : 0,
    size: view.generation === generation ? view.size : 20,
  };
  const query = useSearchFiles(paging, {
    query: {
      queryKey: ['files', generation, paging],
      enabled: status === 'authenticated',
      refetchOnWindowFocus: false,
      select: (response) => readEnvelope(pageSchema(fileInfoSchema), response),
    },
  });
  const refreshAction = useAction('files:refresh');

  /** 완료·실패·취소 뒤 본인 목록을 다시 조회한다. 이전 사용자의 캐시는 갱신하지 않는다. */
  function refreshFiles() {
    if (getAuthenticationGeneration() === generation) {
      void queryClient.invalidateQueries({ queryKey: ['files', generation] });
    }
  }

  /** multipart 한 파일을 실제 전송하며 Axios 이벤트의 바이트로만 진행률을 알린다. */
  async function handleUpload({ file, signal, onProgress }: UploadRequest) {
    try {
      const response = await uploadFile(
        { file },
        {
          signal,
          onUploadProgress: (event) =>
            onProgress(event.loaded, event.lengthComputable ? event.total : undefined),
        },
      );
      readEnvelope(fileInfoSchema, response);
    } finally {
      refreshFiles();
    }
  }

  /** 새로고침·재시도의 중복 요청은 같은 키로 무시하고 조회 오류는 기존 Query 상태로 표시한다. */
  async function handleRefresh() {
    await refreshAction.run(() => query.refetch());
  }

  return (
    <div className={styles.pageStack}>
      <FileUpload
        key={generation}
        actionKey="files:upload"
        upload={status === 'authenticated' ? handleUpload : undefined}
        unavailableMessage={
          status === 'authenticated'
            ? undefined
            : status === 'checking'
              ? '로그인 상태를 확인하고 있어요.'
              : status === 'error'
                ? '로그인 상태를 확인하지 못했어요.'
                : '로그인 후 업로드할 수 있어요.'
        }
      />
      <section className={styles.savedFiles} aria-label="내 파일 목록">
        <div className={styles.fileListHeading}>
          <h3>내 파일</h3>
          <Button
            size="small"
            disabled={status !== 'authenticated'}
            loading={refreshAction.pending}
            onClick={() => void handleRefresh()}
          >
            목록 새로고침
          </Button>
        </div>
        <PermissionGuard>
          {query.isPending ? (
            <Spinner label="내 파일 불러오는 중" />
          ) : query.isError ? (
            <ErrorState
              message={query.error.message}
              traceId={query.error.traceId}
              retrying={refreshAction.pending}
              onRetry={() => void handleRefresh()}
            />
          ) : (
            query.data && (
              <>
                {!query.data.data.items.length ? (
                  <EmptyState message="저장한 파일이 없어요. 위에서 이미지를 선택해 업로드하세요." />
                ) : (
                  <ul className={styles.fileList} aria-label="저장한 파일">
                    {query.data.data.items.map((file) => (
                      <SavedFile key={file.fileId} file={file} onDeleted={refreshFiles} />
                    ))}
                  </ul>
                )}
                <Pagination
                  page={paging.page}
                  size={paging.size}
                  totalElements={query.data.data.totalElements}
                  disabled={query.isFetching}
                  onChange={(page, size) => setView({ generation, page, size })}
                  showSize
                />
              </>
            )
          )}
        </PermissionGuard>
      </section>
    </div>
  );
}

/** 다운로드는 캐시 없이 명시 클릭으로 저장하고 삭제는 성공 응답 후에만 확인창을 닫는다. */
function SavedFile({ file, onDeleted }: { file: FileInfo; onDeleted: () => void }) {
  const download = useAction(`files:download:${file.fileId}`);
  const deletion = useAction(`files:delete:${file.fileId}`);
  const [confirm, setConfirm] = useState(false);
  const [error, setError] = useState<ApiError>();

  /** 바이너리와 검증된 attachment 이름을 받은 뒤 현재 작업 범위에서만 저장을 시작한다. */
  async function handleDownload() {
    setError(undefined);

    try {
      const result = await download.run(async (signal) => {
        let fileName = 'image';
        const blob = await downloadFile(file.fileId, {
          signal,
          onDownloadFileName: (name) => {
            fileName = name;
          },
        });

        return { blob, fileName };
      });

      if (result.status === 'completed') saveFileDownload(result.value.blob, result.value.fileName);
    } catch (failure) {
      setError(normalizeApiError(failure));
    }
  }

  /** 확인한 파일만 삭제하며 204 전에는 목록에서 성공으로 제거하지 않는다. */
  async function handleDelete(signal: AbortSignal) {
    await deleteFile(file.fileId, { signal });
    onDeleted();
  }

  return (
    <li className={styles.savedFile}>
      <div className={styles.fileDetails}>
        <strong className={styles.fileName}>{file.originalName}</strong>
        <div className={styles.fileMeta}>
          <span>{(file.size / 1024).toFixed(1)} KiB</span>
          <span>
            {file.width} × {file.height}
          </span>
          <span>{toDisplayDate(file.createdAt, 'millisecond', 'Asia/Seoul')} (서울)</span>
        </div>
        {error && <ErrorState message={error.message} traceId={error.traceId} />}
      </div>
      <div className={styles.fileActions}>
        <Button
          size="small"
          loading={download.pending}
          onClick={() => void handleDownload()}
          aria-label={`${file.originalName} 다운로드`}
        >
          다운로드
        </Button>
        <Button
          size="small"
          variant="text"
          disabled={deletion.pending}
          onClick={() => setConfirm(true)}
          aria-label={`${file.originalName} 삭제`}
        >
          삭제
        </Button>
      </div>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title="파일을 삭제할까요?"
        description="삭제한 파일은 다시 다운로드할 수 없습니다."
        confirmLabel="삭제"
        intent="danger"
        autoClose
        actionKey={`files:delete:${file.fileId}`}
        onConfirm={handleDelete}
      >
        <p className={styles.fileName}>{file.originalName}</p>
      </ConfirmDialog>
    </li>
  );
}
