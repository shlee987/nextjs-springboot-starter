'use client';

import { useId, type ReactElement, type ReactNode, cloneElement } from 'react';

import { Button } from './Button';
import styles from './ui.module.css';

/** label·도움말·오류를 입력과 aria로 연결하며 RHF 없이도 사용한다. */
export function FormField({
  label,
  required,
  help,
  error,
  horizontal,
  children,
}: {
  label: string;
  required?: boolean;
  help?: string;
  error?: string;
  horizontal?: boolean;
  children: ReactElement<{
    id?: string;
    'aria-describedby'?: string;
    'aria-invalid'?: boolean;
    'aria-required'?: boolean;
  }>;
}) {
  const generatedId = useId();
  const id = children.props.id ?? generatedId;
  const describedBy =
    [help && `${id}-help`, error && `${id}-error`].filter(Boolean).join(' ') || undefined;

  return (
    <div className={styles.field} data-horizontal={horizontal}>
      <label className={styles.label} htmlFor={id}>
        {label}
        {required && ' (필수)'}
      </label>
      <div className={styles.fieldContent}>
        {cloneElement(children, {
          id,
          'aria-describedby': describedBy,
          'aria-invalid': Boolean(error),
          'aria-required': required,
        })}
        {help && (
          <div id={`${id}-help`} className={styles.help}>
            {help}
          </div>
        )}
        {error && (
          <div id={`${id}-error`} className={styles.error}>
            {error}
          </div>
        )}
      </div>
    </div>
  );
}

/** 폼 단위 실패만 한 곳에서 알리고 필드 오류와 중복시키지 않는다. */
export function FormError({ children }: { children?: ReactNode }) {
  return children ? (
    <p className={styles.error} role="alert">
      {children}
    </p>
  ) : null;
}

/** 여러 필드 오류를 요약하고 각 폼이 제공한 ref 기반 이동을 실행한다. */
export function FormErrorSummary({
  items,
}: {
  items: { key: string; label: string; message: string; onFocus: () => void }[];
}) {
  if (!items.length) return null;

  return (
    <section className={styles.errorSummary} role="alert" aria-label="입력 오류 요약">
      <strong>입력한 내용을 확인하세요.</strong>
      <ul>
        {items.map((item) => (
          <li key={item.key}>
            <Button variant="text" className={styles.errorSummaryLink} onClick={item.onFocus}>
              {item.label}: {item.message}
            </Button>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** 제출 중에는 중복 전송과 reset을 막는 공통 조작 영역이다. */
export function FormActions({
  submitting,
  onReset,
  submitLabel = '제출',
  children,
}: {
  submitting?: boolean;
  onReset?: () => void;
  submitLabel?: string;
  children?: ReactNode;
}) {
  return (
    <div className={styles.row}>
      <Button type="submit" variant="primary" loading={submitting}>
        {submitLabel}
      </Button>
      {onReset && (
        <Button disabled={submitting} onClick={onReset}>
          초기화
        </Button>
      )}
      {children}
    </div>
  );
}
