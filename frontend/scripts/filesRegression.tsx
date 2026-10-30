import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type InternalAxiosRequestConfig,
} from 'axios';
import { StrictMode, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { DatePicker, DateRangeInput } from '@/components/ui/DatePickers';
import { fileInfoSchema } from '@/features/files/fileContracts';
import { readDownloadName } from '@/api/fileDownload';
import { ApiError } from '@/api/errors';
import { pageSchema, readEnvelope } from '@/features/data/contracts';
import '@/app/globals.css';

const traceId = '550e8400-e29b-41d4-a716-446655440000';
const fileId = '550e8400-e29b-41d4-a716-446655440001';
let adapter: AxiosAdapter;
let useFixtureSession: typeof import('@/features/auth/AuthProvider').useSession;
axios.defaults.adapter = (config) => adapter(config);

/** 실제 네트워크 adapter 대신 합성 응답만 제공하며 실패도 Axios의 공통 경계를 통과한다. */
async function reply(
  config: InternalAxiosRequestConfig,
  status: number,
  data: unknown,
  headers: Record<string, string> = {},
) {
  const response = {
    config,
    status,
    statusText: 'synthetic',
    data,
    headers: new AxiosHeaders(headers),
  };

  if (status >= 400)
    throw new AxiosError(
      'PRIVATE_TRANSPORT_MESSAGE',
      'ERR_BAD_RESPONSE',
      config,
      undefined,
      response,
    );

  return response;
}

/** 공개 계약의 기대 결과만 비교하고 요청·응답 원문을 실패 메시지에 포함하지 않는다. */
function check(condition: unknown, label: string): asserts condition {
  if (!condition) throw new Error(label);
}

/** Blob 오류·복구·multipart·취소·세대 분리의 실제 생성 함수와 공통 전송 경계를 검증한다. */
async function runTransport() {
  const api = await import('@/api/client');
  const generated = await import('@/api/generated/client');
  const rows: string[] = [];
  const problem = (code: string, status: number) =>
    new Blob(
      [
        JSON.stringify({
          type: 'about:blank',
          title: 'PRIVATE_PROBLEM_TITLE',
          status,
          code,
          traceId,
        }),
      ],
      { type: 'application/problem+json' },
    );
  const binaryHeaders = {
    'content-type': 'image/png',
    'content-disposition': "attachment; filename*=UTF-8''%ED%9A%8C%EA%B7%80.png",
    'x-content-type-options': 'nosniff',
    'cache-control': 'no-store',
  };
  const metadata = {
    fileId,
    originalName: '회귀.png',
    contentType: 'image/png',
    size: 10,
    width: 2,
    height: 2,
    createdAt: '2026-10-30 00:00:00.000',
  };
  let calls = 0;
  let recovery = 0;
  const releaseRecovery = api.registerAuthenticationRecovery(async () => {
    recovery++;
  });
  api.setCsrfToken({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
  let progress = 0;
  adapter = async (config) => {
    calls++;
    check(config.data instanceof FormData, 'multipart FormData');
    check(Array.from(config.data.keys()).join(',') === 'file', 'single file part');
    check(config.headers.get('X-CSRF-TOKEN') === 'synthetic-csrf', 'CSRF header');
    config.onUploadProgress?.({
      loaded: 4,
      total: 8,
      bytes: 4,
      lengthComputable: true,
      upload: true,
    });

    return reply(
      config,
      201,
      { data: metadata, traceId },
      { 'content-type': 'application/json', location: `/api/files/${fileId}/content` },
    );
  };
  const saved = await generated.uploadFile(
    { file: new File(['synthetic'], '회귀.png', { type: 'image/png' }) },
    {
      onUploadProgress: (event) => {
        progress = event.loaded;
      },
    },
  );
  check(
    fileInfoSchema.safeParse(saved.data).success && calls === 1 && progress === 4,
    'upload metadata/progress',
  );
  rows.push('201 multipart one file / CSRF / progress / metadata');
  calls = 0;
  adapter = (config) => {
    calls++;
    return reply(config, 401, problem('TOKEN_EXPIRED', 401), {
      'content-type': 'application/problem+json',
    });
  };

  try {
    await generated.uploadFile({ file: new Blob(['x']) });
    throw new Error('expected upload rejection');
  } catch (error) {
    check(error instanceof ApiError && error.code === 'TOKEN_EXPIRED', 'mutation safe expiry');
  }
  check(calls === 1 && recovery === 0, 'mutation must not replay');
  rows.push('expired mutation no replay');
  calls = 0;
  adapter = (config) => {
    calls++;
    return calls === 1
      ? reply(config, 401, problem('TOKEN_EXPIRED', 401), {
          'content-type': 'application/problem+json',
        })
      : reply(config, 200, new Blob(['image'], { type: 'image/png' }), binaryHeaders);
  };
  let name = '';
  const blob = await generated.downloadFile(fileId, {
    onDownloadFileName: (next) => {
      name = next;
    },
  });
  check(
    blob instanceof Blob && name === '회귀.png' && calls === 2 && Number(recovery) === 1,
    'binary one recovery',
  );
  rows.push('Blob TOKEN_EXPIRED -> one recovery -> PNG attachment');
  releaseRecovery();
  adapter = (config) =>
    reply(config, 403, new Blob(['<html>PRIVATE_INTERNAL</html>']), {
      'content-type': 'text/html',
    });

  try {
    await generated.downloadFile(fileId);
    throw new Error('expected HTML rejection');
  } catch (error) {
    check(error instanceof Error && !error.message.includes('PRIVATE'), 'safe HTML Blob error');
  }
  rows.push('HTML Blob error sanitized');
  for (const [data, headers] of [
    [new Blob(['{}']), { 'content-type': 'application/json' }],
    [new Blob(['x']), { ...binaryHeaders, 'content-disposition': 'inline' }],
    [new Blob([new Uint8Array(5242881)]), binaryHeaders],
  ] as const) {
    adapter = (config) => reply(config, 200, data, { ...headers });

    try {
      await generated.downloadFile(fileId);
      throw new Error('invalid download accepted');
    } catch (error) {
      check(
        error instanceof Error && error.message !== 'invalid download accepted',
        'binary contract rejection',
      );
    }
  }
  rows.push('JSON/inline/oversize download rejected');
  check(
    !readDownloadName("attachment; filename*=UTF-8''..%2F..%2Funsafe.png", 'image/png').includes(
      '/',
    ),
    'filename path confinement',
  );
  check(
    !fileInfoSchema.safeParse({ ...metadata, width: 8192, height: 8192 }).success,
    'pixel metadata validation',
  );
  rows.push('attachment name / pixel metadata');
  const gate = Promise.withResolvers<unknown>();
  adapter = async (config) => {
    await gate.promise;
    return reply(config, 200, new Blob(['image']), binaryHeaders);
  };
  const controller = new AbortController();
  const aborted = generated.downloadFile(fileId, { signal: controller.signal });
  controller.abort();
  gate.resolve(undefined);

  try {
    await aborted;
    throw new Error('abort accepted');
  } catch (error) {
    check(
      error instanceof Error && error.message !== 'abort accepted',
      'cancelled request rejected',
    );
  }
  rows.push('actual cancellation settlement');
  const late = Promise.withResolvers<unknown>();
  adapter = async (config) => {
    await late.promise;
    return reply(config, 200, new Blob(['image']), binaryHeaders);
  };
  const old = generated.downloadFile(fileId, {
    onDownloadFileName: () => {
      throw new Error('stale metadata callback');
    },
  });
  await Promise.resolve();
  api.advanceAuthenticationGeneration();
  late.resolve(undefined);

  try {
    await old;
    throw new Error('old generation accepted');
  } catch (error) {
    check(
      error instanceof Error &&
        error.message !== 'old generation accepted' &&
        error.message !== 'stale metadata callback',
      'generation blocked',
    );
  }
  rows.push('old user response and filename callback blocked');
  api.setCsrfToken({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
  adapter = (config) => reply(config, 204, undefined);
  check((await generated.deleteFile(fileId)) === undefined, 'delete204');
  rows.push('delete204');

  const fileErrors = [
    ['FILE_NAME_INVALID', 400],
    ['FILE_TYPE_UNSUPPORTED', 415],
    ['FILE_CONTENT_INVALID', 400],
    ['FILE_QUOTA_EXCEEDED', 409],
    ['FILE_PROCESSING_BUSY', 429],
    ['FILE_STORAGE_UNAVAILABLE', 503],
    ['FILE_METADATA_UNAVAILABLE', 503],
  ] as const;

  for (const [code, status] of fileErrors) {
    for (const binary of [false, true]) {
      calls = 0;
      adapter = (config) => {
        calls++;
        const data = {
          type: 'about:blank',
          title: 'PRIVATE_TITLE',
          detail: 'PRIVATE_DETAIL',
          code,
          status,
          traceId,
        };

        return reply(config, status, binary ? new Blob([JSON.stringify(data)]) : data, {
          'content-type': 'application/problem+json',
          'retry-after': '7',
        });
      };

      try {
        await generated.uploadFile({ file: new Blob(['synthetic']) });
        throw new Error('file error accepted');
      } catch (error) {
        check(
          error instanceof ApiError &&
            error.code === code &&
            error.traceId === traceId &&
            error.retryAfterSeconds === 7 &&
            !error.message.includes('PRIVATE') &&
            calls === 1,
          'file error metadata/no replay',
        );
      }
    }
  }
  rows.push('7 file codes JSON/Blob: code + traceId + Retry-After + no replay');

  for (const name of [
    '가'.repeat(116) + '.png',
    '😀'.repeat(116) + '.png',
    '😀'.repeat(61) + '.png',
  ]) {
    check(
      fileInfoSchema.safeParse({ ...metadata, originalName: name }).success,
      'Unicode120 valid response',
    );
    const page = readEnvelope(pageSchema(fileInfoSchema), {
      data: {
        items: [{ ...metadata, originalName: name }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      traceId,
    });
    check(page.data.items.length === 1, 'Unicode120 valid list');
  }

  for (const name of ['가'.repeat(117) + '.png', '😀'.repeat(117) + '.png']) {
    check(
      !fileInfoSchema.safeParse({ ...metadata, originalName: name }).success,
      'Unicode121 rejected',
    );
  }
  rows.push('BMP/non-BMP120 accepted /121 rejected /65 emoji example response and list');

  return rows;
}

/** 익명401 뒤 인증 경계를 재마운트해 bootstrap을 반복하지 않는지 실제 파일 예제로 확인한다. */
async function runAnonymousFiles(host: HTMLElement) {
  const { QueryClient, QueryClientProvider } = await import('@tanstack/react-query');
  const { AuthProvider } = await import('@/features/auth/AuthProvider');
  const { ScreenActionScope } = await import('@/providers/ScreenActionScope');
  const { FilesExample } = await import('@/features/examples/FilesExample');
  const counts = { csrf: 0, head: 0, refresh: 0, files: 0 };
  adapter = (config) => {
    if (config.url === '/api/auth/csrf') {
      counts.csrf++;

      return reply(
        config,
        200,
        { data: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' }, traceId },
        { 'content-type': 'application/json' },
      );
    }

    if (config.url === '/api/auth/me') counts.head++;
    else if (config.url === '/api/auth/refresh') counts.refresh++;
    else counts.files++;

    return reply(
      config,
      401,
      { type: 'about:blank', title: 'synthetic', status: 401, code: 'UNAUTHENTICATED', traceId },
      { 'content-type': 'application/problem+json' },
    );
  };
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const root = createRoot(host);
  root.render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <ScreenActionScope>
            <FilesExample />
          </ScreenActionScope>
        </AuthProvider>
      </QueryClientProvider>
    </StrictMode>,
  );

  try {
    for (let index = 0; index < 100 && !host.textContent?.includes('로그인이 필요합니다.'); index++)
      await new Promise((resolve) => setTimeout(resolve, 10));
    check(host.textContent?.includes('로그인이 필요합니다.'), 'anonymous login prompt');
    await new Promise((resolve) => setTimeout(resolve, 150));
    check(
      counts.head === 2 && counts.refresh === 1 && counts.csrf === 2 && counts.files === 0,
      'anonymous bootstrap bounded',
    );

    return counts;
  } finally {
    root.unmount();
    queryClient.clear();
  }
}

/** 합성 인증 조작도 실제 Provider를 거치고 완료 시점을 테스트 DOM에 표시한다. */
function FixtureSessionActions() {
  const session = useFixtureSession();
  const [state, setState] = useState('idle');

  return (
    <>
      <button
        data-fixture-action="logout"
        onClick={() => void session.logout().then(() => setState('logged-out'))}
      >
        회귀 현재 로그아웃
      </button>
      <button
        data-fixture-action="login"
        onClick={() =>
          void session
            .login('synthetic-second', 'synthetic-password')
            .then(() => setState('logged-in-second'))
        }
      >
        회귀 두 번째 로그인
      </button>
      <output data-fixture-session>{state}</output>
    </>
  );
}

/** 로그아웃·재로그인 중 늦은 업로드/다운로드가 새 사용자·페이지·저장에 영향을 주지 않게 한다. */
async function runFilesTransition(host: HTMLElement) {
  const { QueryClient, QueryClientProvider } = await import('@tanstack/react-query');
  const auth = await import('@/features/auth/AuthProvider');
  useFixtureSession = auth.useSession;
  const { ScreenActionScope } = await import('@/providers/ScreenActionScope');
  const { FilesExample } = await import('@/features/examples/FilesExample');
  const counts = { head: 0, files: 0, upload: 0, download: 0 };
  const uploadGate = Promise.withResolvers<void>();
  const downloadGate = Promise.withResolvers<void>();
  let actor: '1' | '2' | undefined = '1';
  let uploadSignal: InternalAxiosRequestConfig['signal'];
  let downloadSignal: InternalAxiosRequestConfig['signal'];
  let lastPage = -1;
  let lastSize = -1;
  const metadata = (index: number) => ({
    fileId: `550e8400-e29b-41d4-a716-${String(index + 1).padStart(12, '0')}`,
    originalName: `old-file-${index}.png`,
    contentType: 'image/png',
    size: 10,
    width: 2,
    height: 2,
    createdAt: '2026-10-30 00:00:00.000',
  });
  adapter = async (config) => {
    if (config.url === '/api/auth/csrf')
      return reply(
        config,
        200,
        { data: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' }, traceId },
        { 'content-type': 'application/json' },
      );
    if (config.url === '/api/auth/logout') {
      actor = undefined;
      return reply(config, 204, undefined);
    }
    if (config.url === '/api/auth/login') {
      actor = '2';
      return reply(config, 200, { data: {}, traceId }, { 'content-type': 'application/json' });
    }
    if (config.url === '/api/auth/me') {
      if (config.method?.toUpperCase() === 'HEAD') {
        counts.head++;
        return reply(config, 200, undefined);
      }

      return reply(
        config,
        200,
        {
          data: {
            userId: actor,
            loginId: `synthetic-${actor}`,
            displayName: '합성 회귀',
            departments: [],
            roles: [],
            permissions: [],
          },
          traceId,
        },
        { 'content-type': 'application/json' },
      );
    }
    if (config.url === '/api/files' && config.method?.toUpperCase() === 'GET') {
      counts.files++;
      lastPage = Number(config.params?.page ?? 0);
      lastSize = Number(config.params?.size ?? 20);
      const items =
        actor === '2'
          ? [{ ...metadata(999), originalName: 'second-user.png' }]
          : Array.from({ length: 21 }, (_, i) => metadata(i)).slice(
              lastPage * lastSize,
              (lastPage + 1) * lastSize,
            );

      return reply(
        config,
        200,
        {
          data: {
            items,
            page: lastPage,
            size: lastSize,
            totalElements: actor === '2' ? 1 : 21,
            totalPages: actor === '2' ? 1 : 2,
          },
          traceId,
        },
        { 'content-type': 'application/json' },
      );
    }
    if (config.url === '/api/files') {
      counts.upload++;
      uploadSignal = config.signal;
      await uploadGate.promise;

      return reply(
        config,
        201,
        { data: metadata(500), traceId },
        { 'content-type': 'application/json' },
      );
    }
    counts.download++;
    downloadSignal = config.signal;
    await downloadGate.promise;

    return reply(config, 200, new Blob(['image']), {
      'content-type': 'image/png',
      'content-disposition': 'attachment; filename="old.png"',
      'x-content-type-options': 'nosniff',
      'cache-control': 'no-store',
    });
  };
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const root = createRoot(host);
  const create = URL.createObjectURL;
  const revoke = URL.revokeObjectURL;
  let created = 0;
  const revoked = new Set<string>();
  URL.createObjectURL = (blob) => {
    created++;
    return create(blob);
  };
  URL.revokeObjectURL = (url) => {
    revoked.add(url);
    revoke(url);
  };
  /** React 업데이트와 응답 게이트만 기다리며 실패 시 테스트 이름만 남긴다. */
  async function waitFor(condition: () => boolean, label: string) {
    for (let i = 0; i < 150 && !condition(); i++)
      await new Promise((resolve) => setTimeout(resolve, 10));
    check(condition(), label);
  }
  root.render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <auth.AuthProvider>
          <ScreenActionScope>
            <FixtureSessionActions />
            <FilesExample />
          </ScreenActionScope>
        </auth.AuthProvider>
      </QueryClientProvider>
    </StrictMode>,
  );

  try {
    await waitFor(
      () => Boolean(host.querySelector('[aria-label="old-file-0.png 다운로드"]')),
      'first user files',
    );
    Array.from(host.querySelectorAll('button'))
      .find((button) => button.textContent === '다음')
      ?.click();
    await waitFor(
      () => Boolean(host.querySelector('[aria-label="old-file-20.png 다운로드"]')),
      'page one',
    );
    const transfer = new DataTransfer();
    transfer.items.add(
      new File(
        [
          Uint8Array.from(
            atob(
              'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
            ),
            (c) => c.charCodeAt(0),
          ),
        ],
        'old-selection.png',
        { type: 'image/png' },
      ),
    );
    host
      .querySelector('section[aria-label="업로드할 이미지"] > div')
      ?.dispatchEvent(new DragEvent('drop', { bubbles: true, dataTransfer: transfer }));
    await waitFor(() => Boolean(host.querySelector('img')?.src), 'old preview');
    const oldUrl = host.querySelector('img')!.src;
    const beforeCreated = created;
    Array.from(host.querySelectorAll('button'))
      .find((button) => button.textContent === '업로드')
      ?.click();
    (host.querySelector('[aria-label="old-file-20.png 다운로드"]') as HTMLButtonElement)?.click();
    await waitFor(() => counts.upload === 1 && counts.download === 1, 'in-flight operations');
    (host.querySelector('[data-fixture-action="logout"]') as HTMLButtonElement).click();
    await waitFor(
      () => host.querySelector('[data-fixture-session]')?.textContent === 'logged-out',
      'logout completed',
    );
    const headsAfterLogout = counts.head;
    await new Promise((resolve) => setTimeout(resolve, 150));
    check(
      counts.head === headsAfterLogout &&
        !host.querySelector('img') &&
        !host.textContent?.includes('old-selection.png') &&
        revoked.has(oldUrl),
      'logout stable / preview revoked',
    );
    check(uploadSignal?.aborted && downloadSignal?.aborted, 'in-flight abort signals');
    (host.querySelector('[data-fixture-action="login"]') as HTMLButtonElement).click();
    await waitFor(
      () => Boolean(host.querySelector('[aria-label="second-user.png 다운로드"]')),
      'second user files',
    );
    const filesBeforeLate = counts.files;
    uploadGate.resolve();
    downloadGate.resolve();
    await new Promise((resolve) => setTimeout(resolve, 150));
    check(
      lastPage === 0 &&
        lastSize === 20 &&
        counts.files === filesBeforeLate &&
        !host.textContent?.includes('old-file-20') &&
        created === beforeCreated,
      'new page/cache and stale completion/download blocked',
    );

    return {
      counts,
      headsAfterLogout,
      page: lastPage,
      size: lastSize,
      previewRevoked: true,
      signalsAborted: true,
      lateDownloadSaveBlocked: true,
      oldInvalidationBlocked: true,
    };
  } finally {
    uploadGate.resolve();
    downloadGate.resolve();
    root.unmount();
    queryClient.clear();
    URL.createObjectURL = create;
    URL.revokeObjectURL = revoke;
  }
}

/** 외부 값 교체 후 화면과 부모 오류를 함께 관찰하는 실제 날짜 컴포넌트 회귀다. */
function DatesCase() {
  const [value, setValue] = useState('2026-10-20');
  const [error, setError] = useState<string>();
  const [range, setRange] = useState({ from: '2026-10-20', to: '2026-10-28' });
  const [rangeError, setRangeError] = useState<string>();

  return (
    <section>
      <h2>외부 날짜 값 회귀</h2>
      <DatePicker
        aria-label="회귀 단일 날짜"
        value={value}
        onChange={setValue}
        max="2026-11-30"
        onValidation={setError}
      />
      <button onClick={() => setValue('2026-10-25')}>외부 유효 날짜</button>
      <button onClick={() => setValue('2026-02-30')}>외부 잘못된 날짜</button>
      <button onClick={() => setValue('2026-12-31')}>외부 제한 밖 날짜</button>
      <output aria-label="단일 부모 오류">{error ?? '정상'}</output>
      <DateRangeInput
        from={range.from}
        to={range.to}
        maxDays={31}
        onChange={(from, to) => setRange({ from, to })}
        onValidation={setRangeError}
      />
      <button onClick={() => setRange((r) => ({ ...r, from: '2026-10-21' }))}>
        외부 시작 교체
      </button>
      <button onClick={() => setRange((r) => ({ ...r, to: '2026-10-29' }))}>외부 종료 교체</button>
      <button onClick={() => setRange({ from: '2026-10-30', to: '2026-10-21' })}>
        외부 역순 기간
      </button>
      <button onClick={() => setRange({ from: '2026-10-01', to: '2026-12-01' })}>
        외부 긴 기간
      </button>
      <output aria-label="기간 부모 오류">{rangeError ?? '정상'}</output>
    </section>
  );
}

/** 실행 결과만 공개하고 합성 요청을 실제 서버 통합 결과와 구분한다. */
function App() {
  const [result, setResult] = useState('READY');
  const [anonymousResult, setAnonymousResult] = useState('READY');
  const [transitionResult, setTransitionResult] = useState('READY');
  return (
    <>
      <h1>파일 경계·날짜 회귀 · 실제 서버 요청 없음</h1>
      <button
        onClick={() =>
          void runTransport()
            .then((rows) => setResult(JSON.stringify({ result: 'PASS', checks: rows }, null, 2)))
            .catch((error: unknown) =>
              setResult(error instanceof Error ? `FAIL: ${error.message}` : 'FAIL'),
            )
        }
      >
        파일 경계 회귀 실행
      </button>
      <pre aria-label="파일 경계 결과">{result}</pre>
      <button
        onClick={() => {
          const host = document.getElementById('anonymous-files');
          if (host)
            void runAnonymousFiles(host)
              .then((counts) => setAnonymousResult(JSON.stringify({ result: 'PASS', counts })))
              .catch((error) =>
                setAnonymousResult(error instanceof Error ? `FAIL: ${error.message}` : 'FAIL'),
              );
        }}
      >
        익명 파일 예제 회귀 실행
      </button>
      <pre aria-label="익명 파일 회귀 결과">{anonymousResult}</pre>
      <div id="anonymous-files" />
      <button
        onClick={() => {
          const host = document.getElementById('transition-files');
          if (host)
            void runFilesTransition(host)
              .then((result) => setTransitionResult(JSON.stringify({ result: 'PASS', ...result })))
              .catch((error) =>
                setTransitionResult(error instanceof Error ? `FAIL: ${error.message}` : 'FAIL'),
              );
        }}
      >
        파일 로그아웃 전환 회귀 실행
      </button>
      <pre aria-label="파일 전환 회귀 결과">{transitionResult}</pre>
      <div id="transition-files" />
      <DatesCase />
    </>
  );
}
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
