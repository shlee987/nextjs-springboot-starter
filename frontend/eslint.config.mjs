import { defineConfig, globalIgnores } from 'eslint/config';
import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTypescript from 'eslint-config-next/typescript';

/** Next와 TypeScript의 기본 정적 검사를 모듈 전체에 적용한다. */
export default defineConfig([
  ...nextVitals,
  ...nextTypescript,
  globalIgnores(['.next/**', 'next-env.d.ts', 'src/api/generated/**']),
]);
