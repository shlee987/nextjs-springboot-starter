import { defineConfig } from 'orval';

const specPath = process.env.OPENAPI_SPEC_PATH;

if (!specPath) {
  throw new Error('OPENAPI_SPEC_PATH에 backend가 제공한 로컬 OpenAPI 파일을 지정해야 합니다.');
}

/** 실제 명세 제공 이후에만 Axios·Query v5 코드를 생성한다. 가짜 명세와 mock은 생성하지 않는다. */
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
        query: { version: 5, useQuery: true, useMutation: true, signal: true },
      },
    },
  },
});
