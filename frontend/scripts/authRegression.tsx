import axios, { AxiosError, type AxiosResponse } from 'axios';
import { StrictMode, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

type Scenario = {
  name: string;
  heads: number[];
  refreshStatus?: number;
  permissions?: string[];
  expectedStatus: 'authenticated' | 'anonymous' | 'error';
  expectedHeads: number;
  expectedRefresh: number;
  expectedMe: number;
  strict?: boolean;
  siblings?: boolean;
  retry?: boolean;
  transition?: boolean;
  generic401?: boolean;
  expectedProfile?: boolean;
};
type Counts = { head: number; refresh: number; me: number; profile: number };
type ResponseGate = {
  arrived: Promise<void>;
  signalArrival: () => void;
  released: Promise<void>;
  release: () => void;
  status?: number;
  capturedUserId?: string;
  requestAborted?: boolean;
};
type FixtureUser = {
  userId: string;
  loginId: string;
  displayName: string;
  departments: [];
  roles: string[];
  permissions: string[];
};
const traceId = '550e8400-e29b-41d4-a716-446655440000';
const permissions = ['USER_READ', 'PERSONAL_INFO_READ'];
let scenario: { name: string };
let fixture: {
  scenario: Pick<Scenario, 'heads' | 'refreshStatus' | 'permissions'>;
  counts: Counts;
  headIndex: number;
  user?: FixtureUser;
  loginUser?: FixtureUser;
  gates?: Map<string, ResponseGate[]>;
};

/** 타이머 대신 요청 도착과 응답 완료를 각각 명시적으로 제어한다. */
function createResponseGate(status?: number): ResponseGate {
  const arrival = Promise.withResolvers<void>();
  const completion = Promise.withResolvers<void>();

  return {
    arrived: arrival.promise,
    signalArrival: arrival.resolve,
    released: completion.promise,
    release: completion.resolve,
    status,
  };
}

/** 사용자 전환을 구분할 공개 가능한 고정 사용자만 구성한다. */
function createFixtureUser(userId: string): FixtureUser {
  return {
    userId,
    loginId: `synthetic-${userId}`,
    displayName: `회귀 사용자 ${userId}`,
    departments: [],
    roles: [],
    permissions,
  };
}

// 실제 mutator가 axios.create를 호출하기 전에 응답 대역만 설치한다. 네트워크 adapter는 쓰지 않는다.
axios.defaults.adapter = async (config) => {
  const current = fixture;
  const path = config.url;
  const method = config.method?.toUpperCase() ?? 'GET';
  let status = 200;
  let data: unknown = { data: {}, traceId };
  let responseUserId: string | undefined;

  if (path === '/api/auth/csrf') {
    data = { data: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' }, traceId };
  } else if (path === '/api/auth/me' && config.method?.toUpperCase() === 'HEAD') {
    current.counts.head++;
    status = current.scenario.heads[current.headIndex++] ?? 200;
    data = undefined;
  } else if (path === '/api/auth/refresh') {
    current.counts.refresh++;
    status = current.scenario.refreshStatus ?? 200;
    if (status === 401)
      data = {
        type: 'about:blank',
        title: 'synthetic',
        status,
        code: 'TOKEN_REFRESH_REUSED',
        traceId,
      };
  } else if (path === '/api/auth/me') {
    current.counts.me++;
    const user = current.user ?? {
      userId: '1',
      loginId: 'synthetic',
      displayName: '회귀 사용자',
      departments: [],
      roles: [],
      permissions: current.scenario.permissions ?? permissions,
    };
    responseUserId = user.userId;
    data = {
      data: user,
      traceId,
    };
  } else if (path === '/api/users/1') {
    current.counts.profile++;
    data = {
      data: {
        userId: '1',
        displayName: '회귀 프로필',
        status: 'ACTIVE',
        departments: [],
        loginId: 'synthetic',
        email: null,
        createdAt: '2026-10-29 00:00:00.000',
        updatedAt: '2026-10-29 00:00:00.000',
      },
      traceId,
    };
  } else if (path === '/api/auth/logout') {
    status = 204;
    data = undefined;
  } else if (path === '/api/auth/login') {
    current.user = current.loginUser ?? createFixtureUser('2');
    data = { data: { user: current.user, accessExpiresAt: '2026-10-29 01:00:00.000' }, traceId };
  } else if (path === '/api/users') {
    status = 401;
    data = undefined;
  } else {
    throw new Error('검사에 등록하지 않은 요청입니다.');
  }

  const gate = current.gates?.get(`${method} ${path}`)?.shift();

  if (gate) {
    gate.capturedUserId = responseUserId;
    gate.requestAborted = config.signal?.aborted ?? false;
    gate.signalArrival();
    await gate.released;
    gate.requestAborted = config.signal?.aborted ?? false;

    if (gate.status !== undefined) {
      status = gate.status;
      if (status >= 400)
        data = { type: 'about:blank', title: 'synthetic', status, code: 'TOKEN_REVOKED', traceId };
    }
  } else {
    await new Promise((resolve) => setTimeout(resolve, 10));
  }

  const response: AxiosResponse = {
    data,
    status,
    statusText: '',
    headers: {
      'content-type': status >= 400 ? 'application/problem+json' : 'application/json',
    },
    config,
  };

  if (status >= 400) {
    throw new AxiosError('synthetic response', 'ERR_BAD_RESPONSE', config, undefined, response);
  }

  return response;
};

const { AuthProvider, useSession } = await import('../src/features/auth/AuthProvider');
const { UserDetailScreen } = await import('../src/features/data/ProfilePanel');
const { advanceAuthenticationGeneration, getAuthenticationGeneration, apiMutator } =
  await import('../src/api/client');
const { ApiError } = await import('../src/api/errors');
let session: ReturnType<typeof useSession> | undefined;

/** 실제 Provider의 상태를 구독하며 Guard 내부를 대신 구현하지 않는다. */
function Probe() {
  const current = useSession();
  useEffect(() => {
    session = current;
  });

  return (
    <output data-session-status={current.status} data-session-user={current.user?.userId}>
      {current.status}
    </output>
  );
}

/** effect에서 관찰한 실제 세션이 없으면 검사를 실패시킨다. */
function readSession() {
  if (!session) throw new Error('Provider 세션을 아직 관찰하지 못했습니다.');

  return session;
}

/** React effect와 응답 대역을 기다리되 실패 시 3초 안에 검사 이름과 함께 종료한다. */
async function waitFor(predicate: () => boolean) {
  const deadline = Date.now() + 3000;

  while (!predicate()) {
    if (Date.now() > deadline) throw new Error(`${scenario.name}: 상태 대기 초과`);
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
}

/** 검증 실패를 결과 영역으로 전달하고 원문 자격 정보는 사용하지 않는다. */
function check(condition: boolean, message: string) {
  if (!condition) throw new Error(`${scenario.name}: ${message}`);
}

/** 두 실제 권한 Guard가 있는 사용자 상세를 유지하며 요청 수·명시 재시도·세대 전환을 검사한다. */
async function runScenario(value: Scenario, host: HTMLElement) {
  scenario = value;
  const counts: Counts = { head: 0, refresh: 0, me: 0, profile: 0 };
  fixture = { scenario: value, counts, headIndex: 0 };
  session = undefined;
  advanceAuthenticationGeneration();
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const root = createRoot(host);
  const tree = (
    <QueryClientProvider client={client}>
      <AuthProvider>
        <Probe />
        <UserDetailScreen userId="1" />
        {value.siblings && <UserDetailScreen userId="1" />}
      </AuthProvider>
    </QueryClientProvider>
  );
  root.render(value.strict ? <StrictMode>{tree}</StrictMode> : tree);

  try {
    await waitFor(() => session?.status === value.expectedStatus);
    await new Promise((resolve) => setTimeout(resolve, 200));
    check(counts.head === value.expectedHeads, `HEAD ${counts.head}, 기대 ${value.expectedHeads}`);
    check(
      counts.refresh === value.expectedRefresh,
      `refresh ${counts.refresh}, 기대 ${value.expectedRefresh}`,
    );
    check(counts.me === value.expectedMe, `me ${counts.me}, 기대 ${value.expectedMe}`);
    const canRead = value.expectedProfile ?? true;

    if (value.expectedStatus === 'authenticated') {
      check(
        canRead
          ? counts.profile >= 1 && counts.profile <= (value.strict ? 2 : 1)
          : counts.profile === 0,
        '상세 권한 또는 profile 호출 수',
      );
      check(
        canRead
          ? host.textContent?.includes('회귀 프로필') === true
          : host.textContent?.includes('조회할 권한이 없습니다') === true,
        '상세 표시/권한 거부',
      );
    } else {
      check(counts.profile === 0, '인증 확인 실패 시 profile을 요청함');
    }

    const previous = { ...counts };
    if (value.expectedStatus !== 'anonymous') {
      await readSession().bootstrap();
      await readSession().bootstrap();
      check(
        counts.head === previous.head && counts.me === previous.me,
        '확인된 세대의 bootstrap 재호출',
      );
    }

    if (value.retry) {
      await readSession().bootstrap(true);
      await waitFor(() => session?.status === 'authenticated');
      await waitFor(() => counts.profile === 1);
      check(counts.head === previous.head + 1 && counts.me === previous.me + 1, '명시 재시도');
    }

    if (value.transition) {
      await readSession().logout();
      await waitFor(() => session?.status === 'anonymous');
      const afterLogout = { ...counts };
      await readSession().bootstrap();
      await waitFor(() => session?.status === 'authenticated');
      check(
        counts.head === afterLogout.head + 1 && counts.me === afterLogout.me + 1,
        '세대 전환 후 재검증',
      );
      await new Promise((resolve) => setTimeout(resolve, 200));
      check(
        counts.head === afterLogout.head + 1 && counts.me === afterLogout.me + 1,
        '세대 전환 후 중첩 가드 재조회 반복',
      );
    }

    if (value.generic401) {
      try {
        await apiMutator({ url: '/api/users', method: 'GET' });
        throw new Error('일반 401이 거부되지 않았습니다.');
      } catch (error) {
        check(error instanceof ApiError && error.status === 401, '일반 본문 없는 401 오류');
      }
      check(
        counts.refresh === previous.refresh && counts.head === previous.head,
        '일반 원인불명 401에 복구가 확대됨',
      );
      await waitFor(() => session?.status === 'anonymous');
    }

    return { name: value.name, result: 'PASS', counts: { ...counts } };
  } finally {
    root.unmount();
    client.clear();
  }
}

/** 보류 요청이 실제 도착하거나 완료된 사건을 기다리며 무한 대기를 실패 처리한다. */
async function withinDeadline<T>(promise: Promise<T>): Promise<T> {
  let timer: number | undefined;

  try {
    return await Promise.race([
      promise,
      new Promise<never>((_, reject) => {
        timer = window.setTimeout(
          () => reject(new Error(`${scenario.name}: 응답 제어 대기 초과`)),
          3000,
        );
      }),
    ]);
  } finally {
    window.clearTimeout(timer);
  }
}

/** 완료된 Promise의 React 갱신이 화면에 반영될 두 프레임을 기다린다. */
async function flushRender() {
  await new Promise<void>((resolve) =>
    requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
  );
}

/** 현재 사용자와 모든 세션 Query가 새 세대에만 속하는지 실제 캐시에서 검사한다. */
function checkNewUser(client: QueryClient, userId: string) {
  const current = readSession();
  const generation = getAuthenticationGeneration();
  const cached = client.getQueryData<{ data: { userId: string } }>(['session', generation, 'me']);
  check(
    current.status === 'authenticated' && current.generation === generation,
    '새 세대의 인증 상태가 변경됨',
  );
  check(
    current.user?.userId === userId && cached?.data.userId === userId,
    '새 사용자 또는 새 캐시가 오염됨',
  );
  check(
    client
      .getQueryCache()
      .findAll({ queryKey: ['session'] })
      .every((query) => query.queryKey[1] === generation),
    '이전 세대의 세션 Query가 재생성됨',
  );
}

type RaceMode =
  'logout-success' | 'login-success' | 'preserve-flight' | 'login-bootstrap401' | 'login-api401';
const raceNames: Record<RaceMode, string> = {
  'logout-success': 'FV09 이전 bootstrap 성공을 logout 뒤 완료',
  'login-success': 'FV09 이전 me 성공을 다른 사용자 로그인 뒤 완료',
  'preserve-flight': 'FV09 이전 finally 완료 중 새 bootstrap single-flight 유지',
  'login-bootstrap401': 'FV09 이전 bootstrap 401을 다른 사용자 로그인 뒤 완료',
  'login-api401': 'FV09 이전 일반 API 401을 다른 사용자 로그인 뒤 완료',
};

/** 응답을 보류한 채 실제 logout/login·새 bootstrap을 실행해 세대 경계의 완료 순서를 재현한다. */
async function runRace(mode: RaceMode, host: HTMLElement) {
  scenario = { name: raceNames[mode] };
  const oldGate = createResponseGate(mode.endsWith('401') ? 401 : undefined);
  const newGate = createResponseGate();
  const gates = new Map<string, ResponseGate[]>();
  if (mode !== 'login-api401')
    gates.set('GET /api/auth/me', mode === 'preserve-flight' ? [oldGate, newGate] : [oldGate]);
  const counts: Counts = { head: 0, refresh: 0, me: 0, profile: 0 };
  const current = {
    scenario: { heads: [200] },
    counts,
    headIndex: 0,
    user: createFixtureUser('1'),
    loginUser: createFixtureUser('2'),
    gates,
  };
  fixture = current;
  session = undefined;
  const oldGeneration = advanceAuthenticationGeneration();
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const root = createRoot(host);
  const pending: Promise<unknown>[] = [];
  root.render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <Probe />
        <UserDetailScreen userId="1" />
      </AuthProvider>
    </QueryClientProvider>,
  );

  try {
    let oldCompletion: Promise<unknown>;

    if (mode === 'login-api401') {
      await waitFor(() => session?.status === 'authenticated' && counts.profile === 1);
      gates.set('GET /api/users', [oldGate]);
      oldCompletion = apiMutator({ url: '/api/users', method: 'GET' }).then(
        () => undefined,
        (error: unknown) => error,
      );
    } else {
      await withinDeadline(oldGate.arrived);
      oldCompletion = readSession().bootstrap();
      check(oldGate.capturedUserId === '1', '보류한 me 응답이 이전 사용자를 담지 않음');
    }
    pending.push(oldCompletion);
    await withinDeadline(oldGate.arrived);

    if (mode === 'logout-success' || mode === 'preserve-flight') {
      await readSession().logout();
      await waitFor(() => session?.status === 'anonymous');
    } else {
      await readSession().login('synthetic-2', 'synthetic-only-password');
      await waitFor(() => session?.status === 'authenticated' && session.user?.userId === '2');
      checkNewUser(client, '2');
    }

    const newGeneration = getAuthenticationGeneration();
    check(newGeneration > oldGeneration, '인증 세대가 전환되지 않음');

    if (mode === 'preserve-flight') {
      current.user = createFixtureUser('2');
      const newFlight = readSession().bootstrap();
      pending.push(newFlight);
      await withinDeadline(newGate.arrived);
      check(newGate.capturedUserId === '2', '새 bootstrap이 새 사용자를 요청하지 않음');
      const beforeOldFinally = { ...counts };
      oldGate.release();
      await withinDeadline(oldCompletion);
      await flushRender();
      const joinedFlight = readSession().bootstrap();
      pending.push(joinedFlight);
      check(joinedFlight === newFlight, '이전 finally가 새 세대 bootFlight를 지움');
      check(
        counts.head === beforeOldFinally.head && counts.me === beforeOldFinally.me,
        '새 세대 bootstrap이 중복 시작됨',
      );
      newGate.release();
      await withinDeadline(newFlight);
      await waitFor(() => session?.status === 'authenticated' && session.user?.userId === '2');
      checkNewUser(client, '2');
    } else {
      oldGate.release();
      const oldResult = await withinDeadline(oldCompletion);
      await flushRender();

      if (mode === 'login-api401')
        check(
          oldResult instanceof ApiError && oldResult.code === 'REQUEST_CANCELLED',
          '이전 일반 401이 취소 오류로 격리되지 않음',
        );
      if (mode === 'logout-success') {
        check(
          readSession().status === 'anonymous' && !readSession().user,
          '이전 성공 응답이 logout 뒤 사용자를 복원함',
        );
        check(
          client
            .getQueryCache()
            .findAll({ queryKey: ['session'] })
            .every((query) => query.state.data === undefined),
          'logout 뒤 이전 세션 캐시가 재주입됨',
        );
      } else {
        checkNewUser(client, '2');
      }
    }

    check(
      oldGate.requestAborted === false,
      '응답이 취소되어 늦은 완료 경계를 실제로 검사하지 못함',
    );
    check(getAuthenticationGeneration() === newGeneration, '이전 응답이 새 인증 세대를 폐기함');
    return {
      name: scenario.name,
      result: 'PASS',
      counts: { ...counts },
      oldGeneration,
      newGeneration,
      oldResponseUser: oldGate.capturedUserId,
      finalUser: readSession().user?.userId ?? null,
      oldRequestAborted: oldGate.requestAborted,
    };
  } finally {
    oldGate.release();
    newGate.release();
    await withinDeadline(Promise.allSettled(pending));
    root.unmount();
    client.clear();
  }
}

const cases: Scenario[] = [
  {
    name: 'FFR1-001 초기/잠금 후 본문 없는 HEAD401',
    heads: [401, 401],
    expectedStatus: 'authenticated',
    expectedHeads: 2,
    expectedRefresh: 1,
    expectedMe: 1,
  },
  {
    name: 'FFR1-001 HEAD200 갱신 생략',
    heads: [200],
    expectedStatus: 'authenticated',
    expectedHeads: 1,
    expectedRefresh: 0,
    expectedMe: 1,
  },
  {
    name: 'FFR1-001 잠금 후 HEAD200 갱신 생략',
    heads: [401, 200],
    expectedStatus: 'authenticated',
    expectedHeads: 2,
    expectedRefresh: 0,
    expectedMe: 1,
  },
  {
    name: 'FFR1-001 refresh401 반복 없음',
    heads: [401, 401],
    refreshStatus: 401,
    expectedStatus: 'anonymous',
    expectedHeads: 2,
    expectedRefresh: 1,
    expectedMe: 0,
  },
  {
    name: 'FFR1-001 HEAD503 갱신 금지/명시 retry',
    heads: [503, 200],
    expectedStatus: 'error',
    expectedHeads: 1,
    expectedRefresh: 0,
    expectedMe: 0,
    retry: true,
  },
  {
    name: 'FFR1-001 HEAD403 갱신 금지',
    heads: [403],
    expectedStatus: 'error',
    expectedHeads: 1,
    expectedRefresh: 0,
    expectedMe: 0,
  },
  {
    name: 'FFR1-001 잠금 후 HEAD503 갱신 금지',
    heads: [401, 503],
    expectedStatus: 'error',
    expectedHeads: 2,
    expectedRefresh: 0,
    expectedMe: 0,
  },
  {
    name: 'FFR1-001 일반 원인불명 401 복구 금지',
    heads: [200],
    expectedStatus: 'authenticated',
    expectedHeads: 1,
    expectedRefresh: 0,
    expectedMe: 1,
    generic401: true,
  },
  {
    name: 'FFR1-002 두 권한 상세/StrictMode 안정',
    heads: [200],
    expectedStatus: 'authenticated',
    expectedHeads: 1,
    expectedRefresh: 0,
    expectedMe: 1,
    strict: true,
    siblings: true,
  },
  {
    name: 'FFR1-002 USER_READ 누락',
    heads: [200],
    permissions: ['PERSONAL_INFO_READ'],
    expectedProfile: false,
    expectedStatus: 'authenticated',
    expectedHeads: 1,
    expectedRefresh: 0,
    expectedMe: 1,
  },
  {
    name: 'FFR1-002 PERSONAL_INFO_READ 누락',
    heads: [200],
    permissions: ['USER_READ'],
    expectedProfile: false,
    expectedStatus: 'authenticated',
    expectedHeads: 1,
    expectedRefresh: 0,
    expectedMe: 1,
  },
  {
    name: 'FFR1-002 로그아웃/새 세대 재검증',
    heads: [200],
    expectedStatus: 'authenticated',
    expectedHeads: 1,
    expectedRefresh: 0,
    expectedMe: 1,
    transition: true,
  },
];
const button = document.querySelector('button');
const output = document.querySelector('pre');
const host = document.getElementById('fixture');

if (!button || !output || !host) throw new Error('검사 문서가 올바르지 않습니다.');

button.addEventListener('click', async () => {
  button.disabled = true;
  const results: unknown[] = [];

  try {
    for (const value of cases) results.push(await runScenario(value, host));
    for (const mode of [
      'logout-success',
      'login-success',
      'preserve-flight',
      'login-bootstrap401',
      'login-api401',
    ] as const)
      results.push(await runRace(mode, host));
    output.textContent = JSON.stringify({ result: 'PASS', cases: results }, null, 2);
  } catch (error) {
    output.textContent = JSON.stringify(
      { result: 'FAIL', cases: results, message: error instanceof Error ? error.message : '실패' },
      null,
      2,
    );
  }
  output.dataset.complete = 'true';
});
