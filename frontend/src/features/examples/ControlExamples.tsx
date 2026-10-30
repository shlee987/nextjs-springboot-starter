'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Button, IconButton } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import {
  Badge,
  EmptyState,
  ErrorState,
  InlineAlert,
  Skeleton,
  Spinner,
  useToast,
  toneLabels,
} from '@/components/ui/Feedback';
import { FormField } from '@/components/ui/Form';
import {
  DatePicker,
  DateRangeInput,
  DateTimePicker,
  PasswordInput,
  SearchInput,
  Textarea,
  TextInput,
} from '@/components/ui/Inputs';
import { ActionMenu, Collapsible, Tabs, Tooltip } from '@/components/ui/Navigation';
import { ConfirmDialog, Dialog, SidePanel } from '@/components/ui/Overlays';
import { Pagination, SearchPanel } from '@/components/ui/Search';
import {
  Checkbox,
  CheckboxGroup,
  Combobox,
  MultiSelect,
  RadioGroup,
  Select,
  Switch,
} from '@/components/ui/Selections';
import styles from '@/components/ui/ui.module.css';
import { parseApiDate, toDisplayDate } from '@/lib/dates';
import { useAction } from '@/providers/ActionProvider';
import { exampleDelay } from './exampleDelay';

import { ExampleSection } from './ExampleSection';

const options = [
  { value: 'one', label: '첫 번째' },
  { value: 'two', label: '두 번째' },
  { value: 'disabled', label: '비활성 항목', disabled: true },
];

/** 버튼 변형과 실제 클릭·아이콘·중복 방지를 같은 공통 구현으로 확인한다. */
export function ButtonsExample() {
  const [count, setCount] = useState(0);

  return (
    <ExampleSection
      title="버튼"
      result={<p role="status">실제 클릭: {count}회</p>}
      description="기본 type=button, 크기·변형·아이콘·disabled/loading을 제공합니다."
      code={
        'import { Button, IconButton } from \'@/components/ui/Button\';\n<Button variant="primary" loading={saving}>저장</Button>'
      }
    >
      <div className={styles.row}>
        {(['default', 'primary', 'danger', 'text'] as const).map((variant) => (
          <Button key={variant} variant={variant} onClick={() => setCount(count + 1)}>
            {variant}
          </Button>
        ))}
      </div>
      <div className={styles.row}>
        {(['small', 'medium', 'large'] as const).map((size) => (
          <Button
            size={size}
            key={size}
            leadingIcon={<Icon name="plus" />}
            trailingIcon={<Icon name="right" />}
            onClick={() => setCount(count + 1)}
          >
            {size}
          </Button>
        ))}
        <Button disabled>비활성</Button>
        <Button loading>진행 중</Button>
        <IconButton
          label="카운트 증가"
          icon={<Icon name="plus" />}
          onClick={() => setCount(count + 1)}
        />
      </div>
    </ExampleSection>
  );
}

/** 입력의 ref·label·읽기 전용·오류·비밀번호 관리자를 보존하는 예제다. */
export function InputsExample() {
  const [text, setText] = useState('');
  const [memo, setMemo] = useState('');
  const [weight, setWeight] = useState('10');

  return (
    <ExampleSection
      title="텍스트 입력"
      result={<p>입력값: {text || '아직 입력하지 않았습니다.'}</p>}
      description="공백·HTML 문자를 임의 제거하지 않습니다. React escaping을 유지합니다."
      code={
        'import { TextInput, Textarea } from \'@/components/ui/Inputs\';\n<TextInput value={weight} onChange={changeWeight} prefixAddon="약" suffixAddon="kg" />\n<TextInput value={text} onChange={changeText} showCount maxLength={30} />\n<Textarea value={memo} onChange={changeMemo} autoResize showCount maxLength={300} />'
      }
    >
      <FormField label="텍스트" required help="최대 30자, 지우기 가능">
        <TextInput
          value={text}
          onChange={(event) => setText(event.target.value)}
          onClear={() => setText('')}
          maxLength={30}
          showCount
        />
      </FormField>
      <FormField label="비밀번호">
        <PasswordInput autoComplete="new-password" />
      </FormField>
      <FormField label="설명">
        <Textarea
          value={memo}
          onChange={(event) => setMemo(event.target.value)}
          rows={3}
          autoResize
          showCount
          maxLength={300}
        />
      </FormField>
      <FormField label="검색">
        <SearchInput placeholder="검색어" />
      </FormField>
      <FormField label="무게" help="단위와 지우기 버튼이 입력을 가리지 않습니다.">
        <TextInput
          value={weight}
          onChange={(event) => setWeight(event.target.value)}
          prefixAddon="약"
          suffixAddon="kg"
          onClear={() => setWeight('')}
          aria-label="무게"
        />
      </FormField>
      <FormField label="오류 예시" error="형식을 확인하세요.">
        <TextInput defaultValue="형식 오류" />
      </FormField>
      <FormField label="읽기 전용" horizontal>
        <TextInput readOnly value="수정할 수 없는 공개 값" />
      </FormField>
      <FormField label="비활성">
        <TextInput disabled value="사용 불가" />
      </FormField>
    </ExampleSection>
  );
}

