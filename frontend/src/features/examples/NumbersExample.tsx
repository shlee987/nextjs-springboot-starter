'use client';

import { useState } from 'react';

import { Button } from '@/components/ui/Button';
import { FormField } from '@/components/ui/Form';
import { AmountInput, NumberInput } from '@/components/ui/Inputs';
import { InlineAlert } from '@/components/ui/Feedback';
import {
  ExactDecimal,
  calculateTotal,
  divideDecimal,
  formatDecimal,
  parseDecimal,
} from '@/lib/decimal';

import { ExampleSection } from './ExampleSection';

/** 문자열 계산·표시·전송을 구분하고 중간/최종 반올림 차이를 실제 계산한다. */
export function NumbersExample() {
  const [price, setPrice] = useState('0.105');
  const [quantity, setQuantity] = useState('3');
  const [divisor, setDivisor] = useState('3');
  const [bounded, setBounded] = useState('10.5');
  const [result, setResult] = useState<Record<string, string>>();
  const [error, setError] = useState('');

  /** 제수·입력폭을 검증하고 문자열 결과만 JSON 미리보기에 전달한다. */
  function calculate() {
    try {
      const p = parseDecimal(price);
      const q = parseDecimal(quantity);

      setResult({
        exactAddition: new ExactDecimal('0.1').plus('0.2').toFixed(),
        product: p.times(q).toFixed(),
        finalRoundedTotal: calculateTotal([
          { price, quantity },
          { price, quantity },
        ]),
        eachRoundedTotal: p.times(q).toDecimalPlaces(2).times(2).toFixed(2),
        discounted: p.times(q).times('0.9').toFixed(2),
        divided: divideDecimal(price, divisor),
        largeId: '9223372036854775807',
        display: formatDecimal(p.times(q).toFixed(), 2),
      });
      setError('');
    } catch (failure) {
      setResult(undefined);
      setError(failure instanceof Error ? failure.message : '계산 조건을 확인하세요.');
    }
  }

  return (
    <ExampleSection
      title="정밀 숫자와 문자열 전송"
      result={
        error ? (
          <InlineAlert tone="error">{error}</InlineAlert>
        ) : result ? (
          <pre>{JSON.stringify(result, null, 2)}</pre>
        ) : (
          <p>
            값을 입력하고 정밀 계산을 누르면 정확한 계산값과 반올림 결과를 비교할 수 있습니다. 아래
            값은 전송 형식의 미리보기이며 서버에는 보내지 않습니다.
          </p>
        )
      }
      description="precision 40 · HALF_UP · 입력 18 유효자리/소수 6자리 · 합계 최대 1000행. 나눗셈은 근삿값에서 명시 반올림합니다. 아래 JSON은 미리보기이며 실제 HTTP 왕복 시험이 아닙니다."
      code={
        "import { calculateTotal, ExactDecimal } from '@/lib/decimal';\ncalculateTotal([{ price: '0.105', quantity: '3' }]);\nnew ExactDecimal('0.1').plus('0.2').toFixed();"
      }
    >
      <FormField label="단가">
        <NumberInput value={price} onChange={setPrice} />
      </FormField>
      <FormField label="수량">
        <NumberInput value={quantity} onChange={setQuantity} />
      </FormField>
      <FormField label="제수">
        <NumberInput value={divisor} onChange={setDivisor} />
      </FormField>
      <FormField label="금액 표시 전용" help="readOnly·천 단위 표시, 원본은 string">
        <AmountInput value="1234567890123.45" onChange={() => {}} readOnly />
      </FormField>
      <FormField label="비활성 금액">
        <AmountInput value="1234.50" onChange={() => {}} disabled />
      </FormField>
      <FormField label="음수 금지·범위 0~100" help="소수 2자리, 편집 후 범위 오류를 확인합니다.">
        <NumberInput
          value={bounded}
          onChange={setBounded}
          allowNegative={false}
          min="0"
          max="100"
          fractionDigits={2}
        />
      </FormField>
      <Button variant="primary" onClick={calculate}>
        정밀 계산
      </Button>
    </ExampleSection>
  );
}
