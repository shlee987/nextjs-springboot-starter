'use client';

import {
  Checkbox as PrimitiveCheckbox,
  RadioGroup as PrimitiveRadio,
  Select as PrimitiveSelect,
  Switch as PrimitiveSwitch,
  Popover,
} from 'radix-ui';
import { useEffect, useId, useRef, useState, type ComponentPropsWithRef } from 'react';

import { useDocumentNonce } from '@/providers/AppProviders';

import { Button, IconButton } from './Button';
import { ErrorState, Spinner } from './Feedback';
import { ClientOnly } from './ClientOnly';
import { TextInput } from './Inputs';
import { Icon } from './Icon';
import styles from './ui.module.css';

export type Option = { value: string; label: string; disabled?: boolean };

/** boolean 호출을 유지하며 일부 선택 상태는 Radix의 mixed 의미와 가로선으로 표시한다. */
export function Checkbox({
  label,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  checked: boolean | 'indeterminate';
  onChange: (checked: boolean) => void;
  disabled?: boolean;
}) {
  const id = useId();

  return (
    <ClientOnly
      fallback={
        <span className={styles.choiceText} aria-busy="true">
          {label} 준비 중
        </span>
      }
    >
      <label className={styles.checkLabel} htmlFor={id}>
        <PrimitiveCheckbox.Root
          id={id}
          className={styles.check}
          checked={checked}
          onCheckedChange={(value) => onChange(value === true)}
          disabled={disabled}
        >
          <PrimitiveCheckbox.Indicator>
            <Icon name={checked === 'indeterminate' ? 'minus' : 'check'} />
          </PrimitiveCheckbox.Indicator>
        </PrimitiveCheckbox.Root>
        <span className={styles.choiceText}>{label}</span>
      </label>
    </ClientOnly>
  );
}

/** 복수 선택은 값 배열로 관리하며 비활성 항목은 조작하지 않는다. */
export function CheckboxGroup({
  label,
  options,
  value,
  onChange,
  disabled,
}: {
  label: string;
  options: Option[];
  value: string[];
  onChange: (value: string[]) => void;
  disabled?: boolean;
}) {
  return (
    <fieldset className={styles.field}>
      <legend>{label}</legend>
      <div className={styles.choiceGroup}>
        {options.map((option) => (
          <Checkbox
            key={option.value}
            label={option.label}
            checked={value.includes(option.value)}
            disabled={disabled || option.disabled}
            onChange={(checked) =>
              onChange(
                checked ? [...value, option.value] : value.filter((item) => item !== option.value),
              )
            }
          />
        ))}
      </div>
    </fieldset>
  );
}

/** Radix의 단일 선택과 방향키 이동을 재사용한다. */
export function RadioGroup({
  label,
  options,
  value,
  onChange,
  disabled,
}: {
  label: string;
  options: Option[];
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}) {
  const id = useId();

  return (
    <ClientOnly
      fallback={
        <span className={styles.choiceText} aria-busy="true">
          {label} 준비 중
        </span>
      }
    >
      <PrimitiveRadio.Root
        aria-label={label}
        value={value}
        onValueChange={onChange}
        disabled={disabled}
        className={styles.row}
      >
        {options.map((option) => (
          <label key={option.value} htmlFor={`${id}-${option.value}`} className={styles.checkLabel}>
            <PrimitiveRadio.Item
              className={styles.radio}
              id={`${id}-${option.value}`}
              value={option.value}
              disabled={option.disabled}
            >
              <PrimitiveRadio.Indicator className={styles.radioDot} />
            </PrimitiveRadio.Item>
            <span className={styles.choiceText}>{option.label}</span>
          </label>
        ))}
      </PrimitiveRadio.Root>
    </ClientOnly>
  );
}

