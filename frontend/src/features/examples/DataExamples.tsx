'use client';

import Link from 'next/link';
import { useState } from 'react';

import { DataGrid, decimalColumn, dateColumn } from '@/components/ui/DataGrid';
import { BarChart, LineChart, PieChart, type ChartRow } from '@/components/ui/Chart';
import { Tabs } from '@/components/ui/Navigation';
import { Checkbox } from '@/components/ui/Selections';

import { ExampleSection } from './ExampleSection';

const rows = [
  {
    id: '9223372036854775807',
    name: '예제 항목 A',
    amount: '123456789012.345',
    createdAt: '2026-10-29 00:00:00.123',
  },
  {
    id: '9223372036854775806',
    name: '예제 항목 B',
    amount: '9.99',
    createdAt: '2026-10-28 00:00:00.123',
  },
  {
    id: '9223372036854775805',
    name: '예제 항목 C',
    amount: '-10.001',
    createdAt: '2026-10-27 00:00:00.123',
  },
];
type Row = (typeof rows)[number];
const columns = [
  { field: 'id' as const, headerName: '큰 ID · string' },
  { field: 'name' as const, headerName: '이름' },
  decimalColumn<Row>({ field: 'amount', headerName: '정밀 값' }),
  dateColumn<Row>({ field: 'createdAt', headerName: '생성 · Asia/Seoul' }),
];

/** 합성 전체 데이터의 Decimal 정렬·안정 ID 선택을 실행하고 서버 페이지와 구분한다. */
export function GridExample() {
  const [selected, setSelected] = useState<string[]>([]);
  const [single, setSingle] = useState(false);
  const [empty, setEmpty] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  return (
    <ExampleSection
      title="표 정렬과 행 선택"
      description="합성 전체 데이터 client 모드입니다. 헤더로 전체 정렬하며 정밀 값은 Decimal로 비교합니다. /users는 실제 생성 Hook의 서버 페이지·허용 sort를 사용하며 실서버 통신 시험은 별도입니다."
      code={
        'import { DataGrid, decimalColumn } from \'@/components/ui/DataGrid\';\n<DataGrid mode="client" rows={rows} columns={columns} rowKey={row => row.id} selection="multiple" />'
      }
    >
      <Checkbox
        label="단일 행 선택"
        checked={single}
        onChange={(value) => {
          setSingle(value);
          setSelected([]);
        }}
      />
      <Checkbox
        label="빈 목록"
        checked={empty}
        onChange={(value) => {
          setEmpty(value);
          setSelected([]);
        }}
      />
      <Checkbox
        label="목록 로딩"
        checked={loading}
        onChange={(value) => {
          setLoading(value);
          setSelected([]);
        }}
      />
      <Checkbox
        label="목록 오류 재현"
        checked={error}
        onChange={(value) => {
          setError(value);
          setSelected([]);
        }}
      />
      <DataGrid
        key={single ? 'single' : 'multiple'}
        mode="client"
        rows={empty ? [] : rows}
        columns={columns}
        rowKey={(row) => row.id}
        selection={single ? 'single' : 'multiple'}
        loading={loading}
        error={
          error ? '목록 오류를 재현했습니다. 다시 시도를 누르면 목록이 나타납니다.' : undefined
        }
        onRetry={() => setError(false)}
        onSelectionChange={setSelected}
      />
      <p>선택 ID: {selected.join(', ') || '없음'}</p>
      <Link href="/users">실제 서버 페이징 조회</Link>
    </ExampleSection>
  );
}

const chartRows: ChartRow[] = [
  { label: '1월', value: '12.345' },
  { label: '2월', value: '18.105' },
  { label: '3월', value: '15.9' },
];

/** 3종 차트·빈 값·로딩·오류·탭 재마운트를 공통 wrapper로 실행한다. */
export function ChartsExample() {
  const [tab, setTab] = useState('bar');
  const [empty, setEmpty] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const props = {
    title: '월별 값 예시',
    rows: empty ? [] : chartRows,
    loading,
    error: error ? '차트 오류를 재현했습니다. 다시 시도를 눌러 보세요.' : undefined,
    onRetry: () => setError(false),
  };

  return (
    <ExampleSection
      title="차트 종류와 화면 상태"
      description="HTML tooltip·외부 자원 없이 richText tooltip을 사용합니다. 탭을 바꾸거나 화면 크기를 조절해 resize/dispose를 확인하세요."
      code={
        "import { BarChart, LineChart, PieChart } from '@/components/ui/Chart';\n<BarChart title=\"월별 값\" rows={[{ label: '1월', value: '12.345' }]} />"
      }
    >
      <Checkbox label="빈 데이터" checked={empty} onChange={setEmpty} />
      <Checkbox label="로딩" checked={loading} onChange={setLoading} />
      <Checkbox label="차트 오류 재현" checked={error} onChange={setError} />
      <Tabs
        label="차트 종류"
        value={tab}
        onChange={setTab}
        items={[
          { value: 'bar', label: '막대', content: <BarChart {...props} /> },
          { value: 'line', label: '선', content: <LineChart {...props} /> },
          { value: 'pie', label: '원형', content: <PieChart {...props} /> },
        ]}
      />
    </ExampleSection>
  );
}
