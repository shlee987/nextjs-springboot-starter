'use client';

import { useEffect, useId, useRef, useState } from 'react';
import { normalizeApiError } from '@/api/errors';
import { useAction } from '@/providers/ActionProvider';
import { Button } from './Button';
import { Icon } from './Icon';
import styles from './ui.module.css';

export type UploadRequest = {
  file: File;
  signal: AbortSignal;
  /** 실제 전송 계층의 loaded/total이며 전체 크기를 모르면 total을 생략한다. */
  onProgress: (loaded: number, total?: number) => void;
};
export type FileUploadProps = {
  /** 실제 서버 저장이 완료된 뒤에만 resolve해야 한다. 변경 요청을 자동 재전송하지 않는다. */
  upload?: (request: UploadRequest) => Promise<void>;
  actionKey: string;
  label?: string;
  maxFiles?: number;
  maxBytes?: number;
  concurrency?: number;
  formats?: readonly { mime: string; extensions: readonly string[] }[];
  disabled?: boolean;
  unavailableMessage?: string;
};
const imageFormats = [
  { mime: 'image/png', extensions: ['png'] },
  { mime: 'image/jpeg', extensions: ['jpg', 'jpeg'] },
] as const;
type UploadItem = {
  id: string;
  file: File;
  status:
    | 'selected'
    | 'invalid'
    | 'queued'
    | 'uploading'
    | 'processing'
    | 'saved'
    | 'error'
    | 'cancelled';
  progress?: number;
  error?: string;
  traceId?: string;
  cancelling?: boolean;
};
const statusLabels: Record<UploadItem['status'], string> = {
  selected: '업로드 대기',
  invalid: '선택 오류',
  queued: '전송 대기',
  uploading: '전송 중',
  processing: '서버 저장 중',
  saved: '서버 저장 완료',
  error: '업로드 실패',
  cancelled: '전송 취소됨',
};

/** 형식·크기 안내 검사를 통과한 로컬 이미지에 미리보기 URL을 만들고 해제 시 정리한다. */
function FilePreview({ file }: { file: File }) {
  const image = useRef<HTMLImageElement>(null);

  useEffect(() => {
    const objectUrl = URL.createObjectURL(file);
    const element = image.current;

    if (element) element.src = objectUrl;

    return () => {
      element?.removeAttribute('src');
      URL.revokeObjectURL(objectUrl);
    };
  }, [file]);

  // 사용자 URL이나 원격 이미지를 받지 않으며 로컬 blob만 사용한다.
  // eslint-disable-next-line @next/next/no-img-element
  return <img ref={image} className={styles.filePreview} alt="선택 이미지 미리보기" />;
}

