'use client';

import { createContext, useContext, useRef, useState, useEffect, type ReactNode } from 'react';

import { Button, IconButton } from './Button';
import { Icon } from './Icon';
import styles from './ui.module.css';

export type Tone = 'info' | 'success' | 'warning' | 'error';
type Notice = { id: number; text: string; tone: Tone };
const ToastContext = createContext<((text: string, tone?: Tone) => void) | undefined>(undefined);
export const toneLabels = { info: '안내', success: '성공', warning: '주의', error: '오류' };

/** 동일 문구의 중복을 막고 시간 경과 또는 명시 조작으로 닫는 알림을 제공한다. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [notices, setNotices] = useState<Notice[]>([]);
  const sequence = useRef(0);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  useEffect(() => {
    const active = timers.current;
    return () => {
      active.forEach(clearTimeout);
      active.clear();
    };
  }, []);

  /** API 원문 대신 승인된 표시 문구를 받아 알림을 최대 5개만 유지한다. */
  function notify(text: string, tone: Tone = 'info') {
    const id = ++sequence.current;
    setNotices((current) =>
      current.some((notice) => notice.text === text && notice.tone === tone)
        ? current
        : [...current.slice(-4), { id, text, tone }],
    );
    timers.current.set(
      id,
      setTimeout(() => {
        timers.current.delete(id);
        setNotices((current) => current.filter((notice) => notice.id !== id));
      }, 6000),
    );
  }

  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className={styles.toastArea} aria-label="알림">
        {notices.map((notice) => (
          <InlineAlert
            key={notice.id}
            tone={notice.tone}
            onDismiss={() =>
              setNotices((current) => current.filter((item) => item.id !== notice.id))
            }
          >
            {notice.text}
          </InlineAlert>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/** Provider의 알림 함수를 사용해 화면별 중복 통지를 피한다. */
export function useToast() {
  const notify = useContext(ToastContext);

  if (!notify) {
    throw new Error('ToastProvider 내부에서 사용하세요.');
  }

  return notify;
}

/** 색뿐 아니라 종류를 글자로 표시하고 긴급도에 맞는 live 영역을 제공한다. */
export function InlineAlert({
  tone = 'info',
  children,
  onDismiss,
  actions,
}: {
  tone?: Tone;
  children: ReactNode;
  onDismiss?: () => void;
  actions?: ReactNode;
}) {
  return (
    <div className={styles.alert} data-tone={tone} role={tone === 'error' ? 'alert' : 'status'}>
      <span className={styles.alertIcon}>
        <Icon name={tone === 'success' ? 'check' : 'info'} />
      </span>
      <div className={styles.alertBody}>
        <strong>{toneLabels[tone]}</strong>
        <div>{children}</div>
        {actions && <div className={styles.alertActions}>{actions}</div>}
      </div>
      {onDismiss && (
        <IconButton
          className={styles.alertClose}
          variant="text"
          label="알림 닫기"
          icon={<Icon name="close" />}
          onClick={onDismiss}
        />
      )}
    </div>
  );
}

/** 상태의 짧은 의미를 글자로 표시한다. */
export function Badge({ children }: { children: ReactNode }) {
  return <span className={styles.badge}>{children}</span>;
}
/** 영역 단위 로딩은 접근 가능한 이름과 회전 표시를 함께 제공한다. */
export function Spinner({
  label = '불러오는 중',
  inline = false,
}: {
  label?: string;
  inline?: boolean;
}) {
  return (
    <span className={`${styles.loadingRow} ${inline ? '' : styles.state}`} role="status">
      <span className={styles.spinner} aria-hidden="true" />
      <span>{label}</span>
    </span>
  );
}
/** 콘텐츠 위치를 유지하는 로딩 자리 표시자다. */
export function Skeleton() {
  return <div className={styles.skeleton} aria-label="불러오는 중" role="status" />;
}
/** 정상 조회의 빈 결과를 오류와 구분해 표시한다. */
export function EmptyState({
  message = '표시할 데이터가 없습니다.',
  actions,
}: {
  message?: string;
  actions?: ReactNode;
}) {
  return (
    <div className={styles.state} role="status">
      <div className={styles.stack}>
        <p>{message}</p>
        {actions && <div className={styles.row}>{actions}</div>}
      </div>
    </div>
  );
}
/** 안전한 오류 문구·진단 ID와 명시적인 재시도를 표시한다. */
export function ErrorState({
  message = '요청을 처리하지 못했습니다.',
  traceId,
  onRetry,
  retrying,
}: {
  message?: string;
  traceId?: string;
  onRetry?: () => void;
  retrying?: boolean;
}) {
  return (
    <InlineAlert
      tone="error"
      actions={
        onRetry && (
          <Button onClick={onRetry} loading={retrying}>
            다시 시도
          </Button>
        )
      }
    >
      {message}
      {traceId && ` · 진단 ID ${traceId}`}
    </InlineAlert>
  );
}
