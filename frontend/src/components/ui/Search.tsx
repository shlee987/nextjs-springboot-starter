'use client';

import type { ReactNode } from 'react';

import { Button } from './Button';
import { Collapsible } from './Navigation';
import { Select } from './Selections';
import styles from './ui.module.css';

/** 페이지 크기의 허용값과 공통 Select 표시를 목록 도구모음과 페이지 예제가 함께 사용한다. */
export function PageSizeSelect({
  size,
  onChange,
  disabled,
}: {
  size: number;
  onChange: (size: number) => void;
  disabled?: boolean;
}) {
  return (
    <div className={styles.compactField}>
      <span>페이지 크기</span>
      <div>
        <Select
          label="페이지 크기"
          value={String(size)}
          disabled={disabled}
          size="small"
          options={[10, 20, 50, 100].map((value) => ({
            value: String(value),
            label: `${value}개`,
          }))}
          onChange={(value) => {
            const next = Number(value);
            if ([10, 20, 50, 100].includes(next)) onChange(next);
          }}
        />
      </div>
    </div>
  );
}

/** API page0과 표시 page1을 이 경계에서 변환하고 size 상한 100을 지킨다. */
export function Pagination({
  page,
  size,
  totalElements,
  onChange,
  disabled,
  showSize = true,
}: {
  page: number;
  size: number;
  totalElements: number;
  onChange: (page: number, size: number) => void;
  disabled?: boolean;
  showSize?: boolean;
}) {
  const totalPages = Math.ceil(totalElements / size);

  return (
    <nav className={styles.pagination} aria-label="페이지">
      <span className={styles.pageCount}>
        총 {totalElements}건 · {totalPages ? page + 1 : 0}/{totalPages} 페이지
      </span>
      {showSize && (
        <PageSizeSelect size={size} disabled={disabled} onChange={(next) => onChange(0, next)} />
      )}
      <Button
        size="small"
        disabled={disabled || page <= 0}
        onClick={() => onChange(page - 1, size)}
      >
        이전
      </Button>
      <Button
        size="small"
        disabled={disabled || page + 1 >= totalPages}
        onClick={() => onChange(page + 1, size)}
      >
        다음
      </Button>
    </nav>
  );
}

/** 편집·적용 조건은 부모가 분리하며 Enter도 form submit으로 통일한다. */
export function SearchPanel({
  children,
  onSearch,
  onReset,
  open,
  onOpenChange,
  busy,
}: {
  children: ReactNode;
  onSearch: () => void;
  onReset: () => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  busy?: boolean;
}) {
  return (
    <section className={styles.panel}>
      <Collapsible label="검색 조건" open={open} onChange={onOpenChange}>
        <form
          className={styles.searchForm}
          onSubmit={(event) => {
            event.preventDefault();
            if (!busy) onSearch();
          }}
        >
          <div className={styles.searchFields}>{children}</div>
          <div className={styles.searchActions}>
            <Button type="submit" variant="primary" loading={busy}>
              조회
            </Button>
            <Button onClick={onReset} disabled={busy}>
              초기화
            </Button>
          </div>
        </form>
      </Collapsible>
    </section>
  );
}