/** 단일·복수·검색 선택의 controlled 계약과 비활성 항목을 실행한다. */
export function SelectionsExample() {
  const [value, setValue] = useState('one');
  const [values, setValues] = useState<string[]>(['one']);
  const [checked, setChecked] = useState(false);
  const [disabled, setDisabled] = useState(false);
  const [loading, setLoading] = useState(false);
  const [empty, setEmpty] = useState(false);
  const displayedOptions = empty ? [] : options;

  return (
    <ExampleSection
      title="선택 컨트롤"
      result={
        <>
          <p>선택한 항목: {options.find((option) => option.value === value)?.label || '없음'}</p>
          <p>
            복수 선택:{' '}
            {values
              .map((value) => options.find((option) => option.value === value)?.label)
              .filter(Boolean)
              .join(', ') || '없음'}
          </p>
          <pre>{JSON.stringify({ single: value, multiple: values }, null, 2)}</pre>
        </>
      }
      description="단일값과 배열을 구분하며 label·키보드·해제를 제공합니다."
      code={
        'import { Select, Checkbox, Combobox, MultiSelect } from \'@/components/ui/Selections\';\n<Select label="항목" options={options} value={value} onChange={setValue} placeholder="항목을 선택하세요" />\n<MultiSelect label="항목" options={options} value={values} onChange={setValues} maxSelected={2} selectAll />'
      }
    >
      <div className={styles.row}>
        <Checkbox label="선택 입력 비활성" checked={disabled} onChange={setDisabled} />
        <Checkbox label="선택 입력 로딩" checked={loading} onChange={setLoading} />
        <Checkbox label="빈 선택 목록" checked={empty} onChange={setEmpty} />
      </div>
      <Checkbox label="확인" checked={checked} onChange={setChecked} />
      <Checkbox label="비활성" checked={true} onChange={() => {}} disabled />
      <Switch label="공통 표시 옵션" checked={checked} onChange={setChecked} disabled={disabled} />
      <RadioGroup
        label="라디오"
        options={displayedOptions}
        value={value}
        onChange={setValue}
        disabled={disabled}
      />
      <Select
        label="단일 선택"
        options={displayedOptions}
        disabled={disabled}
        loading={loading}
        value={value}
        onChange={setValue}
        onClear={() => setValue('')}
      />
      <Combobox
        label="검색 선택"
        options={displayedOptions}
        value={value}
        onChange={setValue}
        disabled={disabled}
        loading={loading}
      />
      <MultiSelect
        label="복수 선택"
        options={displayedOptions}
        value={values}
        onChange={setValues}
        disabled={disabled}
        loading={loading}
        maxSelected={2}
        selectAll
      />
      <CheckboxGroup
        label="공유된 복수 값"
        options={displayedOptions}
        value={values}
        onChange={setValues}
        disabled={disabled}
      />
      <Select label="로딩 예시" options={[]} value="" onChange={() => {}} loading />
    </ExampleSection>
  );
}

