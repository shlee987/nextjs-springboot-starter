import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';

import { build } from 'esbuild';

const root = fileURLToPath(new URL('../', import.meta.url));
const result = await build({
  absWorkingDir: root,
  entryPoints: ['scripts/authRegression.tsx'],
  bundle: true,
  platform: 'browser',
  format: 'esm',
  write: false,
  outdir: 'auth-regression',
  define: { 'process.env.NODE_ENV': '"development"' },
  plugins: [
    {
      name: 'fixture-link',
      /** 인증 회귀와 무관한 Next Link만 검사 문서의 네이티브 링크로 대체한다. */
      setup(builder) {
        // 라우팅은 이 회귀 대상이 아니므로 Link만 네이티브 앵커로 대역한다.
        builder.onResolve({ filter: /^next\/link$/ }, () => ({
          path: 'link',
          namespace: 'fixture',
        }));
        builder.onLoad({ filter: /.*/, namespace: 'fixture' }, () => ({
          contents:
            'import {createElement} from "react"; export default function Link({href,children,...props}) {return createElement("a",{...props,href},children)}',
          resolveDir: root,
        }));
      },
    },
  ],
});
const javascript = result.outputFiles.find((file) => file.path.endsWith('.js'))?.contents;
const css = result.outputFiles.find((file) => file.path.endsWith('.css'))?.contents;

/** 외부 백엔드로 전달하지 않고 실제 Provider와 응답 대역만 담은 검사 문서를 루프백에 제공한다. */
const server = createServer((request, response) => {
  response.setHeader('Cache-Control', 'no-store');

  if (request.url === '/fixture.js') {
    response.setHeader('Content-Type', 'text/javascript');
    response.end(javascript);
  } else if (request.url === '/fixture.css') {
    response.setHeader('Content-Type', 'text/css');
    response.end(css);
  } else if (request.url === '/') {
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end(
      '<!doctype html><html lang="ko"><title>인증 응답 대역 회귀</title><link rel="stylesheet" href="/fixture.css"><button>인증 회귀 실행</button><pre aria-label="회귀 결과">READY</pre><div id="fixture"></div><script type="module" src="/fixture.js"></script></html>',
    );
  } else {
    response.writeHead(404).end();
  }
});
server.listen(43175, '127.0.0.1', () =>
  console.log('Auth response-double regression: http://localhost:43175 (no backend/DB requests).'),
);
