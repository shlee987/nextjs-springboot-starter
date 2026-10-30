/** build/dev 입력으로 허용하는 배포 환경 이름이다. */
export type AppEnvironment = 'local' | 'dev' | 'stg' | 'prod';

/** build/dev 입력을 검증한다. production 요청이나 start에서는 호출하지 않는다. */
export function getEnvironment(environment: NodeJS.ProcessEnv = process.env) {
  const appEnv = environment.APP_ENV;

  if (appEnv !== 'local' && appEnv !== 'dev' && appEnv !== 'stg' && appEnv !== 'prod') {
    throw new Error('APP_ENV는 local/dev/stg/prod 중 하나로 명시해야 합니다.');
  }

  if (environment.NODE_ENV === 'development' && appEnv !== 'local') {
    throw new Error('Next 개발 모드는 APP_ENV=local에서만 허용됩니다.');
  }

  const originValue =
    environment.APP_PUBLIC_ORIGIN ?? (appEnv === 'local' ? 'http://localhost:3000' : '');
  let origin: URL;

  try {
    origin = new URL(originValue);
  } catch {
    throw new Error('APP_PUBLIC_ORIGIN에 유효한 origin이 필요합니다.');
  }

  if (
    origin.username ||
    origin.password ||
    origin.pathname !== '/' ||
    origin.search ||
    origin.hash ||
    (appEnv !== 'local' && origin.protocol !== 'https:') ||
    (appEnv === 'local' &&
      (!['http:', 'https:'].includes(origin.protocol) ||
        !['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname)))
  ) {
    throw new Error('APP_PUBLIC_ORIGIN 형식 또는 HTTPS 정책이 올바르지 않습니다.');
  }

  return {
    appEnv: appEnv satisfies AppEnvironment,
    examplesEnabled: getExamplesEnabled(environment.APP_EXAMPLES_ENABLED, appEnv),
    localApiOrigin:
      appEnv === 'local' ? getLocalApiOrigin(environment.LOCAL_API_ORIGIN) : undefined,
  };
}

/** 예제 공개 여부는 명시 boolean만 허용하며 공유 환경의 기본값은 false다. */
function getExamplesEnabled(value: string | undefined, appEnv: AppEnvironment): boolean {
  if (value !== undefined && value !== 'true' && value !== 'false') {
    throw new Error('APP_EXAMPLES_ENABLED는 true/false여야 합니다.');
  }

  return value === undefined ? appEnv === 'local' : value === 'true';
}

/** 로컬 개발 전달 대상은 자격·추가 경로 없는 loopback HTTP(S) origin만 허용한다. */
function getLocalApiOrigin(value: string | undefined): string {
  let origin: URL;

  try {
    origin = new URL(value ?? 'http://127.0.0.1:8080');
  } catch {
    throw new Error('LOCAL_API_ORIGIN에 유효한 loopback origin이 필요합니다.');
  }

  if (
    !['http:', 'https:'].includes(origin.protocol) ||
    !['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname) ||
    origin.username ||
    origin.password ||
    origin.pathname !== '/' ||
    origin.search ||
    origin.hash
  ) {
    throw new Error('LOCAL_API_ORIGIN은 자격·추가 경로 없는 loopback HTTP(S) origin이어야 합니다.');
  }

  return origin.origin;
}
