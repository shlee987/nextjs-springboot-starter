import { spawn } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { loadEnvironment } from './environment.mjs';

const root = dirname(dirname(fileURLToPath(import.meta.url)));

/** 현재 Node와 명시 환경으로 Next/TypeScript를 실행하고 종료·시그널을 전달한다. */
function runTool(file, args, environment) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [join(root, file), ...args], {
      cwd: root,
      env: environment,
      stdio: 'inherit',
      shell: false,
    });
    const signals = new Map(
      ['SIGINT', 'SIGTERM'].map((signal) => [signal, () => child.kill(signal)]),
    );

    for (const [signal, forward] of signals) {
      process.on(signal, forward);
    }

    /** 자식이 끝나면 해당 실행의 시그널 핸들러를 정리한다. */
    function cleanup() {
      for (const [signal, forward] of signals) {
        process.off(signal, forward);
      }
    }

    child.once('error', () => {
      cleanup();
      reject(new Error('실행 도구를 시작하지 못했습니다. npm ci와 Node 버전을 확인하세요.'));
    });
    child.once('exit', (code) => {
      cleanup();
      resolve(code ?? 1);
    });
  });
}

/** build/dev 입력을 검증하고 Next 또는 타입 검사를 실행한다. start는 담당하지 않는다. */
async function main() {
  const [command, profile, ...extra] = process.argv.slice(2);

  if (extra.length > 0) {
    throw new Error('추가 인자는 지원하지 않습니다. package.json의 환경별 명령을 사용하세요.');
  }

  const environment = loadEnvironment(root, profile, command);
  const next = 'node_modules/next/dist/bin/next';
  const args = command === 'typecheck' ? ['typegen'] : [command];

  if (command === 'dev') {
    args.push('--hostname', '127.0.0.1', '--port', '3000');
  }

  let code = await runTool(next, args, environment);

  if (code === 0 && command === 'typecheck') {
    code = await runTool('node_modules/typescript/bin/tsc', ['--noEmit'], environment);
  }

  process.exitCode = code;
}

try {
  await main();
} catch (error) {
  // 승인된 설정 오류만 표시하고 알 수 없는 예외 원문은 공개하지 않는다.
  const safeMessages = [
    '실행 환경은',
    '지원하지 않는',
    'Next 개발 모드는',
    '선택한 환경',
    '선택한 명령',
    'APP_ENV는',
    'APP_PUBLIC_ORIGIN',
    'LOCAL_API_ORIGIN',
    '추가 인자는',
    '실행 도구를',
  ];
  console.error(
    error instanceof Error && safeMessages.some((prefix) => error.message.startsWith(prefix))
      ? error.message
      : '실행 준비에 실패했습니다. 설정과 설치 상태를 확인하세요.',
  );
  process.exitCode = 1;
}
