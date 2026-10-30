import type { ComponentPropsWithoutRef } from 'react';

const paths = {
  check: 'm5 12 4 4L19 6',
  minus: 'M5 12h14',
  down: 'm6 9 6 6 6-6',
  close: 'm6 6 12 12M6 18 18 6',
  right: 'M4 12h16m-6-6 6 6-6 6',
  left: 'M20 12H4m6-6-6 6 6 6',
  menu: 'M4 6h16M4 12h16M4 18h16',
  plus: 'M12 4v16M4 12h16',
  upload: 'M12 16V3m-5 5 5-5 5 5M4 15v5h16v-5',
  image: 'M4 4h16v16H4ZM4 16l5-5 4 4 3-3 4 4M15 8h.01',
  calendar:
    'M5 4h14a1 1 0 0 1 1 1v15H4V5a1 1 0 0 1 1-1ZM8 2v4m8-4v4M4 9h16m-12 4h2m4 0h2m-8 4h2m4 0h2',
  info: 'M12 11v6m0-10v.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
};

/** 글꼴 문자에 의존하지 않는 장식 아이콘이며 조작 이름은 부모 컨트롤이 제공한다. */
export function Icon({
  name,
  ...props
}: { name: keyof typeof paths } & ComponentPropsWithoutRef<'svg'>) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d={paths[name]} />
    </svg>
  );
}
