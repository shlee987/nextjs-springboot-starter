import Decimal from 'decimal.js';

/** 예제의 유효 폭에서 계산하며 전역 Decimal 설정을 변경하지 않는다. */
export const ExactDecimal = Decimal.clone({
  precision: 40,
  rounding: Decimal.ROUND_HALF_UP,
  toExpNeg: -40,
  toExpPos: 40,
});

/** 최대 18 유효자리·소수 6자리의 정밀 문자열을 검사하고 지수·무한값을 거부한다. */
export function parseDecimal(value: string): Decimal {
  if (value.length > 26 || !/^-?(?:0|[1-9]\d{0,17})(?:\.\d{1,6})?$/.test(value)) {
    throw new Error('소수는 지수 없이 최대 6자리로 입력하세요.');
  }

  const decimal = new ExactDecimal(value);

  if (!decimal.isFinite() || decimal.sd() > 18) {
    throw new Error('입력은 최대 18 유효자리까지 허용합니다.');
  }

  return decimal;
}

/** 정밀 문자열을 Number 변환 없이 천 단위로 표시한다. 원본 값은 변경하지 않는다. */
export function formatDecimal(value: string, fractionDigits?: number): string {
  const decimal = new ExactDecimal(value);

  if (!decimal.isFinite()) {
    throw new Error('유한한 숫자만 표시할 수 있습니다.');
  }

  const [whole, fraction] = (
    fractionDigits === undefined ? decimal.toFixed() : decimal.toFixed(fractionDigits)
  ).split('.');

  return `${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}${fraction === undefined ? '' : `.${fraction}`}`;
}

/** 1000행 이내 단가×수량 합계를 정밀 계산하고 마지막에만 소수 두 자리로 반올림한다. */
export function calculateTotal(rows: { price: string; quantity: string }[]): string {
  if (rows.length > 1000) {
    throw new Error('계산은 최대 1000행까지 가능합니다.');
  }

  const products = rows.map((row) => parseDecimal(row.price).times(parseDecimal(row.quantity)));
  const integerWidth = Math.max(
    1,
    ...products.map((value) => value.abs().trunc().toFixed().length),
  );
  const fractionWidth = Math.max(0, ...products.map((value) => value.dp()));

  if (integerWidth + fractionWidth + String(rows.length).length > 40) {
    throw new Error('합계의 정수·소수 폭이 precision 40을 넘을 수 있습니다. 입력 범위를 줄이세요.');
  }

  return products.reduce((total, value) => total.plus(value), new ExactDecimal(0)).toFixed(2);
}

/** 문자열 제수의 0을 거부하고 precision 40의 나눗셈 결과를 명시한 소수 자리로 반올림한다. */
export function divideDecimal(value: string, divisor: string, fractionDigits = 6): string {
  const numerator = parseDecimal(value);
  const denominator = parseDecimal(divisor);

  if (
    denominator.isZero() ||
    !Number.isInteger(fractionDigits) ||
    fractionDigits < 0 ||
    fractionDigits > 40
  ) {
    throw new Error('제수는 0이 아니어야 하며 소수 자리는 0~40이어야 합니다.');
  }

  return numerator.div(denominator).toFixed(fractionDigits);
}

/** 차트 좌표에 한정해 유한하고 표현 가능한 범위의 근삿값으로 변환한다. */
export function toChartNumber(value: string): number {
  const decimal = parseDecimal(value);

  if (decimal.abs().gt('1000000000000')) {
    throw new Error('차트 좌표는 절댓값 1조 이내로 제한합니다.');
  }

  return decimal.toNumber();
}
