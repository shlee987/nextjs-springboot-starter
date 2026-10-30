export const menuGroups = [
  { id: 'input', label: '입력과 선택' },
  { id: 'feedback', label: '피드백과 탐색' },
  { id: 'data', label: '데이터 표현' },
  { id: 'service', label: '실서비스 연결' },
] as const;
export type MenuGroup = (typeof menuGroups)[number]['id'];
export type MenuItem = {
  id: string;
  label: string;
  href: string;
  icon: 'home' | 'catalog' | 'account' | 'data';
  category: MenuGroup;
  description: string;
  tryIt?: string;
  result?: string;
  permission?: string;
  example?: boolean;
  children?: MenuItem[];
};
const exampleDefinitions: [string, string, MenuGroup, string, string, string][] = [
  [
    'files',
    '파일 업로드',
    'service',
    '이미지 선택·파일별 검증과 업로드 진행 상태를 확인합니다.',
    '로그인 후 이미지를 업로드하고 내 파일 목록에서 다운로드·삭제하세요.',
    '파일별 서버 저장 결과와 오류를 구분하고 본인 파일만 관리합니다.',
  ],
  [
    'buttons',
    '버튼',
    'input',
    '주요 동작과 보조 동작을 크기·상태로 구분합니다.',
    '버튼을 누르고 비활성·진행 상태를 비교하세요.',
    '활성 버튼만 클릭 횟수를 늘립니다.',
  ],
  [
    'inputs',
    '텍스트 입력',
    'input',
    '입력·지우기·암호 표시와 오류 상태를 비교합니다.',
    '텍스트를 입력한 뒤 지우기와 표시 전환을 눌러 보세요.',
    '입력값은 아래에 표시되며 읽기 전용 값은 바뀌지 않습니다.',
  ],
  [
    'selections',
    '선택 컨트롤',
    'input',
    '단일·검색·복수 선택의 값을 확인합니다.',
    '단일 선택은 방향키와 Enter, 복수 선택은 Space로 바꿔 보세요.',
    '공유한 선택값과 체크 상태가 함께 바뀝니다.',
  ],
  [
    'numbers',
    '정밀 숫자',
    'data',
    '소수 계산과 반올림 순서의 차이를 확인합니다.',
    '단가와 수량을 바꾸고 정밀 계산을 누르세요.',
    '0.1 + 0.2는 정확히 0.3이며 반올림 순서에 따라 합계가 달라집니다.',
  ],
  [
    'dates',
    '날짜·시간',
    'input',
    '화면의 서울 시각과 서버 전달용 UTC를 비교합니다.',
    '날짜·시각을 수정하고 존재하지 않는 날짜도 입력해 보세요.',
    '유효한 입력만 반영되며 날짜와 UTC 전달값을 구분해 보여줍니다.',
  ],
  [
    'forms',
    '폼 검증',
    'input',
    '필수값·오류 수정·실패 보존·초기화를 시험합니다.',
    '빈 상태 제출 → 오류 수정 → 실패 모드 → 초기화 순서로 확인하세요.',
    '첫 오류로 이동하고 수정하면 해제됩니다. 제출 테스트는 서버에 저장하지 않습니다.',
  ],
  [
    'overlays',
    '팝업·패널',
    'feedback',
    '대화상자와 확인창의 닫기·포커스 동작입니다.',
    '팝업을 열어 Tab과 Escape를 누르고 진행 상태를 전환하세요.',
    '진행 중에는 닫히지 않고 종료하면 열기 버튼으로 포커스가 돌아옵니다.',
  ],
  [
    'feedback',
    '알림·상태',
    'feedback',
    '성공·안내·오류와 빈 결과를 구분합니다.',
    '알림을 연속 생성하거나 닫고 오류에서 다시 시도를 누르세요.',
    '같은 알림은 합쳐지고 수동 닫기 또는 시간 경과로 제거됩니다.',
  ],
  [
    'navigation',
    '탭·메뉴',
    'feedback',
    '탭·보조 설명·접힘 영역을 키보드로 이동합니다.',
    'Tab·방향키·Enter·Escape로 각 컨트롤을 조작하세요.',
    '선택한 탭 내용과 메뉴 동작 결과가 표시됩니다.',
  ],
  [
    'search',
    '검색·페이지',
    'feedback',
    '편집 중 검색어와 적용 조건·페이지를 분리합니다.',
    '검색어 입력 → Enter → 페이지 크기 변경 → 초기화를 시험하세요.',
    '조회할 때만 조건이 반영되고 크기를 바꾸면 첫 페이지로 이동합니다.',
  ],
  [
    'grid',
    '데이터 그리드',
    'data',
    '행 선택·정렬·정밀값과 서버 페이지를 구분합니다.',
    '열 제목으로 정렬하고 행 선택·빈 결과 상태를 비교하세요.',
    '큰 ID와 금액을 문자열 정밀도로 표시하며 선택한 행 ID를 보여줍니다.',
  ],
  [
    'charts',
    '차트',
    'data',
    '막대·선·원형 그래프와 정확한 값 표를 봅니다.',
    '차트 종류와 로딩·빈 데이터·오류 상태를 바꿔 보세요.',
    '차트가 화면 폭에 맞춰 바뀌며 원본 값은 표에서도 읽을 수 있습니다.',
  ],
  [
    'api',
    'API·Query',
    'service',
    '실제 프로필 조회와 캐시 상태를 확인합니다.',
    '로그인 후 조회 → 캐시 무효화 → 재조회를 누르세요.',
    '조회 결과가 남아 있는지, 다시 조회가 필요한지 확인할 수 있습니다. 오류 예시는 실제 응답과 구분합니다.',
  ],
  [
    'auth',
    '인증 연결',
    'service',
    '서버 세션 확인·갱신과 권한 경계를 봅니다.',
    '연결된 서버에서 로그인한 뒤 인증 확인·갱신 경계를 실행하세요.',
    '서버가 확인한 상태를 표시하며 토큰 원문은 읽거나 저장하지 않습니다.',
  ],
];
export const exampleItems: MenuItem[] = exampleDefinitions.map(
  ([id, label, category, description, tryIt, result]) => ({
    id,
    label,
    category,
    description,
    tryIt,
    result,
    href: `/examples/${id}`,
    icon: 'catalog',
    example: true,
  }),
);

