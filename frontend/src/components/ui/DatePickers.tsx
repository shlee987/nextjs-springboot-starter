'use client';

import { Popover } from 'radix-ui';
import {
  useEffect,
  useEffectEvent,
  useId,
  useRef,
  useState,
  type ComponentPropsWithRef,
  type KeyboardEvent,
} from 'react';

import {
  formatApiDate,
  fromDisplayDate,
  getMonthDays,
  parseApiDate,
  shiftCalendarDay,
  shiftCalendarMonth,
  toDisplayDate,
  validateDateRange,
  type DatePrecision,
  type DisplayTimeZone,
} from '@/lib/dates';

import { Button, IconButton } from './Button';
import { FormField } from './Form';
import { Icon } from './Icon';
import styles from './ui.module.css';

export type DateInputProps = Omit<
  ComponentPropsWithRef<'input'>,
  'value' | 'onChange' | 'min' | 'max'
> & {
  value: string;
  onChange: (value: string) => void;
  precision?: DatePrecision;
  timeZone?: DisplayTimeZone;
  min?: string;
  max?: string;
  error?: boolean;
  onClear?: () => void;
  onValidation?: (error: string | undefined) => void;
  isDateDisabled?: (date: string) => boolean;
};
const invalidDateMessage =
  '날짜 형식·존재 여부·범위를 확인하세요. 중복되거나 없는 현지 시각은 UTC로 입력하세요.';
const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

