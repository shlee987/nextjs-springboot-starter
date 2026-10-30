'use client';

import { AllCommunityModule, ModuleRegistry, themeQuartz, type ColDef } from 'ag-grid-community';
import { AgGridReact } from 'ag-grid-react';

import { ExactDecimal, formatDecimal } from '@/lib/decimal';
import { toDisplayDate, type DatePrecision } from '@/lib/dates';
import { useDocumentNonce } from '@/providers/AppProviders';

import { ClientOnly } from './ClientOnly';
import { EmptyState, ErrorState, Spinner } from './Feedback';
import { PageSizeSelect, Pagination } from './Search';
import { Select } from './Selections';
import styles from './ui.module.css';

ModuleRegistry.registerModules([AllCommunityModule]);
const theme = themeQuartz.withParams({
  fontFamily: 'var(--font-sans)',
  fontSize: 14,
  accentColor: 'var(--color-primary)',
});

/** 문자열 숫자는 Decimal 비교로 정렬하며 표시도 Number 중간 변환 없이 처리한다. */
export function decimalColumn<T extends object>(column: ColDef<T>): ColDef<T> {
  return {
    ...column,
    comparator: (a: string | null | undefined, b: string | null | undefined) =>
      a == null && b == null
        ? 0
        : a == null
          ? -1
          : b == null
            ? 1
            : new ExactDecimal(a).comparedTo(b),
    valueFormatter: ({ value }) => (value == null ? '' : formatDecimal(String(value))),
  };
}

/** 날짜 formatter는 API UTC 계약과 화면 시간대를 명시한다. */
export function dateColumn<T extends object>(
  column: ColDef<T>,
  precision: DatePrecision = 'millisecond',
): ColDef<T> {
  return {
    ...column,
    valueFormatter: ({ value }) => (value ? toDisplayDate(String(value), precision) : ''),
  };
}

type Page = {
  page: number;
  size: number;
  totalElements: number;
  onChange: (page: number, size: number) => void;
};
type Props<T extends object> = {
  rows: T[];
  columns: ColDef<T>[];
  rowKey: (row: T) => string;
  loading?: boolean;
  error?: string;
  errorTraceId?: string;
  onRetry?: () => void;
  selection?: 'single' | 'multiple';
  onSelectionChange?: (ids: string[]) => void;
} & (
  | { mode: 'client'; pagination?: never; sort?: never }
  | {
      mode: 'server';
      pagination: Page;
      sort?: {
        value: string;
        options: { value: string; label: string }[];
        onChange: (value: string) => void;
      };
    }
);

/** Community row model에서 client 전체 정렬과 서버 한 페이지를 명시적으로 분리한다. */
export function DataGrid<T extends object>(props: Props<T>) {
  const nonce = useDocumentNonce();

  if (props.error) {
    return (
      <ErrorState message={props.error} traceId={props.errorTraceId} onRetry={props.onRetry} />
    );
  }

  if (props.loading) {
    return <Spinner label="목록을 불러오는 중" />;
  }

  const columns = props.columns.map((column) => ({
    ...column,
    sortable: props.mode === 'client' && column.sortable !== false,
    filter: false,
  }));

  return (
    <div className={styles.results}>
      {props.mode === 'server' && (
        <div className={styles.toolbar}>
          <span className={styles.help}>
            총 {props.pagination.totalElements}건 · 서버 전체 결과
          </span>
          <div className={styles.row}>
            {props.sort && (
              <Select
                label="전체 결과 정렬"
                value={props.sort.value}
                options={props.sort.options}
                onChange={(value) => props.sort?.onChange(value)}
                size="small"
              />
            )}
            <PageSizeSelect
              size={props.pagination.size}
              onChange={(size) => props.pagination.onChange(0, size)}
            />
          </div>
        </div>
      )}
      {!props.rows.length ? (
        <EmptyState />
      ) : (
        <ClientOnly fallback={<Spinner />}>
          <div className={styles.scroll}>
            <div className={styles.grid}>
              <AgGridReact<T>
                theme={theme}
                styleNonce={nonce}
                loadThemeGoogleFonts={false}
                rowData={props.rows}
                columnDefs={columns}
                defaultColDef={{ flex: 1, minWidth: 130, resizable: true }}
                getRowId={({ data }) => props.rowKey(data)}
                rowSelection={
                  props.selection
                    ? {
                        mode: props.selection === 'single' ? 'singleRow' : 'multiRow',
                        enableClickSelection: true,
                      }
                    : undefined
                }
                onSelectionChanged={(event) =>
                  props.onSelectionChange?.(event.api.getSelectedRows().map(props.rowKey))
                }
              />
            </div>
          </div>
        </ClientOnly>
      )}
      {props.mode === 'server' && <Pagination {...props.pagination} showSize={false} />}
      <p className={styles.help}>
        행 선택은 현재 페이지에만 적용됩니다. 이 예제는 셀 편집·일괄 저장·파일 내보내기를 제공하지
        않습니다.
      </p>
    </div>
  );
}
