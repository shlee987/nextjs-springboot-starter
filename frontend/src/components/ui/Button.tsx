'use client';

import type { ComponentPropsWithRef, ReactNode } from 'react';

import styles from './ui.module.css';

export type ButtonProps = ComponentPropsWithRef<'button'> & {
  variant?: 'default' | 'primary' | 'danger' | 'text';
  size?: 'small' | 'medium' | 'large';
  loading?: boolean;
  leadingIcon?: ReactNode;
  trailingIcon?: ReactNode;
};

/** 진행 중 중복 클릭을 막는다. 아이콘 없는 버튼은 문구 공간·접근 이름을 유지한 채 중앙에 진행 표시를 겹친다. */
export function Button({
  variant = 'default',
  size = 'medium',
  loading,
  disabled,
  children,
  leadingIcon,
  trailingIcon,
  className = '',
  type = 'button',
  ...props
}: ButtonProps) {
  const loadingOverlay = loading !== undefined && !leadingIcon && !trailingIcon;

  return (
    <button
      {...props}
      type={type}
      className={`${styles.button} ${className}`}
      data-variant={variant}
      data-size={size}
      data-loading-overlay={loadingOverlay || undefined}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
    >
      {loadingOverlay ? (
        <>
          <span className={styles.buttonLoadingLabel} data-loading={Boolean(loading)}>
            {children}
          </span>
          {loading && (
            <span className={styles.buttonLoadingOverlay} aria-hidden="true">
              <span className={styles.spinner} />
            </span>
          )}
        </>
      ) : (
        <>
          {leadingIcon && (
            <span className={styles.buttonIconSlot} aria-hidden="true">
              {loading ? <span className={styles.spinner} /> : leadingIcon}
            </span>
          )}
          {children}
          {trailingIcon && (
            <span className={styles.buttonIconSlot} aria-hidden="true">
              {loading && !leadingIcon ? <span className={styles.spinner} /> : trailingIcon}
            </span>
          )}
        </>
      )}
    </button>
  );
}

/** 아이콘만 표시해도 접근 가능한 이름을 필수로 받는다. */
export function IconButton({
  label,
  icon,
  className = '',
  ...props
}: Omit<ButtonProps, 'children'> & { label: string; icon: ReactNode }) {
  return (
    <Button {...props} className={`${styles.iconButton} ${className}`} aria-label={label}>
      {icon}
    </Button>
  );
}
