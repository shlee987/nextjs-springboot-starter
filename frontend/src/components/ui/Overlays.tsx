'use client';

import { Dialog as PrimitiveDialog, AlertDialog } from 'radix-ui';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { ApiError } from '@/api/errors';
import { useAction } from '@/providers/ActionProvider';

import { Button, IconButton } from './Button';
import { Icon } from './Icon';
import { ErrorState } from './Feedback';
import styles from './ui.module.css';

export type DialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'medium' | 'large';
  busy?: boolean;
  dismissible?: boolean;
  side?: boolean;
};

let scrollLocks = 0;
let previousOverflow = '';

/** 중첩 팝업이 모두 닫힌 뒤에만 원래 body 스크롤 상태를 복원한다. */
function useBodyScrollLock(open: boolean) {
  useEffect(() => {
    if (!open) {
      return;
    }

    if (scrollLocks++ === 0) {
      previousOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
    }

    return () => {
      if (--scrollLocks === 0) {
        document.body.style.overflow = previousOverflow;
      }
    };
  }, [open]);
}

/** Radix 포커스 관리를 유지하고 nonce 없는 style 태그를 만드는 Overlay 대신 CSS 배경을 쓴다. */
export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  size = 'medium',
  busy,
  dismissible = true,
  side,
}: DialogProps) {
  const opener = useRef<HTMLElement | null>(null);
  useBodyScrollLock(open);

  return (
    <PrimitiveDialog.Root
      open={open}
      onOpenChange={(next) => {
        if (!busy && (next || dismissible)) onOpenChange(next);
      }}
    >
      <PrimitiveDialog.Portal>
        <div className={styles.overlay} aria-hidden="true" />
        <PrimitiveDialog.Content
          className={side ? styles.sidePanel : styles.dialog}
          data-size={size}
          onEscapeKeyDown={(event) => {
            if (busy || !dismissible) event.preventDefault();
          }}
          onPointerDownOutside={(event) => {
            if (busy || !dismissible) event.preventDefault();
          }}
          onOpenAutoFocus={() => {
            opener.current =
              document.activeElement instanceof HTMLElement ? document.activeElement : null;
          }}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            opener.current?.focus();
          }}
        >
          <div className={styles.dialogHeader}>
            <div className={styles.dialogHeading}>
              <PrimitiveDialog.Title>{title}</PrimitiveDialog.Title>
              {description ? (
                <PrimitiveDialog.Description className={styles.help}>
                  {description}
                </PrimitiveDialog.Description>
              ) : (
                <PrimitiveDialog.Description className={styles.srOnly}>
                  {title}
                </PrimitiveDialog.Description>
              )}
            </div>
            {dismissible && (
              <IconButton
                label="닫기"
                variant="text"
                disabled={busy}
                onClick={() => onOpenChange(false)}
                icon={<Icon name="close" />}
              />
            )}
          </div>
          {children}
          {footer && <div className={styles.dialogActions}>{footer}</div>}
        </PrimitiveDialog.Content>
      </PrimitiveDialog.Portal>
    </PrimitiveDialog.Root>
  );
}

/** SidePanel도 같은 닫기·진행 정책과 포커스 복귀를 공유한다. */
export function SidePanel(props: DialogProps) {
  return <Dialog {...props} side />;
}

/** 취소를 먼저 제시하고 의도에 맞는 확인 동작을 실행한다. 비동기 작업은 완료 Promise를 반환해야 한다. */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  onConfirm,
  busy,
  confirmLabel = '확인',
  intent = 'primary',
  autoClose = false,
  actionKey,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  onConfirm: (signal: AbortSignal) => unknown | Promise<unknown>;
  busy?: boolean;
  confirmLabel?: string;
  intent?: 'primary' | 'danger';
  autoClose?: boolean;
  actionKey?: string;
  children?: ReactNode;
}) {
  const opener = useRef<HTMLElement | null>(null);
  useBodyScrollLock(open);
  const generatedKey = useId();
  const action = useAction(actionKey ?? `confirm:${generatedKey}`);
  const [failure, setFailure] = useState<{ message: string; traceId?: string }>();
  const pending = busy || action.pending;

  /** 같은 키의 확인을 즉시 막고 성공한 작업만 자동 닫는다. 실패한 내용은 창 안에 유지한다. */
  async function handleConfirm() {
    if (busy || action.isRunning()) return;

    setFailure(undefined);

    try {
      const result = await action.run(onConfirm);

      if (result.status === 'completed' && autoClose) onOpenChange(false);
    } catch (error) {
      setFailure(
        error instanceof ApiError
          ? { message: error.message, traceId: error.traceId }
          : { message: '작업을 완료하지 못했습니다. 내용을 확인하고 다시 시도하세요.' },
      );
    }
  }

  return (
    <AlertDialog.Root
      open={open}
      onOpenChange={(next) => {
        if (!busy && !action.isRunning()) {
          setFailure(undefined);
          onOpenChange(next);
        }
      }}
    >
      <AlertDialog.Portal>
        <div className={styles.overlay} aria-hidden="true" />
        <AlertDialog.Content
          className={styles.dialog}
          onOpenAutoFocus={() => {
            opener.current =
              document.activeElement instanceof HTMLElement ? document.activeElement : null;
          }}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            opener.current?.focus();
          }}
          onEscapeKeyDown={(event) => {
            if (busy || action.isRunning()) event.preventDefault();
          }}
        >
          <AlertDialog.Title>{title}</AlertDialog.Title>
          <AlertDialog.Description>{description}</AlertDialog.Description>
          {children}
          {failure && <ErrorState message={failure.message} traceId={failure.traceId} />}
          <div className={styles.row}>
            <AlertDialog.Cancel asChild>
              <Button disabled={pending}>취소</Button>
            </AlertDialog.Cancel>
            <Button variant={intent} loading={pending} onClick={() => void handleConfirm()}>
              {confirmLabel}
            </Button>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