/** 직접 편집과 달력 선택에 같은 엄격 검증을 적용한다. 날짜시간은 팝업 확인 전까지 부모 값을 바꾸지 않는다. */
export function DatePicker({
  value,
  onChange,
  precision = 'date',
  timeZone = 'Asia/Seoul',
  min,
  max,
  isDateDisabled,
  onClear,
  onValidation,
  disabled,
  readOnly,
  error: fieldError,
  className = '',
  'aria-describedby': describedBy,
  ...props
}: DateInputProps) {
  const [editing, setEditing] = useState<string>();
  const [error, setError] = useState<string>();
  const [popupError, setPopupError] = useState<string>();
  const [open, setOpen] = useState(false);
  const [previousValue, setPreviousValue] = useState(value);
  const [draft, setDraft] = useState('');
  const [timeParts, setTimeParts] = useState(['00', '00', '00', '000']);
  const [focusedDay, setFocusedDay] = useState('');
  const [today, setToday] = useState('');
  const calendarRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const id = useId();
  const locked = disabled || readOnly;
  if (previousValue !== value) {
    setPreviousValue(value);
    setEditing(undefined);
    setError(undefined);
    setPopupError(undefined);
    setOpen(false);
  }
  const validationMessage =
    precision === 'date' ? '날짜 형식·존재 여부·범위를 확인하세요.' : invalidDateMessage;
  let display = value;

  try {
    if (value) display = toDisplayDate(value, precision, timeZone);
  } catch {
    // 잘못된 외부 값은 숨기지 않고 직접 수정할 수 있게 원문을 남긴다.
  }

  /** 외부 UTC 값도 min/max·선택 제한을 검사하며 DST 중복 표시를 역변환하지 않는다. */
  function validateCanonical(canonical: string): void {
    const instant = parseApiDate(canonical, precision).getTime();

    if (isDateDisabled?.(toDisplayDate(canonical, precision, timeZone).slice(0, 10))) {
      throw new Error('선택할 수 없는 날짜입니다.');
    }

    if (
      (min && instant < parseApiDate(min, precision).getTime()) ||
      (max && instant > parseApiDate(max, precision).getTime())
    ) {
      throw new Error('허용 범위를 벗어난 날짜입니다.');
    }
  }

  let externalError: string | undefined;

  try {
    if (value) validateCanonical(value);
  } catch {
    externalError = validationMessage;
  }

  const visibleError = error || externalError;
  const notifyExternalValidation = useEffectEvent((next: string | undefined) =>
    onValidation?.(next),
  );

  useEffect(() => {
    // 부모 값 교체가 이전 편집 오류를 해제하되 잘못된 외부 값은 그대로 보고한다.
    notifyExternalValidation(externalError);
  }, [value, externalError]);

  /** 표시값을 UTC 계약으로 변환하고 min/max를 검사한다. 실패하면 부모 값은 유지한다. */
  function parseSelection(text: string): string {
    const canonical = fromDisplayDate(text, precision, timeZone);
    validateCanonical(canonical);

    return canonical;
  }

  /** 입력 완료값 또는 달력의 확정값만 전달하며 실패한 편집값을 지우지 않는다. */
  function handleCommit(text: string, fromPopup = false): boolean {
    if (locked) return false;

    try {
      const canonical = text ? parseSelection(text) : '';
      onChange(canonical);
      setEditing(undefined);
      setError(undefined);
      setPopupError(undefined);
      onValidation?.(undefined);

      return true;
    } catch {
      if (fromPopup) {
        setPopupError(validationMessage);
      } else {
        setError(validationMessage);
        onValidation?.(validationMessage);
      }

      return false;
    }
  }

  /** 열 때마다 확정값을 복사한다. 취소·외부 클릭·Escape는 임시값을 폐기한다. */
  function handleOpen(next: boolean) {
    if (next && locked) return;

    if (next) {
      const now = toDisplayDate(formatApiDate(new Date(), precision), precision, timeZone);
      let initial = display;

      try {
        parseApiDate(initial, precision);
      } catch {
        initial = now;
      }

      setToday(now.slice(0, 10));
      setDraft(initial);
      setTimeParts([
        initial.slice(11, 13),
        initial.slice(14, 16),
        initial.slice(17, 19),
        initial.slice(20, 23),
      ]);
      setFocusedDay(initial.slice(0, 10));
      setPopupError(undefined);
    }

    setOpen(next);
  }

  const month = focusedDay.slice(0, 7);
  const yearNumber = Number(month.slice(0, 4));
  const monthNumber = Number(month.slice(5, 7));
  const monthDays = month ? getMonthDays(yearNumber, monthNumber) : 0;
  const firstWeekday = month ? parseApiDate(`${month}-01`, 'date').getUTCDay() : 0;
  const selectedDay = draft.slice(0, 10);

  /** 날짜만 범위를 비교한다. 날짜시간의 경계일은 시간 편집을 허용하고 확정 시 정밀 비교한다. */
  function isDayDisabled(day: string): boolean {
    try {
      const lower = min ? getBoundaryDay(min) : undefined;
      const upper = max ? getBoundaryDay(max) : undefined;

      return Boolean(
        isDateDisabled?.(day) ||
        (min && !lower) ||
        (lower && day < lower) ||
        (upper && day > upper),
      );
    } catch {
      return true;
    }
  }

  /** 서울 표시가 10000년으로 넘어간 유효 UTC 경계는 달력 밖이다. 잘못된 명세 값은 대체하지 않는다. */
  function getBoundaryDay(bound: string): string | undefined {
    const instant = parseApiDate(bound, precision);

    try {
      return toDisplayDate(bound, precision, timeZone).slice(0, 10);
    } catch (cause) {
      if (timeZone === 'Asia/Seoul' && instant.getUTCFullYear() === 9999) {
        return undefined;
      }

      throw cause;
    }
  }

  /** 렌더가 바뀐 뒤 roving tab stop의 실제 날짜 버튼으로 포커스를 이동한다. */
  function handleFocusDay(day: string) {
    setFocusedDay(day);
    requestAnimationFrame(() =>
      calendarRef.current?.querySelector<HTMLButtonElement>(`[data-date="${day}"]`)?.focus(),
    );
  }

  /** 날짜 선택은 기존 시·분·초·밀리초를 보존한다. 날짜만 선택은 즉시 확정한다. */
  function handleSelectDay(day: string) {
    if (locked || isDayDisabled(day)) return;

    const next = `${day}${draft.slice(10)}`;
    setDraft(next);
    setPopupError(undefined);

    if (precision === 'date' && handleCommit(next, true)) setOpen(false);
  }

  /** 방향키·주 경계·월 이동은 UTC 달력 계산으로 처리하고 연도 경계를 넘지 않는다. */
  function handleCalendarKey(event: KeyboardEvent<HTMLButtonElement>, day: string) {
    const weekday = parseApiDate(day, 'date').getUTCDay();
    const offsets: Record<string, number> = {
      ArrowLeft: -1,
      ArrowRight: 1,
      ArrowUp: -7,
      ArrowDown: 7,
      Home: -weekday,
      End: 6 - weekday,
    };
    let next: string;

    if (event.key in offsets) {
      next = shiftCalendarDay(day, offsets[event.key]);
    } else if (event.key === 'PageUp' || event.key === 'PageDown') {
      next = shiftCalendarMonth(day, event.key === 'PageUp' ? -1 : 1);
    } else {
      return;
    }

    event.preventDefault();
    handleFocusDay(next);
  }

  return (
    <div className={styles.field}>
      <Popover.Root open={open} onOpenChange={handleOpen}>
        <div className={styles.inputGroup}>
          <input
            {...props}
            disabled={disabled}
            readOnly={readOnly}
            className={`${styles.input} ${className}`}
            value={editing ?? display}
            aria-invalid={Boolean(visibleError) || fieldError || props['aria-invalid']}
            aria-describedby={
              [describedBy, visibleError && `${id}-error`, precision !== 'date' && `${id}-zone`]
                .filter(Boolean)
                .join(' ') || undefined
            }
            placeholder={
              props.placeholder ??
              (precision === 'date'
                ? 'yyyy-MM-dd'
                : `yyyy-MM-dd HH:mm:ss${precision === 'millisecond' ? '.SSS' : ''}`)
            }
            onChange={(event) => {
              setEditing(event.target.value);
              onValidation?.('날짜 편집을 마친 뒤 값을 확정하세요.');
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && editing !== undefined) {
                event.preventDefault();
                handleCommit(editing);
              }

              props.onKeyDown?.(event);
            }}
            onBlur={(event) => {
              if (editing !== undefined) handleCommit(editing);
              props.onBlur?.(event);
            }}
          />
          <Popover.Trigger asChild>
            <IconButton
              ref={triggerRef}
              label={`${props['aria-label'] ?? '날짜'} 달력 열기`}
              icon={<Icon name="calendar" />}
              disabled={locked}
            />
          </Popover.Trigger>
          {onClear && (
            <Button
              disabled={locked}
              onClick={() => {
                if (handleCommit('')) onClear();
              }}
            >
              지우기
            </Button>
          )}
        </div>
        <Popover.Portal>
          <Popover.Content
            className={`${styles.floating} ${styles.datePopup}`}
            sideOffset={8}
            collisionPadding={16}
            aria-label={precision === 'date' ? '날짜 선택' : '날짜와 시간 선택'}
            onOpenAutoFocus={(event) => {
              event.preventDefault();
              requestAnimationFrame(() =>
                calendarRef.current
                  ?.querySelector<HTMLButtonElement>(`[data-date="${focusedDay}"]`)
                  ?.focus(),
              );
            }}
            onCloseAutoFocus={(event) => {
              event.preventDefault();
              triggerRef.current?.focus();
            }}
          >
            <div className={styles.datePopupBody}>
              <div className={styles.calendarHeading}>
                <IconButton
                  label="이전 월"
                  icon={<Icon name="left" />}
                  disabled={month === '0001-01'}
                  onClick={() => setFocusedDay(shiftCalendarMonth(focusedDay, -1))}
                />
                <div className={styles.calendarMonth}>
                  <label className={styles.srOnly} htmlFor={`${id}-year`}>
                    달력 연도
                  </label>
                  <input
                    id={`${id}-year`}
                    className={styles.input}
                    type="number"
                    min="1"
                    max="9999"
                    value={yearNumber || ''}
                    onChange={(event) => {
                      const year = Number(event.target.value);

                      if (Number.isInteger(year) && year >= 1 && year <= 9999)
                        setFocusedDay(
                          `${String(year).padStart(4, '0')}-${String(monthNumber).padStart(2, '0')}-01`,
                        );
                    }}
                  />
                  <label className={styles.srOnly} htmlFor={`${id}-month`}>
                    달력 월
                  </label>
                  <input
                    id={`${id}-month`}
                    className={styles.input}
                    type="number"
                    min="1"
                    max="12"
                    value={monthNumber || ''}
                    onChange={(event) => {
                      const nextMonth = Number(event.target.value);

                      if (Number.isInteger(nextMonth) && nextMonth >= 1 && nextMonth <= 12)
                        setFocusedDay(
                          `${String(yearNumber).padStart(4, '0')}-${String(nextMonth).padStart(2, '0')}-01`,
                        );
                    }}
                  />
                </div>
                <IconButton
                  label="다음 월"
                  icon={<Icon name="right" />}
                  disabled={month === '9999-12'}
                  onClick={() => setFocusedDay(shiftCalendarMonth(focusedDay, 1))}
                />
              </div>
              <p className={styles.help} aria-live="polite">
                {yearNumber}년 {monthNumber}월 ·{' '}
                {precision === 'date' ? '날짜를 선택하세요' : '선택 후 확인을 누르세요'}
              </p>
              <div
                role="grid"
                aria-label={`${yearNumber}년 ${monthNumber}월 달력`}
                className={styles.calendarGrid}
                ref={calendarRef}
              >
                <div role="row" className={styles.calendarWeek}>
                  {weekdays.map((day) => (
                    <span role="columnheader" key={day}>
                      {day}
                    </span>
                  ))}
                </div>
                {Array.from({ length: Math.ceil((firstWeekday + monthDays) / 7) }, (_, week) => (
                  <div role="row" className={styles.calendarWeek} key={week}>
                    {weekdays.map((weekday, column) => {
                      const number = week * 7 + column - firstWeekday + 1;

                      if (number < 1 || number > monthDays)
                        return <span role="gridcell" key={weekday} />;

                      const day = `${month}-${String(number).padStart(2, '0')}`;
                      const unavailable = locked || isDayDisabled(day);

                      return (
                        <div role="gridcell" aria-selected={day === selectedDay} key={weekday}>
                          <button
                            type="button"
                            className={styles.calendarDay}
                            data-date={day}
                            data-selected={day === selectedDay}
                            aria-label={`${day}${day === today ? ' 오늘' : ''}${day === selectedDay ? ' 선택됨' : ''}`}
                            aria-current={day === today ? 'date' : undefined}
                            aria-disabled={unavailable}
                            tabIndex={day === focusedDay ? 0 : -1}
                            onFocus={() => setFocusedDay(day)}
                            onKeyDown={(event) => handleCalendarKey(event, day)}
                            onClick={() => handleSelectDay(day)}
                          >
                            {number}
                          </button>
                        </div>
                      );
                    })}
                  </div>
                ))}
              </div>
              {precision !== 'date' && (
                <fieldset className={styles.field}>
                  <legend>시간 · {timeZone}</legend>
                  <div className={styles.calendarTime}>
                    {['시', '분', '초', ...(precision === 'millisecond' ? ['밀리초'] : [])].map(
                      (label, index) => {
                        const width = index === 3 ? 3 : 2;
                        const text = timeParts[index];

                        return (
                          <label className={styles.field} key={label}>
                            {label}
                            <input
                              className={styles.input}
                              aria-label={label}
                              inputMode="numeric"
                              value={text}
                              maxLength={width}
                              onChange={(event) => {
                                const next = event.target.value;

                                if (/^\d*$/.test(next) && next.length <= width) {
                                  setTimeParts(
                                    timeParts.map((part, partIndex) =>
                                      partIndex === index ? next : part,
                                    ),
                                  );
                                  setPopupError(undefined);
                                }
                              }}
                            />
                          </label>
                        );
                      },
                    )}
                  </div>
                </fieldset>
              )}
              {popupError && (
                <p role="alert" className={styles.error}>
                  {popupError}
                </p>
              )}
            </div>
            <div className={styles.popupActions}>
              <Button
                size="small"
                disabled={isDayDisabled(today)}
                onClick={() => {
                  handleFocusDay(today);
                  handleSelectDay(today);
                }}
              >
                오늘
              </Button>
              <Button
                size="small"
                onClick={() => {
                  if (precision !== 'date') {
                    setDraft('');
                    setPopupError(undefined);
                  } else if (handleCommit('', true)) {
                    onClear?.();
                    setOpen(false);
                  }
                }}
              >
                지우기
              </Button>
              <Popover.Close asChild>
                <Button size="small">{precision === 'date' ? '닫기' : '취소'}</Button>
              </Popover.Close>
              {precision !== 'date' && (
                <Button
                  size="small"
                  variant="primary"
                  onClick={() => {
                    const time = timeParts
                      .slice(0, 3)
                      .map((part) => part.padStart(2, '0'))
                      .join(':');
                    const next = draft
                      ? `${draft.slice(0, 10)} ${time}${precision === 'millisecond' ? `.${timeParts[3].padStart(3, '0')}` : ''}`
                      : '';

                    if (handleCommit(next, true)) setOpen(false);
                  }}
                >
                  확인
                </Button>
              )}
            </div>
          </Popover.Content>
        </Popover.Portal>
      </Popover.Root>
      {precision !== 'date' && (
        <span id={`${id}-zone`} className={styles.help}>
          표시: {timeZone} · API: UTC
        </span>
      )}
      {visibleError && (
        <span id={`${id}-error`} className={styles.error} role="alert">
          {visibleError}
        </span>
      )}
    </div>
  );
}

