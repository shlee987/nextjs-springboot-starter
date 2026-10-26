import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseEnv } from 'node:util';

import { getEnvironment } from '../src/config/environment.ts';

/** build/dev용 선택 파일 하나와 외부 설정을 합친다. Next 기본 env 동작은 변경하지 않는다. */
export function loadEnvironment(root, profile, command, external = process.env) {
  if (!['local', 'dev', 'stg', 'prod'].includes(profile)) {
    throw new Error('실행 환경은 local/dev/stg/prod 중 하나여야 합니다.');
  }

  if (!['dev', 'build', 'typecheck'].includes(command)) {
    throw new Error('지원하지 않는 실행 명령입니다.');
  }

  if (command === 'dev' && profile !== 'local') {
    throw new Error('Next 개발 모드는 local에서만 실행할 수 있습니다.');
  }

  const file = join(root, 'config', 'env', `.env.${profile}`);
  let values = {};

  try {
    values = existsSync(file) ? parseEnv(readFileSync(file, 'utf8')) : {};
  } catch {
    throw new Error('선택한 환경 파일을 읽을 수 없습니다.');
  }

  const nodeEnv = command === 'dev' ? 'development' : 'production';

  if (values.APP_ENV !== undefined && values.APP_ENV !== profile) {
    throw new Error('선택한 환경과 파일의 APP_ENV가 일치하지 않습니다.');
  }

  if (values.NODE_ENV !== undefined && values.NODE_ENV !== nodeEnv) {
    throw new Error('선택한 명령과 파일의 NODE_ENV가 일치하지 않습니다.');
  }

  const environment = { ...values, ...external, APP_ENV: profile, NODE_ENV: nodeEnv };
  // 공유 origin은 명시 입력에서 먼저 검증해 루트 .env.local의 local 값으로 충족하지 않는다.
  getEnvironment(environment);

  return environment;
}
