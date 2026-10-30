'use client';

import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ComponentPropsWithRef,
  type ReactNode,
} from 'react';

import { formatDecimal, parseDecimal } from '@/lib/decimal';

import { Button } from './Button';

import styles from './ui.module.css';

type TextProps = ComponentPropsWithRef<'input'> & {
  error?: boolean;
  onClear?: () => void;
  prefixAddon?: ReactNode;
  suffixAddon?: ReactNode;
  showCount?: boolean;
};

/** HTML 속성과 ref를 보존하고 값을 trim하거나 변형하지 않는 기본 입력이다. */
export function TextInput({
  error,
  onClear,
  prefixAddon,
  suffixAddon,
  showCount,
  className = '',
  ...props
}: TextProps) {
  const [count, setCount] = useState(String(props.value ?? props.defaultValue ?? '').length);
  const input = (
    <input
      {...props}
      className={`${styles.input} ${className}`}
      aria-invalid={error || props['aria-invalid']}
      onChange={(event) => {
        if (showCount && props.value === undefined) setCount(event.target.value.length);
        props.onChange?.(event);
      }}
    />
  );

  const control =
    onClear || prefixAddon || suffixAddon ? (
      <div className={styles.inputGroup}>
        {prefixAddon && <span className={styles.inputAddon}>{prefixAddon}</span>}
        {input}
        {suffixAddon && <span className={styles.inputAddon}>{suffixAddon}</span>}
        {onClear && (
          <Button
            onClick={onClear}
            disabled={props.disabled || props.readOnly}
            aria-label="입력 지우기"
          >
            지우기
          </Button>
        )}
      </div>
    ) : (
      input
    );

  return showCount ? (
    <div className={styles.field}>
      {control}
      <span className={styles.characterCount} aria-live="polite">
        {props.value !== undefined ? String(props.value).length : count}
        {props.maxLength !== undefined && ` / ${props.maxLength}`}자
      </span>
    </div>
  ) : (
    control
  );
}

/** 붙여넣기와 비밀번호 관리자를 유지하며 표시 여부만 전환한다. */
export function PasswordInput(props: TextProps) {
  const [visible, setVisible] = useState(false);

  return (
    <div className={styles.inputGroup}>
      <TextInput {...props} type={visible ? 'text' : 'password'} />
      <Button
        aria-label={visible ? '비밀번호 숨기기' : '비밀번호 표시'}
        aria-pressed={visible}
        onClick={() => setVisible(!visible)}
        disabled={props.disabled}
      >
        {visible ? '숨기기' : '표시'}
      </Button>
    </div>
  );
}

/** 여러 줄 입력도 네이티브 속성과 오류 의미를 그대로 전달한다. */
export function Textarea({
  className = '',
  autoResize = false,
  showCount = false,
  ref,
  ...props
}: ComponentPropsWithRef<'textarea'> & { autoResize?: boolean; showCount?: boolean }) {
  const element = useRef<HTMLTextAreaElement | null>(null);
  const [count, setCount] = useState(String(props.value ?? props.defaultValue ?? '').length);
  const resize = useCallback(() => {
    const input = element.current;

    if (autoResize && input) {
      input.style.height = 'auto';
      input.style.height = `${Math.min(input.scrollHeight + 2, 320)}px`;
    }
  }, [autoResize]);
  useLayoutEffect(resize, [resize, props.value]);
  useEffect(() => {
    const input = element.current;

    if (!autoResize || !input) return;

    let width = input.clientWidth;
    let active = true;
    const observer = new ResizeObserver(() => {
      if (input.clientWidth !== width) {
        width = input.clientWidth;
        resize();
      }
    });
    observer.observe(input);
    void document.fonts.ready.then(() => {
      if (active) resize();
    });

    return () => {
      active = false;
      observer.disconnect();
    };
  }, [autoResize, resize]);

  const control = (
    <textarea
      {...props}
      ref={(input) => {
        element.current = input;
        if (typeof ref === 'function') return ref(input);
        if (ref) ref.current = input;
      }}
      className={`${styles.input} ${className}`}
      onChange={(event) => {
        if (showCount && props.value === undefined) setCount(event.target.value.length);
        resize();
        props.onChange?.(event);
      }}
    />
  );

  return showCount ? (
    <div className={styles.field}>
      {control}
      <span className={styles.characterCount} aria-live="polite">
        {props.value !== undefined ? String(props.value).length : count}
        {props.maxLength !== undefined && ` / ${props.maxLength}`}자
      </span>
    </div>
  ) : (
    control
  );
}

/** 검색 제출은 부모 form이 소유하며 표시용 텍스트를 임의 변환하지 않는다. */
export function SearchInput(props: TextProps) {
  return <TextInput {...props} type="search" />;
}

export type NumberInputProps = Omit<TextProps, 'value' | 'onChange' | 'type' | 'min' | 'max'> & {
  value: string;
  onChange: (value: string) => void;
  allowNegative?: boolean;
  fractionDigits?: number;
  min?: string;
  max?: string;
  grouped?: boolean;
  onValidation?: (error: string | undefined) => void;
};

/** 편집 중 '-'·'0.'를 보존하고 blur에서 문자열 정밀도와 범위를 검증한다. */
export function NumberInput({
  value,
  onChange,
  allowNegative = true,
  fractionDigits = 6,
  min,
  max,
  grouped,
  onValidation,
  ...props
}: NumberInputProps) {
  const [focused, setFocused] = useState(false);
  const [error, setError] = useState<string>();
  let display = value;

  if (grouped && !focused && value && !error) {
    try {
      display = formatDecimal(value);
    } catch {
      display = value;
    }
  }

  /** 편집을 마친 값만 검사하고 원본 문자열을 임의로 반올림하지 않는다. */
  function validate() {
    setFocused(false);
    let message: string | undefined;

    try {
      if (value) {
        const parsed = parseDecimal(value);

        if (
          (!allowNegative && parsed.isNegative()) ||
          (value.split('.')[1]?.length ?? 0) > fractionDigits ||
          (min !== undefined && parsed.lt(min)) ||
          (max !== undefined && parsed.gt(max))
        ) {
          throw new Error('허용 범위');
        }
      }
    } catch {
      message = `숫자 범위와 소수 ${fractionDigits}자리 제한을 확인하세요.`;
    }

    setError(message);
    onValidation?.(message);
  }

  return (
    <div>
      <TextInput
        {...props}
        value={display}
        inputMode="decimal"
        error={Boolean(error) || props.error}
        onFocus={(event) => {
          setFocused(true);
          props.onFocus?.(event);
        }}
        onBlur={(event) => {
          validate();
          props.onBlur?.(event);
        }}
        onChange={(event) => {
          const next = event.target.value;
          if (/^-?\d*(?:\.\d*)?$/.test(next)) {
            onChange(next);
            setError(undefined);
          }
        }}
      />
      {error && (
        <span className={styles.error} role="alert">
          {error}
        </span>
      )}
    </div>
  );
}

/** 금액도 문자열을 유지하고 표시만 천 단위로 구분한다. */
export function AmountInput(props: NumberInputProps) {
  return <NumberInput {...props} grouped fractionDigits={props.fractionDigits ?? 2} />;
}

export {
  DatePicker,
  DateTimePicker,
  DateInput,
  DateTimeInput,
  DateRangeInput,
} from './DatePickers';
export type { DateInputProps } from './DatePickers';
