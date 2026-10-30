export type DatePrecision = 'date' | 'second' | 'millisecond';
export type DisplayTimeZone = 'UTC' | 'Asia/Seoul';
export const DISPLAY_TIME_ZONE: DisplayTimeZone = 'Asia/Seoul';

/** 달력 월의 마지막 날짜를 구하며 Date 생성자의 0~99년 보정을 피한다. */
export function getMonthDays(year: number, month: number): number {
  parseApiDate(`${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-01`, 'date');
  const date = new Date(0);
  date.setUTCFullYear(year, month, 0);

  return date.getUTCDate();
}

/** 달력의 일 이동은 0001~9999 경계에서 멈춘다. 시스템 시간대는 사용하지 않는다. */
export function shiftCalendarDay(value: string, days: number): string {
  const date = parseApiDate(value, 'date');
  date.setUTCDate(date.getUTCDate() + days);

  if (date.getUTCFullYear() < 1) {
    return '0001-01-01';
  }

  if (date.getUTCFullYear() > 9999) {
    return '9999-12-31';
  }

  return formatApiDate(date, 'date');
}

/** 월 이동은 월말을 새 월의 마지막 날로 제한하고 계약 연도 밖으로 나가지 않는다. */
export function shiftCalendarMonth(value: string, months: number): string {
  const date = parseApiDate(value, 'date');
  const index = Math.max(
    0,
    Math.min(9999 * 12 - 1, (date.getUTCFullYear() - 1) * 12 + date.getUTCMonth() + months),
  );
  const year = Math.floor(index / 12) + 1;
  const month = (index % 12) + 1;
  const day = Math.min(date.getUTCDate(), getMonthDays(year, month));

  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}
const seoulFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul',
  calendar: 'iso8601',
  numberingSystem: 'latn',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/** 계약의 세 문자열 형식을 UTC로 엄격 파싱한다. 날짜만 값도 UTC 자정으로 검사한다. */
export function parseApiDate(value: string, precision: DatePrecision): Date {
  const patterns = {
    date: /^(\d{4})-(\d{2})-(\d{2})$/,
    second: /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})$/,
    millisecond: /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})$/,
  };
  const parts = patterns[precision].exec(value);

  if (!parts) {
    throw new Error('날짜 형식이 계약과 일치하지 않습니다.');
  }

  const [year, month, day, hour = 0, minute = 0, second = 0, millis = 0] = parts
    .slice(1)
    .map(Number);
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  date.setUTCHours(hour, minute, second, millis);

  if (
    year < 1 ||
    year > 9999 ||
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day ||
    date.getUTCHours() !== hour ||
    date.getUTCMinutes() !== minute ||
    date.getUTCSeconds() !== second ||
    date.getUTCMilliseconds() !== millis
  ) {
    throw new Error('존재하지 않는 날짜 또는 시각입니다.');
  }

  return date;
}

/** 시점을 계약 형식으로 직렬화한다. 날짜만 값에는 표시 시간대 변환을 하지 않는다. */
export function formatApiDate(date: Date, precision: DatePrecision): string {
  const iso = date.toISOString();

  if (date.getUTCFullYear() < 1 || date.getUTCFullYear() > 9999) {
    throw new Error('연도는 0001~9999 범위여야 합니다.');
  }

  return precision === 'date'
    ? iso.slice(0, 10)
    : iso.slice(0, precision === 'second' ? 19 : 23).replace('T', ' ');
}

/** UTC 계약 문자열을 명시한 표시 시간대의 같은 정밀도 문자열로 변환한다. */
export function toDisplayDate(
  value: string,
  precision: DatePrecision,
  timeZone: DisplayTimeZone = DISPLAY_TIME_ZONE,
): string {
  const date = parseApiDate(value, precision);

  if (precision === 'date' || timeZone === 'UTC') {
    return formatApiDate(date, precision);
  }

  const parts = seoulFormatter.formatToParts(date);
  const fields = new Map(parts.map((part) => [part.type, part.value]));
  const text = `${fields.get('year')?.padStart(4, '0')}-${fields.get('month')}-${fields.get('day')} ${fields.get('hour')}:${fields.get('minute')}:${fields.get('second')}${precision === 'millisecond' ? `.${String(date.getUTCMilliseconds()).padStart(3, '0')}` : ''}`;
  parseApiDate(text, precision);

  return text;
}

/** IANA 서울 규칙으로 역변환하며 DST의 누락·중복 현지 시각은 추측하지 않고 거부한다. */
export function fromDisplayDate(
  value: string,
  precision: DatePrecision,
  timeZone: DisplayTimeZone = DISPLAY_TIME_ZONE,
): string {
  const date = parseApiDate(value, precision);

  if (precision === 'date' || timeZone === 'UTC') {
    return formatApiDate(date, precision);
  }

  const offsets = new Set<number>();

  for (const days of [-2, -1, 0, 1, 2]) {
    const sample = new Date(date.getTime() + days * 86400000);

    try {
      const utc = formatApiDate(sample, 'millisecond');
      const local = toDisplayDate(utc, 'millisecond', timeZone);
      offsets.add(parseApiDate(local, 'millisecond').getTime() - sample.getTime());
    } catch {
      // 계약 연도 범위를 벗어난 가장자리 표본은 제외한다.
    }
  }

  const candidates = [...offsets].flatMap((offset) => {
    try {
      const candidate = formatApiDate(new Date(date.getTime() - offset), precision);
      return toDisplayDate(candidate, precision, timeZone) === value ? [candidate] : [];
    } catch {
      return [];
    }
  });

  if (candidates.length !== 1) {
    throw new Error('존재하지 않거나 중복된 현지 시각입니다. UTC로 입력하세요.');
  }

  return candidates[0];
}

/** 시작≤종료·최대 기간을 검사한다. 이력의 종료 시각은 exclusive다. */
export function validateDateRange(
  from: string,
  to: string,
  precision: DatePrecision,
  maxDays?: number,
): boolean {
  try {
    const duration =
      parseApiDate(to, precision).getTime() - parseApiDate(from, precision).getTime();

    return duration >= 0 && (maxDays === undefined || duration <= maxDays * 86400000);
  } catch {
    return false;
  }
}