/** 초 또는 밀리초를 보존하며 UTC 계약 값과 표시 시간대를 왕복하는 날짜시간 선택기다. */
export function DateTimePicker(props: DateInputProps) {
  return (
    <DatePicker {...props} precision={props.precision === 'second' ? 'second' : 'millisecond'} />
  );
}

/** 기존 날짜 입력 호출부의 값·ref·검증 계약을 달력 선택기로 유지한다. */
export function DateInput(props: DateInputProps) {
  return <DatePicker {...props} />;
}

/** 기존 날짜시간 입력 호출부의 정밀도와 시간대 계약을 유지한다. */
export function DateTimeInput(props: DateInputProps) {
  return <DateTimePicker {...props} />;
}

/** 시작·종료와 최대 기간을 검사하며 두 입력이 같은 달력 선택기를 사용한다. */
export function DateRangeInput({
  from,
  to,
  onChange,
  precision = 'date',
  maxDays,
  presets,
  onValidation,
  ...props
}: Omit<DateInputProps, 'value' | 'onChange'> & {
  from: string;
  to: string;
  onChange: (from: string, to: string) => void;
  maxDays?: number;
  presets?: { label: string; from: string; to: string }[];
}) {
  const errors = useRef<{ from?: string; to?: string; range?: string }>({});
  const [presetRevision, setPresetRevision] = useState(0);
  const rangeError =
    from && to && !validateDateRange(from, to, precision, maxDays)
      ? '시작·종료 순서와 최대 기간을 확인하세요.'
      : undefined;
  const notifyRangeValidation = useEffectEvent(() => {
    errors.current.range = rangeError;
    onValidation?.(errors.current.from || errors.current.to || rangeError);
  });

  useEffect(() => {
    notifyRangeValidation();
  }, [from, to, rangeError]);

  /** 빠른 기간도 직접 입력과 같은 정밀도·범위·선택 제한을 통과해야 적용한다. */
  function isPresetAllowed(preset: { from: string; to: string }) {
    if (!validateDateRange(preset.from, preset.to, precision, maxDays)) return false;

    try {
      return [preset.from, preset.to].every((value) => {
        const instant = parseApiDate(value, precision).getTime();

        return (
          !(props.min && instant < parseApiDate(props.min, precision).getTime()) &&
          !(props.max && instant > parseApiDate(props.max, precision).getTime()) &&
          !props.isDateDisabled?.(toDisplayDate(value, precision, props.timeZone).slice(0, 10))
        );
      });
    } catch {
      return false;
    }
  }

  return (
    <div className={styles.dateRange}>
      {presets && (
        <div className={`${styles.row} ${styles.rangePresets}`}>
          {presets.map((preset) => (
            <Button
              key={preset.label}
              size="small"
              disabled={props.disabled || props.readOnly || !isPresetAllowed(preset)}
              onClick={() => {
                errors.current = {};
                setPresetRevision((revision) => revision + 1);
                onValidation?.(undefined);
                onChange(preset.from, preset.to);
              }}
            >
              {preset.label}
            </Button>
          ))}
        </div>
      )}
      <FormField label="시작">
        <DatePicker
          key={`from:${presetRevision}`}
          {...props}
          aria-label="시작"
          value={from}
          precision={precision}
          onChange={(value) => {
            errors.current.range =
              value && to && !validateDateRange(value, to, precision, maxDays)
                ? '시작·종료 순서와 최대 기간을 확인하세요.'
                : undefined;
            onChange(value, to);
          }}
          onValidation={(error) => {
            errors.current.from = error;
            onValidation?.(error || errors.current.to || errors.current.range);
          }}
        />
      </FormField>
      <FormField label="종료">
        <DatePicker
          key={`to:${presetRevision}`}
          {...props}
          aria-label="종료"
          value={to}
          precision={precision}
          onChange={(value) => {
            errors.current.range =
              from && value && !validateDateRange(from, value, precision, maxDays)
                ? '시작·종료 순서와 최대 기간을 확인하세요.'
                : undefined;
            onChange(from, value);
          }}
          onValidation={(error) => {
            errors.current.to = error;
            onValidation?.(error || errors.current.from || errors.current.range);
          }}
        />
      </FormField>
      {rangeError && (
        <span className={styles.error} role="alert">
          {rangeError}
        </span>
      )}
    </div>
  );
}
