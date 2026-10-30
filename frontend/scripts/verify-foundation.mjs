import assert from 'node:assert/strict';

import { getEnvironment } from '../src/config/environment.ts';
import {
  ExactDecimal,
  calculateTotal,
  divideDecimal,
  formatDecimal,
  parseDecimal,
  toChartNumber,
} from '../src/lib/decimal.ts';
import {
  fromDisplayDate,
  getMonthDays,
  parseApiDate,
  shiftCalendarDay,
  shiftCalendarMonth,
  toDisplayDate,
  validateDateRange,
} from '../src/lib/dates.ts';

// 외부 API·DB·브라우저 없이 날짜·정밀수치·예제 공개 입력의 회귀를 확인한다.
let checks = 0;

verify('달력 월말·윤년·0001~9999 이동 경계', () => {
  assert.equal(getMonthDays(1, 2), 28);
  assert.equal(getMonthDays(2000, 2), 29);
  assert.equal(getMonthDays(1900, 2), 28);
  assert.equal(shiftCalendarMonth('2024-01-31', 1), '2024-02-29');
  assert.equal(shiftCalendarMonth('2023-01-31', 1), '2023-02-28');
  assert.equal(shiftCalendarMonth('0099-12-31', 1), '0100-01-31');
  assert.equal(shiftCalendarMonth('0001-01-31', -1), '0001-01-31');
  assert.equal(shiftCalendarMonth('9999-12-31', 1), '9999-12-31');
  assert.equal(shiftCalendarDay('2024-02-28', 1), '2024-02-29');
  assert.equal(shiftCalendarDay('0001-01-01', -1), '0001-01-01');
  assert.equal(shiftCalendarDay('9999-12-31', 1), '9999-12-31');
});

/** 성공 값과 경계 실패를 실제 공개 함수의 결과로 검증한다. */
function verify(name, run) {
  run();
  checks += 1;
  console.log(`PASS ${name}`);
}

verify('문자열 소수 덧셈과 큰 정수 표시', () => {
  assert.equal(parseDecimal('0.1').plus(parseDecimal('0.2')).toString(), '0.3');
  assert.equal(formatDecimal('9007199254740993'), '9,007,199,254,740,993');
  assert.equal(formatDecimal('1234.125', 2), '1,234.13');
  assert.equal(new ExactDecimal('1').div('3').toFixed(6), '0.333333');
  assert.equal(divideDecimal('1', '3'), '0.333333');
  assert.throws(() => divideDecimal('1', '0'));
  assert.throws(() => divideDecimal('1', '-0'));
});
verify('금액 합계는 마지막에 HALF_UP 반올림', () => {
  assert.equal(
    calculateTotal([
      { price: '0.105', quantity: '1' },
      { price: '0.105', quantity: '1' },
    ]),
    '0.21',
  );
  assert.equal(calculateTotal([{ price: '-0.105', quantity: '1' }]), '-0.11');
  assert.equal(
    calculateTotal(Array.from({ length: 1000 }, () => ({ price: '0.1', quantity: '0.2' }))),
    '20.00',
  );
  assert.equal(calculateTotal([]), '0.00');
});
verify('지수·비유한값·입력 폭·행 상한 거부', () => {
  for (const value of [
    '1e2',
    'NaN',
    'Infinity',
    '01',
    '1.',
    '-',
    '0.1234567',
    '1234567890123456789',
  ]) {
    assert.throws(() => parseDecimal(value));
  }
  assert.throws(() =>
    calculateTotal(Array.from({ length: 1001 }, () => ({ price: '1', quantity: '1' }))),
  );
  assert.throws(() =>
    calculateTotal([
      { price: '999999999999999999', quantity: '999999999999999999' },
      { price: '0.000001', quantity: '0.000001' },
    ]),
  );
});
verify('차트에만 제한된 number 좌표 변환', () => {
  assert.equal(toChartNumber('12.345'), 12.345);
  assert.equal(toChartNumber('1000000000000'), 1000000000000);
  assert.throws(() => toChartNumber('1000000000001'));
});
verify('UTC 세 형식과 서울 날짜 경계 왕복', () => {
  for (const [precision, value] of [
    ['date', '2024-02-29'],
    ['second', '2026-12-31 23:59:59'],
    ['millisecond', '2026-12-31 23:59:59.123'],
  ]) {
    assert.equal(fromDisplayDate(toDisplayDate(value, precision), precision), value);
    assert.equal(toDisplayDate(value, precision, 'UTC'), value);
  }
  assert.equal(toDisplayDate('2026-12-31 23:59:59.123', 'millisecond'), '2027-01-01 08:59:59.123');
  assert.equal(parseApiDate('0001-01-01', 'date').getUTCFullYear(), 1);
  assert.equal(parseApiDate('9999-12-31', 'date').getUTCFullYear(), 9999);
});
verify('존재하지 않는 날짜·암묵 파싱·초 정밀도 혼용 거부', () => {
  for (const value of ['2023-02-29', '0000-01-01', '2026-13-01', '2026-04-31', '2026-1-01']) {
    assert.throws(() => parseApiDate(value, 'date'));
  }
  for (const value of [
    '2026-01-01T00:00:00',
    '2026-01-01 24:00:00',
    '2026-01-01 00:00:60',
    '2026-01-01 00:00:00.000',
  ]) {
    assert.throws(() => parseApiDate(value, 'second'));
  }
});
verify('기간 순서와 이력 31일 상한', () => {
  assert.equal(validateDateRange('2026-01-01', '2026-02-01', 'date', 31), true);
  assert.equal(validateDateRange('2026-01-01', '2026-02-02', 'date', 31), false);
  assert.equal(validateDateRange('2026-02-01', '2026-01-01', 'date'), false);
  assert.equal(validateDateRange('invalid', '2026-01-01', 'date'), false);
});
verify('서울 과거 DST·초 단위 오프셋과 누락/중복 현지 시각', () => {
  assert.equal(toDisplayDate('1988-06-01 00:00:00', 'second'), '1988-06-01 10:00:00');
  assert.equal(fromDisplayDate('1988-06-01 10:00:00', 'second'), '1988-06-01 00:00:00');
  assert.equal(toDisplayDate('1900-01-01 00:00:00', 'second'), '1900-01-01 08:27:52');
  assert.equal(fromDisplayDate('1900-01-01 08:27:52', 'second'), '1900-01-01 00:00:00');
  assert.throws(() => fromDisplayDate('1988-05-08 02:30:00', 'second'));
  assert.throws(() => fromDisplayDate('1988-10-09 02:30:00', 'second'));
});
verify('네 환경 예제 기본값·명시 override·잘못된 boolean', () => {
  for (const APP_ENV of ['local', 'dev', 'stg', 'prod']) {
    const base = {
      APP_ENV,
      APP_PUBLIC_ORIGIN:
        APP_ENV === 'local' ? 'http://localhost:3000' : 'https://app.example.invalid',
    };
    assert.equal(getEnvironment(base).examplesEnabled, APP_ENV === 'local');
    assert.equal(getEnvironment({ ...base, APP_EXAMPLES_ENABLED: 'true' }).examplesEnabled, true);
    assert.equal(getEnvironment({ ...base, APP_EXAMPLES_ENABLED: 'false' }).examplesEnabled, false);
    assert.throws(() => getEnvironment({ ...base, APP_EXAMPLES_ENABLED: '1' }));
  }
});
console.log(`PASS ${checks} foundation regression groups; no external connections.`);