/** 날짜만 값은 유지하고 시점은 UTC와 명시한 표시 시간대 사이에서 왕복한다. */
export function DatesExample() {
  const [date, setDate] = useState('2026-10-29');
  const [second, setSecond] = useState('2026-10-29 12:30:00');
  const [millis, setMillis] = useState('2026-10-29 12:30:00.123');
  const [range, setRange] = useState(['2026-10-01', '2026-10-29']);
  const [weekdaysOnly, setWeekdaysOnly] = useState(false);

  return (
    <ExampleSection
      title="날짜·UTC 시각"
      result={
        <pre>
          {JSON.stringify(
            {
              date,
              secondUTC: second,
              millisUTC: millis,
              displaySeoul: millis ? toDisplayDate(millis, 'millisecond') : '',
            },
            null,
            2,
          )}
        </pre>
      }
      description="API: UTC, 화면: Asia/Seoul. yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / yyyy-MM-dd HH:mm:ss.SSS를 엄격 검사합니다."
      code={
        'import { DatePicker, DateTimePicker, DateRangeInput } from \'@/components/ui/Inputs\';\n<DateTimePicker value={utc} onChange={setUtc} precision="millisecond" timeZone="Asia/Seoul" />\n<DateRangeInput from={from} to={to} onChange={changeRange} maxDays={31} presets={presets} />'
      }
    >
      <p className={styles.help}>
        달력 아이콘으로 날짜를 고르거나 직접 입력하세요. 시각은 초·밀리초를 편집한 뒤 확인으로
        반영하고, 취소하면 원래 값을 유지합니다.
      </p>
      <div className={styles.row}>
        <Checkbox
          label="날짜 예제에서 주말 선택 제한"
          checked={weekdaysOnly}
          onChange={setWeekdaysOnly}
        />
      </div>
      <FormField label="날짜" help="2020~2030년만 선택할 수 있습니다. 없는 날짜는 거부합니다.">
        <DatePicker
          value={date}
          onChange={setDate}
          onClear={() => setDate('')}
          min="2020-01-01"
          max="2030-12-31"
          isDateDisabled={
            weekdaysOnly
              ? (day) => [0, 6].includes(parseApiDate(day, 'date').getUTCDay())
              : undefined
          }
        />
      </FormField>
      <FormField label="초까지 시각">
        <DateTimePicker
          aria-label="초까지 시각"
          value={second}
          onChange={setSecond}
          precision="second"
        />
      </FormField>
      <FormField label="밀리초 시각">
        <DateTimePicker aria-label="밀리초 시각" value={millis} onChange={setMillis} />
      </FormField>
      <FormField label="읽기 전용 UTC 시각">
        <DateTimePicker
          value="2026-10-29 12:30:00.123"
          onChange={() => {}}
          timeZone="UTC"
          readOnly
        />
      </FormField>
      <FormField label="비활성 날짜">
        <DatePicker value="2026-10-29" onChange={() => {}} disabled />
      </FormField>
      <DateRangeInput
        from={range[0]}
        to={range[1]}
        onChange={(from, to) => setRange([from, to])}
        maxDays={31}
        presets={[
          { label: '예제 기준 7일', from: '2026-10-23', to: '2026-10-29' },
          { label: '예제 기준 30일', from: '2026-09-30', to: '2026-10-29' },
        ]}
      />
    </ExampleSection>
  );
}