/** boolean 설정을 키보드로 조작 가능한 switch로 표시한다. */
export function Switch({
  label,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
}) {
  const id = useId();

  return (
    <ClientOnly
      fallback={
        <span className={styles.choiceText} aria-busy="true">
          {label} 준비 중
        </span>
      }
    >
      <label htmlFor={id} className={styles.checkLabel}>
        <PrimitiveSwitch.Root
          id={id}
          className={styles.switch}
          checked={checked}
          onCheckedChange={onChange}
          disabled={disabled}
        >
          <PrimitiveSwitch.Thumb className={styles.thumb} />
        </PrimitiveSwitch.Root>
        <span className={styles.choiceText}>{label}</span>
      </label>
    </ClientOnly>
  );
}

/** Radix Select를 value/label 계약으로 제공하고 내부 style 태그에 문서 nonce를 전달한다. */
export function Select({
  label,
  options,
  value,
  onChange,
  disabled,
  loading,
  placeholder = '선택하세요',
  emptyMessage = '선택 가능한 항목이 없습니다.',
  onClear,
  size = 'medium',
  id,
  ref,
  onBlur,
  'aria-describedby': describedBy,
  'aria-invalid': invalid,
  'aria-required': required,
}: {
  label: string;
  options: Option[];
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  loading?: boolean;
  placeholder?: string;
  emptyMessage?: string;
  onClear?: () => void;
  size?: 'small' | 'medium';
} & Pick<
  ComponentPropsWithRef<'button'>,
  'id' | 'ref' | 'onBlur' | 'aria-describedby' | 'aria-invalid' | 'aria-required'
>) {
  const nonce = useDocumentNonce();
  const [open, setOpen] = useState(false);

  if (open && (disabled || loading)) setOpen(false);

  return (
    <ClientOnly fallback={<span className={styles.skeleton} aria-label="선택 준비 중" />}>
      <div className={styles.selectGroup}>
        <PrimitiveSelect.Root
          value={value}
          onValueChange={onChange}
          disabled={disabled || loading}
          open={open}
          onOpenChange={(next) => {
            if (!next || (!disabled && !loading)) setOpen(next);
          }}
        >
          <PrimitiveSelect.Trigger
            className={styles.select}
            data-size={size}
            aria-label={label}
            id={id}
            ref={ref}
            onBlur={onBlur}
            aria-describedby={describedBy}
            aria-invalid={invalid}
            aria-required={required}
          >
            <PrimitiveSelect.Value placeholder={loading ? '불러오는 중' : placeholder} />
            <PrimitiveSelect.Icon>
              <Icon name="down" />
            </PrimitiveSelect.Icon>
          </PrimitiveSelect.Trigger>
          <PrimitiveSelect.Portal>
            <PrimitiveSelect.Content
              className={`${styles.floating} ${styles.selectPopup}`}
              position="popper"
              sideOffset={4}
              collisionPadding={16}
            >
              <PrimitiveSelect.Viewport nonce={nonce} className={styles.selectViewport}>
                {options.length ? (
                  options.map((option) => (
                    <PrimitiveSelect.Item
                      className={styles.option}
                      key={option.value}
                      value={option.value}
                      disabled={disabled || loading || option.disabled}
                    >
                      <PrimitiveSelect.ItemText>{option.label}</PrimitiveSelect.ItemText>
                      <PrimitiveSelect.ItemIndicator>
                        <Icon name="check" />
                      </PrimitiveSelect.ItemIndicator>
                    </PrimitiveSelect.Item>
                  ))
                ) : (
                  <p className={styles.comboboxEmpty}>{emptyMessage}</p>
                )}
              </PrimitiveSelect.Viewport>
            </PrimitiveSelect.Content>
          </PrimitiveSelect.Portal>
        </PrimitiveSelect.Root>
        {onClear && (
          <Button onClick={onClear} disabled={disabled || loading}>
            선택 해제
          </Button>
        )}
      </div>
    </ClientOnly>
  );
}

