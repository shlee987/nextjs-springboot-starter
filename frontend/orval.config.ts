import { existsSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'orval';

const configDirectory = dirname(fileURLToPath(import.meta.url));
const specPath = resolve(
  configDirectory,
  process.env.OPENAPI_SPEC_PATH ?? '../docs/api/openapi.json',
);

if (!existsSync(specPath) || !statSync(specPath).isFile()) {
  throw new Error(
    `OpenAPI 파일을 찾을 수 없습니다: ${specPath}. docs/api/openapi.json을 준비하거나 OPENAPI_SPEC_PATH에 로컬 명세 파일을 지정하세요.`,
  );
}

/** 저장소의 실제 명세 또는 명시한 로컬 명세로 Axios·Query v5 코드를 생성한다. mock은 생성하지 않는다. */
export default defineConfig({
  api: {
    input: { target: specPath },
    output: {
      target: './src/api/generated/client.ts',
      schemas: './src/api/generated/models',
      client: 'react-query',
      httpClient: 'axios',
      mock: false,
      override: {
        mutator: { path: './src/api/client.ts', name: 'apiMutator' },
        query: { version: 5, signal: true },
      },
    },
  },
});
