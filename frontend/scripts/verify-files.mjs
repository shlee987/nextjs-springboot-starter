import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = await build({
  absWorkingDir: root,
  entryPoints: ['scripts/filesRegression.tsx'],
  bundle: true,
  write: false,
  outdir: 'files-regression',
  format: 'esm',
  platform: 'browser',
  external: ['/fonts/*'],
  define: { 'process.env.NODE_ENV': '"development"' },
  plugins: [
    {
      name: 'fixture-navigation',
      setup(builder) {
        builder.onResolve({ filter: /^next\/(?:link|navigation)$/ }, (args) => ({
          path: args.path,
          namespace: 'fixture',
        }));
        builder.onLoad({ filter: /.*/, namespace: 'fixture' }, (args) => ({
          contents:
            args.path === 'next/navigation'
              ? 'export const usePathname=()=>"/examples/files";'
              : 'import {createElement} from "react";export default function Link({href,children,...props}){return createElement("a",{...props,href},children)}',
          resolveDir: root,
        }));
      },
    },
  ],
});
const js = output.outputFiles.find((file) => file.path.endsWith('.js'))?.contents;
const css = output.outputFiles.find((file) => file.path.endsWith('.css'))?.contents;

/** 합성 응답 adapter만 사용하는 루프백 화면이며 실제 API 요청은 네트워크로 전달하지 않는다. */
const server = createServer(async (request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader(
    'Content-Security-Policy',
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; connect-src 'none'; object-src 'none'",
  );

  if (request.url === '/fixture.js') {
    response.setHeader('Content-Type', 'text/javascript');
    response.end(js);
  } else if (request.url === '/fixture.css') {
    response.setHeader('Content-Type', 'text/css');
    response.end(css);
  } else if (request.url === '/fonts/pretendard/PretendardVariable.woff2') {
    response.setHeader('Content-Type', 'font/woff2');
    response.end(
      await readFile(
        new URL('../public/fonts/pretendard/PretendardVariable.woff2', import.meta.url),
      ),
    );
  } else if (request.url === '/') {
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end(
      '<!doctype html><html lang="ko"><title>파일 경계·날짜 회귀</title><link rel="stylesheet" href="/fixture.css"><div id="root"></div><script type="module" src="/fixture.js"></script></html>',
    );
  } else response.writeHead(404).end();
});
server.listen(43181, '127.0.0.1', () =>
  console.log('File/date regression http://localhost:43181; no API/DB requests'),
);