export type SearchControl = {
  query: string;
  onQueryChange: (query: string) => void;
  status: 'idle' | 'loading' | 'success' | 'error';
  minLength?: number;
  onRetry?: () => void;
  errorMessage?: string;
};

/** 로컬 배열 필터와 외부에서 제어한 검색 상태를 구분한다. 이 컴포넌트는 서버 API를 만들거나 호출하지 않는다. */
export function Combobox({
  label,
  options,
  value,
  onChange,
  disabled,
  loading,
  placeholder = '검색하여 선택',
  emptyMessage = '검색 결과가 없습니다.',
  search,
  selectedLabel,
}: {
  label: string;
  options: Option[];
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  loading?: boolean;
  placeholder?: string;
  emptyMessage?: string;
  search?: SearchControl;
  selectedLabel?: string;
}) {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const [active, setActive] = useState(-1);
  const listId = useId();
  if (open && (disabled || loading)) setOpen(false);
  const listRef = useRef<HTMLDivElement>(null);
  const query = search?.query ?? q;
  const ready = !search || search.status === 'success';
  const filtered = !ready
    ? []
    : search
      ? options
      : options.filter((option) =>
          option.label.toLocaleLowerCase().includes(query.toLocaleLowerCase()),
        );
  useEffect(() => {
    listRef.current
      ?.querySelector(`[data-index="${active}"]`)
      ?.scrollIntoView({ block: 'nearest' });
  }, [active]);

  return (
    <Popover.Root
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        setActive(-1);
      }}
    >
      <Popover.Trigger asChild>
        <Button className={styles.select} disabled={disabled || loading} aria-label={label}>
          <span>
            {loading
              ? '불러오는 중'
              : options.find((option) => option.value === value)?.label ||
                selectedLabel ||
                placeholder}
          </span>
          <Icon name="down" />
        </Button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          className={`${styles.floating} ${styles.comboboxPopup}`}
          sideOffset={8}
          collisionPadding={16}
        >
          <TextInput
            autoFocus
            disabled={disabled || loading}
            role="combobox"
            aria-expanded={open}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={filtered[active] ? `${listId}-${active}` : undefined}
            aria-label={`${label} 검색`}
            value={query}
            onChange={(event) => {
              if (search) search.onQueryChange(event.target.value);
              else setQ(event.target.value);
              setActive(-1);
            }}
            onKeyDown={(event) => {
              if (disabled || loading) return;

              const enabled = filtered.flatMap((option, index) => (option.disabled ? [] : [index]));

              if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
                event.preventDefault();
                const current = enabled.indexOf(active);
                const next =
                  event.key === 'Home'
                    ? 0
                    : event.key === 'End'
                      ? enabled.length - 1
                      : event.key === 'ArrowDown'
                        ? (current + 1) % enabled.length
                        : current <= 0
                          ? enabled.length - 1
                          : current - 1;
                setActive(enabled[next] ?? -1);
              }

              if (
                event.key === 'Enter' &&
                active >= 0 &&
                filtered[active] &&
                !filtered[active].disabled
              ) {
                event.preventDefault();
                onChange(filtered[active].value);
                setOpen(false);
              }
            }}
          />
          <div
            className={styles.comboboxResults}
            role="listbox"
            id={listId}
            aria-label={label}
            ref={listRef}
            aria-busy={search?.status === 'loading'}
          >
            {filtered.map((option, index) => (
              <button
                id={`${listId}-${index}`}
                tabIndex={-1}
                data-active={active === index}
                data-index={index}
                className={styles.option}
                type="button"
                role="option"
                aria-selected={option.value === value}
                disabled={disabled || loading || option.disabled}
                key={option.value}
                onClick={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
              >
                {option.label}
              </button>
            ))}
          </div>
          {search?.status === 'idle' && (
            <p className={styles.comboboxEmpty} role="status">
              {search.minLength ?? 2}글자 이상 입력하면 검색합니다.
            </p>
          )}
          {search?.status === 'loading' && <Spinner inline label="검색하는 중" />}
          {search?.status === 'error' && (
            <ErrorState
              message={search.errorMessage ?? '검색을 완료하지 못했습니다.'}
              onRetry={search.onRetry}
            />
          )}
          {ready && !filtered.length && (
            <p className={styles.comboboxEmpty} role="status">
              {emptyMessage}
            </p>
          )}
          <div className={styles.popupActions}>
            <Button
              disabled={disabled || loading}
              onClick={() => {
                onChange('');
                setOpen(false);
              }}
            >
              선택 해제
            </Button>
            <Popover.Close asChild>
              <Button>닫기</Button>
            </Popover.Close>
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}

