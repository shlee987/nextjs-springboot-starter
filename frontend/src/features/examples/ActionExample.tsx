'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Selections';
import { InlineAlert } from '@/components/ui/Feedback';
import { ActionProvider, useAction } from '@/providers/ActionProvider';
import { ExampleSection } from './ExampleSection';
import { exampleDelay } from './exampleDelay';
import styles from '@/components/ui/ui.module.css';

/** 서로 다른 컴포넌트의 버튼이 같은 작업 키의 진행 상태를 구독한다. */
function SharedSaveButton({ label, onRun }: { label: string; onRun: () => void }) {
  const action = useAction('example:save');

  return (
    <Button loading={action.pending} onClick={onRun}>
      {label}
    </Button>
  );
}

/** 재렌더 전 연타·다른 키 병렬·실패·동기 예외·실제 취소를 서버 호출 없이 실행한다. */
function ActionControls() {
  const save = useAction('example:save');
  const other = useAction('example:other');
  const [mode, setMode] = useState('success');
  const [counts, setCounts] = useState({ started: 0, completed: 0, other: 0, ignored: 0 });
  const [message, setMessage] = useState('아직 실행하지 않았습니다.');

  async function handleSave() {
    try {
      const result = await save.run((signal) => {
        setCounts((current) => ({ ...current, started: current.started + 1 }));

        if (mode === 'sync') throw new Error('동기 실패 예제');

        return exampleDelay(1200, signal).then(() => {
          if (mode === 'failure') throw new Error('비동기 실패 예제');

          return '저장 테스트를 완료했습니다.';
        });
      });

      if (result.status === 'completed') {
        setCounts((current) => ({ ...current, completed: current.completed + 1 }));
        setMessage(result.value);
      }
      if (result.status === 'ignored')
        setCounts((current) => ({ ...current, ignored: current.ignored + 1 }));
    } catch {
      setMessage('실패를 재현했습니다. 다시 실행할 수 있습니다.');
    }
  }

  async function handleOther() {
    try {
      const result = await other.run((signal) => exampleDelay(800, signal));

      if (result.status === 'completed')
        setCounts((current) => ({ ...current, other: current.other + 1 }));
    } catch {
      setMessage('다른 작업을 완료하지 못했습니다.');
    }
  }

  return (
    <section className={`${styles.stack} ${styles.formPanel}`} aria-label="작업 실행 컨트롤">
      <Select
        label="작업 결과 재현"
        options={[
          { value: 'success', label: '정상 완료' },
          { value: 'failure', label: '비동기 실패' },
          { value: 'sync', label: '동기 예외' },
        ]}
        value={mode}
        onChange={setMode}
      />
      <div className={styles.row}>
        <SharedSaveButton label="저장 테스트" onRun={() => void handleSave()} />
        <SharedSaveButton label="다른 위치에서 저장" onRun={() => void handleSave()} />
        <Button
          onClick={() => {
            void handleSave();
            void handleSave();
          }}
        >
          연속 두 번 실행
        </Button>
        <Button loading={other.pending} onClick={() => void handleOther()}>
          다른 작업 실행
        </Button>
        <Button disabled={!save.pending} onClick={save.cancel}>
          저장 작업 취소
        </Button>
      </div>
      <InlineAlert>{message}</InlineAlert>
      <pre aria-label="작업 실행 결과">{JSON.stringify(counts, null, 2)}</pre>
    </section>
  );
}

/** 범위 전환은 실제 로그인 변경 없이도 이전 작업 격리를 확인하는 제어된 예제다. */
export function ActionExample() {
  const [scope, setScope] = useState(1);

  return (
    <ExampleSection
      title="작업별 중복 실행 방지"
      purpose="같은 저장 작업의 중복 실행을 막고 다른 작업은 함께 실행합니다. 이 예제는 서버에 저장하지 않습니다."
      tryIt="연속 두 번 실행을 누르거나 저장 버튼 두 개를 비교하세요. 실패·취소 후 다시 실행하고 진행 중 범위도 바꿔 보세요."
      expectedResult="같은 키는 한 번만 시작하고 버튼 두 개가 함께 진행 상태가 됩니다. 다른 키는 병렬로 실행되며 종료 뒤 다시 실행할 수 있습니다."
      description="ActionProvider 범위는 실제 화면에서는 인증 세대와 경로로 구분합니다. 중복은 ignored, 취소 종료는 cancelled, 성공은 completed로 반환하며 원래 실패는 reject합니다. 서버 idempotency나 탭 간 보호가 아닙니다."
      code={
        'const save = useAction("example:save");\nconst result = await save.run(signal => saveChanges(signal));\nif (result.status === "completed") updateView(result.value);'
      }
    >
      <div className={styles.row}>
        <span>예제 범위: {scope}</span>
        <Button onClick={() => setScope((value) => value + 1)}>화면 범위 전환</Button>
      </div>
      <ActionProvider identity={`example:${scope}`}>
        <ActionControls />
      </ActionProvider>
    </ExampleSection>
  );
}
