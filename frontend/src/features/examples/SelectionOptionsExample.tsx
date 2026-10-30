'use client';

import { useCallback, useRef, useState } from 'react';
import { Checkbox, Combobox, MultiSelect, Select, type Option } from '@/components/ui/Selections';
import { FormField } from '@/components/ui/Form';
import { useOptionSearch } from '@/providers/useOptionSearch';
import { ExampleSection } from './ExampleSection';
import { exampleDelay } from './exampleDelay';
import styles from '@/components/ui/ui.module.css';

const departments: Option[] = Array.from({ length: 30 }, (_, index) => ({
  value: `team-${index + 1}`,
  label:
    index === 29
      ? '여러 지역과 부서에서 함께 검토하는 매우 긴 이름의 운영 지원 담당 부서입니다. OperationsAndPlanningDepartmentWithALongName'
      : `${index % 2 ? '운영' : '기획'}팀 ${String(index + 1).padStart(2, '0')}`,
  disabled: index === 4,
}));

/** 용도별 안내·많은 항목·제어된 검색 상태와 제한 있는 복수 선택을 같은 공통 구현으로 시험한다. */
export function SelectionOptionsExample() {
  const [single, setSingle] = useState('');
  const [selected, setSelected] = useState('');
  const [multiple, setMultiple] = useState(['team-1']);
  const [failure, setFailure] = useState(false);
  const failRequest = useRef(false);
  const lookup = useCallback(async (query: string, signal: AbortSignal) => {
    const shouldFail = failRequest.current;
    await exampleDelay(500, signal);

    if (shouldFail) throw new Error('검색 실패 예제');

    return departments.filter((option) =>
      option.label.toLocaleLowerCase().includes(query.toLocaleLowerCase()),
    );
  }, []);
  const results = useOptionSearch(lookup, { minLength: 2, delayMs: 250 });
  const statuses = {
    idle: '검색 전',
    loading: '검색 중',
    success: '검색 완료',
    error: '검색 실패',
  };

  return (
    <ExampleSection
      title="많은 항목과 검색 상태"
      purpose="용도별 선택 안내와 검색 상태·선택 개수 제한을 확인합니다. 검색은 화면 안에서 응답을 제어하는 예제이며 실제 서버 API를 호출하지 않습니다."
      tryIt="긴 항목을 찾아 선택하고, 검색에 두 글자를 입력하세요. 실패 재현 후 끄고 재시도하거나 복수 선택 태그를 지워 보세요."
      expectedResult="이전 검색은 취소되고 최신 결과만 보입니다. 최대3개까지 선택하며 개별 해제와 검색 결과 전체 선택의 범위가 구분됩니다."
      description="useOptionSearch의 검색 함수는 호출자가 제공합니다. minLength2/delay250ms·AbortSignal·최신 응답 검사를 사용합니다. 로드되지 않은 서버 데이터 전체를 선택하지 않습니다."
      code={
        'const results = useOptionSearch(search, { minLength: 2, delayMs: 250 });\n<Combobox search={results.search} options={results.options} ... />\n<MultiSelect maxSelected={3} selectAll ... />'
      }
      result={
        <>
          <p>기본 선택: {departments.find((option) => option.value === single)?.label || '없음'}</p>
          <p>검색 상태: {statuses[results.search.status]}</p>
          <p>
            검색 선택: {departments.find((option) => option.value === selected)?.label || '없음'}
          </p>
          <p>복수 선택: {multiple.length}개</p>
        </>
      }
    >
      <FormField label="담당 부서">
        <Select
          label="담당 부서"
          options={departments}
          value={single}
          onChange={setSingle}
          onClear={() => setSingle('')}
          placeholder="담당 부서를 선택하세요"
        />
      </FormField>
      <FormField label="빈 부서 목록">
        <Select
          label="빈 부서 목록"
          options={[]}
          value=""
          onChange={() => {}}
          placeholder="선택할 부서 확인"
          emptyMessage="선택할 부서가 없습니다."
        />
      </FormField>
      <div className={styles.stack}>
        <Checkbox
          label="검색 실패 재현"
          checked={failure}
          onChange={(next) => {
            failRequest.current = next;
            setFailure(next);
          }}
        />
        <Combobox
          label="부서 검색"
          options={results.options}
          value={selected}
          onChange={setSelected}
          selectedLabel={departments.find((option) => option.value === selected)?.label}
          search={{
            ...results.search,
            errorMessage: '부서 목록을 불러오지 못했습니다. 다시 시도하세요.',
          }}
          emptyMessage="일치하는 부서가 없습니다."
          placeholder="두 글자로 부서 찾기"
        />
      </div>
      <MultiSelect
        label="참여 부서"
        options={departments}
        value={multiple}
        onChange={setMultiple}
        maxSelected={3}
        selectAll
      />
    </ExampleSection>
  );
}