/** 모달·확인·패널에서 포커스 복귀·Escape·진행 중 닫기 거부를 실행한다. */
export function OverlaysExample() {
  const [open, setOpen] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [panel, setPanel] = useState(false);
  const [busy, setBusy] = useState(false);
  const [large, setLarge] = useState(false);
  const [dismissible, setDismissible] = useState(true);
  const [confirmFailure, setConfirmFailure] = useState(false);
  const [confirmMemo, setConfirmMemo] = useState('선택한 조건');
  const confirmAction = useAction('example:confirm');
  const notify = useToast();

  return (
    <ExampleSection
      title="팝업·확인·패널"
      description="진행 중에는 닫기·Escape를 막고 완료 뒤 열기 버튼으로 포커스가 돌아갑니다. 비동기 확인은 완료 Promise를 반환하며 실패하면 입력을 유지하고 성공한 뒤 닫습니다."
      code={
        'import { Dialog, ConfirmDialog, SidePanel } from \'@/components/ui/Overlays\';\n<ConfirmDialog open={open} onOpenChange={setOpen} title="선택 적용" description="선택한 내용을 적용합니다." confirmLabel="선택 적용" intent="primary" autoClose onConfirm={signal => applySelection(signal)} />'
      }
    >
      <Checkbox label="큰 대화상자" checked={large} onChange={setLarge} />
      <Checkbox label="닫기 허용" checked={dismissible} onChange={setDismissible} />
      <div className={styles.row}>
        <Button onClick={() => setOpen(true)}>대화상자 열기</Button>
        <Button onClick={() => setConfirm(true)}>확인 열기</Button>
        <Button onClick={() => setPanel(true)}>패널 열기</Button>
      </div>
      <Dialog
        open={open}
        onOpenChange={setOpen}
        title="공통 대화상자"
        description="화면에서 동작을 확인하는 예제입니다. 서버에 저장하지 않습니다."
        busy={busy}
        size={large ? 'large' : 'medium'}
        dismissible={dismissible}
        footer={
          <Button onClick={() => setBusy(!busy)}>
            {busy ? '진행 상태 해제' : '진행 상태 전환'}
          </Button>
        }
      >
        {!dismissible && (
          <Button disabled={busy} onClick={() => setOpen(false)}>
            예제 종료
          </Button>
        )}
        <FormField label="포커스 확인">
          <TextInput placeholder="Tab / Shift+Tab / Escape" />
        </FormField>
      </Dialog>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title="선택 적용"
        description="실제 데이터는 변경하지 않습니다."
        confirmLabel="선택 적용"
        intent="primary"
        autoClose
        actionKey="example:confirm"
        onConfirm={async (signal) => {
          await exampleDelay(500, signal);

          if (confirmFailure) throw new Error('확인 실패 예제');

          notify('확인 동작을 완료했습니다. 이 예제는 서버에 저장하지 않습니다.', 'success');
        }}
      >
        <FormField label="확인할 메모">
          <TextInput
            value={confirmMemo}
            onChange={(event) => setConfirmMemo(event.target.value)}
            disabled={confirmAction.pending}
          />
        </FormField>
        <Checkbox
          label="확인 실패 재현"
          checked={confirmFailure}
          onChange={setConfirmFailure}
          disabled={confirmAction.pending}
        />
      </ConfirmDialog>
      <SidePanel open={panel} onOpenChange={setPanel} title="정보 패널">
        <p>긴 내용과 키보드 이동을 확인할 수 있는 공통 패널입니다.</p>
        <Button onClick={() => notify('패널 동작', 'info')}>패널 동작</Button>
      </SidePanel>
    </ExampleSection>
  );
}

/** 정상·진행·빈 결과·실패를 각각 표시하며 중복 Toast를 합친다. */
export function FeedbackExample() {
  const [error, setError] = useState(true);
  const [emptyFilter, setEmptyFilter] = useState('일치하지 않는 조건');
  const retry = useAction('example:retry');
  const notify = useToast();

  async function handleRetry() {
    try {
      const result = await retry.run((signal) => exampleDelay(500, signal));

      if (result.status === 'completed') setError(false);
    } catch {
      setError(true);
    }
  }

  return (
    <ExampleSection
      title="알림·화면 상태"
      description="오류를 빈 목록으로 숨기지 않습니다. 동일 문구의 toast는 중복 생성하지 않습니다."
      code={
        "const notify = useToast();\nnotify('완료했습니다.', 'success');\n<EmptyState actions={<Button onClick={resetFilter}>조건 초기화</Button>} />\n<ErrorState message=\"안전한 문구\" onRetry={retry} retrying={pending} />"
      }
    >
      <div className={styles.row}>
        {(['info', 'success', 'warning', 'error'] as const).map((tone) => (
          <Button key={tone} onClick={() => notify('공통 알림 예시', tone)}>
            {toneLabels[tone]} 알림
          </Button>
        ))}
        <Button
          onClick={() =>
            notify(
              '여러 부서에서 함께 확인하는 긴 안내 메시지입니다. 좁은 화면에서도 문장이 자연스럽게 줄바꿈되고, 오른쪽 위 닫기 버튼을 가리지 않는지 확인하세요. 이 예제는 서버에 저장하지 않습니다.',
              'info',
            )
          }
        >
          긴 안내 보기
        </Button>
      </div>
      <InlineAlert tone="info">표시용 안내입니다.</InlineAlert>
      <Badge>읽기 전용</Badge>
      <Spinner />
      <Spinner inline label="변경 내용을 확인하는 중" />
      <Skeleton />
      <FormField label="빈 결과 예제 조건">
        <SearchInput value={emptyFilter} onChange={(event) => setEmptyFilter(event.target.value)} />
      </FormField>
      {'안내 예제'.includes(emptyFilter) ? (
        <p>조회 결과: 안내 예제</p>
      ) : (
        <EmptyState
          message="입력한 조건에 맞는 항목이 없습니다."
          actions={<Button onClick={() => setEmptyFilter('')}>조건 초기화</Button>}
        />
      )}
      {error ? (
        <ErrorState
          message="오류 화면을 재현했습니다. 다시 시도를 누르면 정상 화면으로 바뀝니다."
          traceId="550e8400-e29b-41d4-a716-446655440000"
          retrying={retry.pending}
          onRetry={() => void handleRetry()}
        />
      ) : (
        <InlineAlert tone="success">재시도 동작을 확인했습니다.</InlineAlert>
      )}
    </ExampleSection>
  );
}

