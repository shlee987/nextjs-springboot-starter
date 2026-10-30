'use client';

import { useState, type ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { getMenuItem } from '@/config/navigation';

import { Button } from '@/components/ui/Button';
import styles from '@/components/ui/ui.module.css';

/** 공통 구현을 직접 사용한 결과와 escape된 안전한 코드 예제를 함께 제공한다. */
export function ExampleSection({
  title,
  description,
  code,
  children,
  result,
  purpose,
  tryIt,
  expectedResult,
}: {
  title: string;
  description: string;
  code: string;
  children: ReactNode;
  result?: ReactNode;
  purpose?: string;
  tryIt?: string;
  expectedResult?: string;
}) {
  const [copyResult, setCopyResult] = useState('');
  const example = getMenuItem(usePathname());

  /** 클립보드 권한 실패는 성공으로 숨기지 않고 사용자에게 안내한다. */
  async function copy() {
    try {
      await navigator.clipboard.writeText(code);
      setCopyResult('코드를 복사했습니다.');
    } catch {
      setCopyResult('복사 권한을 확인하거나 코드를 직접 선택해 주세요.');
    }
  }

  return (
    <section className={`${styles.panel} ${styles.example}`}>
      <div className={styles.exampleHeader}>
        <h2>{title}</h2>
        <p className={styles.help}>{purpose ?? example?.description ?? description}</p>
      </div>
      <div className={styles.exampleGuide}>
        <div>
          <strong>해볼 동작</strong>
          <p>{tryIt ?? example?.tryIt ?? '아래 컨트롤의 값을 바꾸고 상태를 비교하세요.'}</p>
        </div>
        <div>
          <strong>예상 결과</strong>
          <p>
            {expectedResult ?? example?.result ?? '변경한 값과 화면 상태를 확인할 수 있습니다.'}
          </p>
        </div>
      </div>
      {result ? (
        <div className={styles.exampleWorkbench}>
          <div className={styles.exampleControls}>{children}</div>
          <section className={styles.exampleResult} aria-label="실행 결과">
            <h3>실행 결과</h3>
            {result}
          </section>
        </div>
      ) : (
        <div className={styles.exampleControls}>{children}</div>
      )}
      <details className={styles.codePanel}>
        <summary>사용 코드 · 주요 속성</summary>
        <p className={styles.help}>{description}</p>
        <pre>
          <code>{code}</code>
        </pre>
        <Button size="small" onClick={() => void copy()}>
          코드 복사
        </Button>
        <span role="status">{copyResult}</span>
      </details>
    </section>
  );
}