/** 복수 선택 요약·개별 해제·최대 수와 명시한 검색 결과 전체 선택을 제공한다. */
export function MultiSelect({
  label,
  options,
  value,
  onChange,
  disabled,
  loading,
  maxSelected,
  selectAll = false,
}: {
  label: string;
  options: Option[];
  value: string[];
  onChange: (value: string[]) => void;
  disabled?: boolean;
  loading?: boolean;
  maxSelected?: number;
  selectAll?: boolean;
}) {
  const [q, setQ] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const filtered = options.filter((option) => option.label.includes(q));
  const enabled = filtered.filter((option) => !option.disabled);
  const selectedCount = enabled.filter((option) => value.includes(option.value)).length;
  const combined = [...new Set([...value, ...enabled.map((option) => option.value)])];
  const locked = disabled || loading;
  const atLimit = maxSelected !== undefined && value.length >= maxSelected;

  return (
    <div className={styles.multiSelect}>
      <p role="status">
        {value.length}개 선택됨{maxSelected !== undefined && ` · 최대 ${maxSelected}개`}
      </p>
      <ul className={styles.tags} aria-label={`${label} 선택한 항목`}>
        {value.map((item) => {
          const name = options.find((option) => option.value === item)?.label ?? item;

          return (
            <li className={styles.tag} key={item}>
              <span>{name}</span>
              <IconButton
                size="small"
                variant="text"
                label={`${name} 선택 해제`}
                icon={<Icon name="close" />}
                disabled={locked}
                onClick={() => {
                  onChange(value.filter((selected) => selected !== item));
                  inputRef.current?.focus();
                }}
              />
            </li>
          );
        })}
      </ul>
      <TextInput
        ref={inputRef}
        aria-label={`${label} 검색`}
        value={q}
        onChange={(event) => setQ(event.target.value)}
        disabled={disabled || loading}
      />
      {selectAll && (
        <Checkbox
          label="검색 결과 전체 선택"
          checked={
            selectedCount === enabled.length && selectedCount > 0
              ? true
              : selectedCount > 0
                ? 'indeterminate'
                : false
          }
          disabled={
            locked ||
            !enabled.length ||
            (maxSelected !== undefined &&
              combined.length > maxSelected &&
              selectedCount !== enabled.length)
          }
          onChange={(checked) =>
            onChange(
              checked
                ? combined
                : value.filter((item) => !enabled.some((option) => option.value === item)),
            )
          }
        />
      )}
      <div className={styles.multiOptions}>
        <CheckboxGroup
          label={loading ? '불러오는 중' : label}
          options={filtered.map((option) => ({
            ...option,
            disabled: option.disabled || (atLimit && !value.includes(option.value)),
          }))}
          value={value}
          onChange={(next) => {
            if (
              maxSelected === undefined ||
              next.length <= maxSelected ||
              next.length < value.length
            )
              onChange(next);
          }}
          disabled={disabled || loading}
        />
      </div>
      {!filtered.length && <p>검색 결과가 없습니다.</p>}
      <Button onClick={() => onChange([])} disabled={disabled || loading}>
        전체 선택 해제
      </Button>
    </div>
  );
}