/** 탭·Tooltip·접힘·ActionMenu의 키보드 동작과 제어 값을 표시한다. */
export function NavigationExample() {
  const [tab, setTab] = useState('first');
  const [open, setOpen] = useState(false);
  const [action, setAction] = useState('없음');

  return (
    <ExampleSection
      title="탭·보조설명·메뉴"
      description="Tab과 방향키, Enter, Escape로 이동합니다."
      code={
        'import { Tabs, Tooltip, Collapsible, ActionMenu } from \'@/components/ui/Navigation\';\n<Tabs label="내용" value={tab} onChange={setTab} items={items} />'
      }
    >
      <Tabs
        label="예제 탭"
        value={tab}
        onChange={setTab}
        items={[
          { value: 'first', label: '첫 탭', content: <p>첫 탭 내용</p> },
          { value: 'second', label: '둘째 탭', content: <p>둘째 탭 내용</p> },
          { value: 'disabled', label: '비활성', disabled: true, content: null },
        ]}
      />
      <Tooltip text="키보드 포커스로도 표시됩니다.">
        <Button>보조 설명</Button>
      </Tooltip>
      <Collapsible label="상세 내용" open={open} onChange={setOpen}>
        <p>접힌 상태는 부모가 제어합니다.</p>
      </Collapsible>
      <ActionMenu
        label="행 동작"
        actions={[
          { id: 'inspect', label: '확인', onSelect: () => setAction('확인') },
          { id: 'disabled', label: '사용 불가', disabled: true, onSelect: () => {} },
        ]}
      />
      <p>최근 선택: {action}</p>
    </ExampleSection>
  );
}

/** 편집 조건과 적용 조건을 분리하고 API 0 기반 페이지를 화면에 1 기반으로 표시한다. */
export function SearchExample() {
  const [draft, setDraft] = useState('');
  const [applied, setApplied] = useState('');
  const [open, setOpen] = useState(true);
  const [paging, setPaging] = useState({ page: 0, size: 20 });

  return (
    <ExampleSection
      title="검색·페이지"
      result={
        <p>
          적용한 검색어: {applied || '전체'} · 현재 {paging.page + 1}페이지
        </p>
      }
      description="105건의 테스트용 목록입니다. 검색어를 입력하고 조회를 누르면 조건이 적용됩니다. 실제 서버에는 요청하지 않습니다."
      code={
        '<SearchPanel onSearch={apply} onReset={reset} open={open} onOpenChange={setOpen}>...</SearchPanel>\n<Pagination page={page} size={size} totalElements={total} onChange={change} />'
      }
    >
      <SearchPanel
        open={open}
        onOpenChange={setOpen}
        onSearch={() => {
          setApplied(draft);
          setPaging({ ...paging, page: 0 });
        }}
        onReset={() => {
          setDraft('');
          setApplied('');
          setPaging({ page: 0, size: 20 });
        }}
      >
        <FormField label="검색어">
          <SearchInput
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            maxLength={100}
          />
        </FormField>
      </SearchPanel>
      <Pagination
        {...paging}
        totalElements={105}
        onChange={(page, size) => setPaging({ page, size })}
      />
      <Link href="/users">실제 서버 페이지 화면</Link>
    </ExampleSection>
  );
}
