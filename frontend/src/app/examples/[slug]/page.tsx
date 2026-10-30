import { notFound } from 'next/navigation';

import { exampleItems } from '@/config/navigation';
import {
  ButtonsExample,
  DatesExample,
  FeedbackExample,
  InputsExample,
  NavigationExample,
  OverlaysExample,
  SearchExample,
  SelectionsExample,
} from '@/features/examples/ControlExamples';
import { ChartsExample, GridExample } from '@/features/examples/DataExamples';
import { FormsExample } from '@/features/examples/FormsExample';
import { NumbersExample } from '@/features/examples/NumbersExample';
import { ApiExample, AuthExample } from '@/features/examples/IntegrationExamples';
import { ActionExample } from '@/features/examples/ActionExample';
import { FilesExample } from '@/features/examples/FilesExample';
import { SelectionOptionsExample } from '@/features/examples/SelectionOptionsExample';
import styles from '@/components/ui/ui.module.css';

/** 기본 버튼과 작업 잠금을 같은 메뉴에서 이어서 체험한다. */
function ButtonExamples() {
  return (
    <div className={styles.pageStack}>
      <ButtonsExample />
      <ActionExample />
    </div>
  );
}
/** 기본 선택과 큰 목록·검색 상태를 같은 메뉴에서 이어서 체험한다. */
function SelectionExamples() {
  return (
    <div className={styles.pageStack}>
      <SelectionsExample />
      <SelectionOptionsExample />
    </div>
  );
}

const examples = {
  files: FilesExample,
  buttons: ButtonExamples,
  inputs: InputsExample,
  selections: SelectionExamples,
  dates: DatesExample,
  overlays: OverlaysExample,
  feedback: FeedbackExample,
  navigation: NavigationExample,
  search: SearchExample,
  numbers: NumbersExample,
  forms: FormsExample,
  grid: GridExample,
  charts: ChartsExample,
  api: ApiExample,
  auth: AuthExample,
};

/** registry에 등록된 예제만 제공하며 공통 구현을 직접 실행한다. */
export default async function ExamplePage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const entry = exampleItems.find((item) => item.id === slug);

  if (!entry || !Object.hasOwn(examples, slug)) {
    notFound();
  }

  const Component = examples[slug as keyof typeof examples];

  return (
    <>
      <h1>{entry.label}</h1>
      <Component />
    </>
  );
}