/** 파일 선택과 제한된 병렬 전송을 제공한다. 항목 제거는 서버 파일 삭제가 아니다. */
export function FileUpload({
  upload,
  actionKey,
  label = '업로드할 이미지',
  maxFiles = 5,
  maxBytes = 5 * 1024 * 1024,
  concurrency = 2,
  formats = imageFormats,
  disabled = false,
  unavailableMessage = '파일 업로드를 준비하고 있어요.',
}: FileUploadProps) {
  const inputId = useId();
  const input = useRef<HTMLInputElement>(null);
  const [items, setItems] = useState<UploadItem[]>([]);
  const itemsRef = useRef(items);
  const [selectionError, setSelectionError] = useState('');
  const [dragging, setDragging] = useState(false);
  const active = useRef(new Map<string, AbortController>());
  const cancelled = useRef(new Set<string>());
  const batch = useAction(actionKey);
  const blocked = disabled || batch.pending;
  const accept = formats
    .flatMap((format) => [format.mime, ...format.extensions.map((ext) => `.${ext}`)])
    .join(',');

  useEffect(() => {
    const controllers = active.current;

    return () => controllers.forEach((controller) => controller.abort());
  }, []);

  /** 같은 이벤트 루프의 선택·진행 콜백도 최신 항목에 순서대로 반영한다. */
  function update(next: UploadItem[]) {
    itemsRef.current = next;
    setItems(next);
  }

  /** 파일별 상태를 교체하며 제거된 항목을 늦은 응답으로 되살리지 않는다. */
  function patch(id: string, change: Partial<UploadItem>) {
    update(itemsRef.current.map((item) => (item.id === id ? { ...item, ...change } : item)));
  }

  /** accept는 안내일 뿐이며 파일명·MIME·크기 오류는 각각 표시한다. 서버가 실제 내용을 검증한다. */
  function handleSelect(files: File[]) {
    if (disabled || batch.isRunning()) return;

    const remaining = Math.max(0, maxFiles - itemsRef.current.length);
    setSelectionError(
      files.length > remaining
        ? `최대 ${maxFiles}개까지 선택할 수 있습니다. 초과한 ${files.length - remaining}개는 추가하지 않았습니다.`
        : '',
    );
    const additions = files.slice(0, remaining).map((file): UploadItem => {
      const extension = file.name.split('.').at(-1)?.toLowerCase();
      const format = formats.find(
        (entry) => entry.mime === file.type && entry.extensions.some((ext) => ext === extension),
      );
      const error = !format
        ? '허용한 확장자와 파일 형식이 일치해야 합니다.'
        : file.size === 0
          ? '빈 파일은 업로드할 수 없습니다.'
          : file.size > maxBytes
            ? `파일 크기는 ${maxBytes / 1024 / 1024}MiB 이하여야 합니다.`
            : undefined;

      return { id: crypto.randomUUID(), file, status: error ? 'invalid' : 'selected', error };
    });
    update([...itemsRef.current, ...additions]);
  }

  /** 서버 저장 완료 Promise를 두 작업씩 기다린다. 취소는 실제 요청 종료 후에만 잠금을 해제한다. */
  async function handleUpload(ids: string[]) {
    if (!upload || disabled) return;

    await batch.run(async (batchSignal) => {
      const queue = itemsRef.current.filter(
        (item) => ids.includes(item.id) && ['selected', 'error', 'cancelled'].includes(item.status),
      );
      cancelled.current.clear();
      update(
        itemsRef.current.map((item) =>
          queue.includes(item)
            ? {
                ...item,
                status: 'queued',
                error: undefined,
                traceId: undefined,
                progress: undefined,
                cancelling: false,
              }
            : item,
        ),
      );

      /** 대기열에서 하나씩 꺼내며 실패해도 다른 파일의 결과를 보존한다. */
      async function worker() {
        while (queue.length) {
          const item = queue.shift();

          if (!item) return;

          if (batchSignal.aborted || cancelled.current.has(item.id)) {
            patch(item.id, { status: 'cancelled', cancelling: false });
            continue;
          }

          const controller = new AbortController();
          const signal = AbortSignal.any([batchSignal, controller.signal]);
          active.current.set(item.id, controller);
          patch(item.id, { status: 'uploading' });

          try {
            await upload!({
              file: item.file,
              signal,
              onProgress: (loaded, total) => {
                if (signal.aborted) return;

                const progress =
                  total && total > 0
                    ? Math.min(100, Math.max(0, Math.floor((loaded / total) * 100)))
                    : undefined;
                patch(item.id, { progress, status: progress === 100 ? 'processing' : 'uploading' });
              },
            });
            patch(item.id, {
              status: signal.aborted ? 'cancelled' : 'saved',
              progress: signal.aborted ? undefined : 100,
            });
          } catch (error) {
            const safe = normalizeApiError(error);
            patch(
              item.id,
              signal.aborted
                ? { status: 'cancelled' }
                : { status: 'error', error: safe.message, traceId: safe.traceId },
            );
          } finally {
            active.current.delete(item.id);
            patch(item.id, { cancelling: false });
          }
        }
      }

      await Promise.all(
        Array.from({ length: Math.max(1, Math.min(2, concurrency, queue.length)) }, () => worker()),
      );
    });
  }

  /** 대기 항목은 건너뛰고 실행 항목은 취소를 요청한다. 서버 저장 취소를 보장하지 않는다. */
  function handleCancel(item: UploadItem) {
    cancelled.current.add(item.id);
    active.current.get(item.id)?.abort();
    patch(item.id, item.status === 'queued' ? { status: 'cancelled' } : { cancelling: true });
  }

  return (
    <section className={styles.fileUpload} aria-label={label}>
      <div
        className={styles.fileDrop}
        data-dragging={dragging || undefined}
        data-disabled={blocked || undefined}
        data-error={
          Boolean(selectionError) || items.some((item) => item.status === 'invalid') || undefined
        }
        onDragOver={(event) => {
          event.preventDefault();
          if (!blocked) setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          handleSelect(Array.from(event.dataTransfer.files));
        }}
      >
        <span className={styles.fileDropIcon}>
          <Icon name="upload" width="28" height="28" />
        </span>
        <div className={styles.fileDropCopy}>
          <h3>이미지를 끌어 놓으세요</h3>
          <p>또는 파일을 선택해 업로드할 이미지를 확인하세요.</p>
        </div>
        <Button
          disabled={blocked}
          leadingIcon={<Icon name="plus" />}
          aria-controls={inputId}
          aria-describedby={`${inputId}-help`}
          onClick={() => input.current?.click()}
        >
          파일 선택
        </Button>
        <p id={`${inputId}-help`} className={styles.help}>
          {formats
            .flatMap((format) => format.extensions)
            .join(' / ')
            .toUpperCase()}{' '}
          · 파일당 {maxBytes / 1024 / 1024}MiB · 최대 {maxFiles}개
        </p>
        <input
          ref={input}
          id={inputId}
          type="file"
          className={styles.srOnly}
          tabIndex={-1}
          aria-label={label}
          accept={accept}
          multiple
          disabled={blocked}
          aria-describedby={`${inputId}-help`}
          onChange={(event) => {
            handleSelect(Array.from(event.target.files ?? []));
            event.target.value = '';
          }}
        />
      </div>
      {!upload && (
        <div className={styles.fileNotice} role="status">
          <Icon name="info" />
          <p>
            <strong>{unavailableMessage}</strong>
            <span>지금은 파일을 선택하고 확인할 수 있어요.</span>
          </p>
        </div>
      )}
      {selectionError && (
        <p role="alert" className={styles.error}>
          {selectionError}
        </p>
      )}
      {items.length > 0 && (
        <div className={styles.fileListHeading}>
          <h3>
            선택한 파일 <span>{items.length}</span>
          </h3>
          <span className={styles.help}>최대 {maxFiles}개</span>
        </div>
      )}
      <ul className={styles.fileList} aria-label="선택한 파일">
        {items.map((item) => {
          const running = ['queued', 'uploading', 'processing'].includes(item.status);

          return (
            <li key={item.id} className={styles.fileItem} data-state={item.status}>
              {item.status !== 'invalid' &&
                ['image/png', 'image/jpeg'].includes(item.file.type) && (
                  <FilePreview file={item.file} />
                )}
              {(item.status === 'invalid' ||
                !['image/png', 'image/jpeg'].includes(item.file.type)) && (
                <span className={styles.filePlaceholder}>
                  <Icon name="image" />
                </span>
              )}
              <div className={styles.fileDetails}>
                <strong className={styles.fileName} title={item.file.name}>
                  {item.file.name}
                </strong>
                <div className={styles.fileMeta}>
                  <span>{(item.file.size / 1024).toFixed(1)} KiB</span>
                  <span aria-hidden="true">·</span>
                  <span role="status" className={styles.fileStatus}>
                    {item.cancelling ? '취소 요청 중 · 요청 종료 대기' : statusLabels[item.status]}
                  </span>
                </div>
                {['uploading', 'processing'].includes(item.status) && (
                  <div className={styles.fileProgress}>
                    <progress
                      max={100}
                      value={item.progress}
                      aria-label={`${item.file.name} 전송률`}
                    />
                    <span>
                      {item.progress === undefined ? '전송 크기 확인 중' : `${item.progress}% 전송`}
                    </span>
                  </div>
                )}
                {item.error && (
                  <p role="alert" className={styles.error}>
                    {item.error}
                  </p>
                )}
                {item.traceId && <small>진단 ID: {item.traceId}</small>}
              </div>
              <div className={styles.fileActions}>
                {running ? (
                  <Button
                    size="small"
                    aria-label={`${item.file.name} 전송 취소`}
                    disabled={item.cancelling}
                    onClick={() => handleCancel(item)}
                  >
                    취소
                  </Button>
                ) : (
                  <Button
                    size="small"
                    variant="text"
                    disabled={blocked}
                    aria-label={`${item.file.name} 선택 제거`}
                    onClick={() => update(itemsRef.current.filter((entry) => entry.id !== item.id))}
                  >
                    선택 제거
                  </Button>
                )}
                {['error', 'cancelled'].includes(item.status) && (
                  <Button
                    size="small"
                    aria-label={`${item.file.name} 다시 업로드`}
                    disabled={blocked || !upload}
                    onClick={() => void handleUpload([item.id])}
                  >
                    다시 업로드
                  </Button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
      <div className={styles.fileToolbar}>
        <div className={styles.fileSummary}>
          <strong>{items.length ? `${items.length}개 파일 선택` : '선택한 파일이 없어요'}</strong>
          <span>
            {items.length
              ? `${items.filter((item) => item.status === 'saved').length}개 저장 완료 · ${items.filter((item) => ['invalid', 'error'].includes(item.status)).length}개 오류`
              : '이미지를 추가하면 여기에 표시됩니다.'}
          </span>
        </div>
        <div className={styles.row}>
          <Button
            variant="primary"
            loading={batch.pending}
            disabled={disabled || !upload || !items.some((item) => item.status === 'selected')}
            onClick={() =>
              void handleUpload(
                itemsRef.current
                  .filter((item) => item.status === 'selected')
                  .map((item) => item.id),
              )
            }
          >
            업로드
          </Button>
          {batch.pending && <Button onClick={batch.cancel}>전체 취소</Button>}
        </div>
      </div>
      <details className={styles.fileNotes}>
        <summary>업로드·취소 안내</summary>
        <ul>
          <li>
            최대 {Math.max(1, Math.min(2, concurrency))}개씩 전송해요. 100% 전송 뒤에는 서버 저장
            완료를 기다려 주세요.
          </li>
          <li>취소해도 서버에 저장될 수 있어요. 다시 업로드하기 전에 내 파일 목록을 확인하세요.</li>
          <li>선택 제거는 이 목록에서만 제거하며 서버 파일을 삭제하지 않아요.</li>
        </ul>
      </details>
    </section>
  );
}