/** 경로·제목·분류·설명·권한을 모든 메뉴와 목차가 함께 사용한다. */
export const menuItems: MenuItem[] = [
  {
    id: 'home',
    label: '기능 목차',
    href: '/',
    icon: 'home',
    category: 'service',
    description: '재사용할 공통 기능을 찾아 실행합니다.',
  },
  ...exampleItems,
  {
    id: 'me',
    label: '내 정보',
    href: '/me',
    icon: 'account',
    category: 'service',
    description: '기본 정보·권한과 로그인 상태를 관리합니다.',
  },
  {
    id: 'users',
    label: '사용자',
    href: '/users',
    icon: 'data',
    category: 'service',
    description: '서버 검색·정렬·페이지와 개인 프로필 조회입니다.',
    permission: 'USER_READ',
  },
  {
    id: 'departments',
    label: '부서',
    href: '/departments',
    icon: 'data',
    category: 'service',
    description: '부서 이름·코드·활성 상태를 조회합니다.',
    permission: 'DEPARTMENT_READ',
  },
  {
    id: 'access-audit',
    label: '접근 이력',
    href: '/audits/access',
    icon: 'data',
    category: 'service',
    description: '기간과 리소스 조건으로 접근 기록을 확인합니다.',
    permission: 'AUDIT_READ',
  },
  {
    id: 'security-audit',
    label: '보안 이력',
    href: '/audits/security',
    icon: 'data',
    category: 'service',
    description: '인증·권한 관련 사건과 진단 ID를 조회합니다.',
    permission: 'SECURITY_AUDIT_READ',
  },
];

/** 개인 식별자 대신 등록 경로의 표시 정보만 반환한다. */
export function getMenuItem(pathname: string): MenuItem | undefined {
  return (
    menuItems.find((item) => item.href === pathname) ??
    (pathname.startsWith('/users/') ? menuItems.find((item) => item.id === 'users') : undefined)
  );
}
