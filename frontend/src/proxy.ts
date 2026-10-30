import { randomBytes } from 'node:crypto';
import { NextResponse, type NextRequest } from 'next/server';

/** Next 문서마다 신뢰할 수 있는 nonce와 CSP를 요청·응답에 함께 설정한다. */
export function proxy(request: NextRequest) {
  // Next config가 공개 환경명 하나만 빌드 시 정적 치환한다. runtime 설정을 읽지 않는다.
  const appEnv = process.env.APP_ENV;
  const isLocalDevelopment = appEnv === 'local' && process.env.NODE_ENV === 'development';
  const nonce = randomBytes(16).toString('base64');
  const csp = [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isLocalDevelopment ? " 'unsafe-eval'" : ''}`,
    "script-src-attr 'none'",
    isLocalDevelopment ? "style-src 'self' 'unsafe-inline'" : `style-src 'self' 'nonce-${nonce}'`,
    `style-src-attr ${isLocalDevelopment ? "'unsafe-inline'" : "'none'"}`,
    "img-src 'self' data: blob:",
    "font-src 'self'",
    `connect-src 'self'${isLocalDevelopment ? ' ws://127.0.0.1:3000 ws://localhost:3000' : ''}`,
    "media-src 'none'",
    "object-src 'none'",
    "base-uri 'none'",
    "frame-src 'none'",
    "frame-ancestors 'none'",
    "form-action 'self'",
    "worker-src 'none'",
    "manifest-src 'self'",
    ...(appEnv === 'local' ? [] : ['upgrade-insecure-requests']),
  ].join('; ');
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set('x-nonce', nonce);
  requestHeaders.set('Content-Security-Policy', csp);
  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set('Content-Security-Policy', csp);
  response.headers.set('Cache-Control', 'no-store');
  response.headers.set('X-Content-Type-Options', 'nosniff');
  response.headers.set('X-Frame-Options', 'DENY');
  response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
  response.headers.set(
    'Permissions-Policy',
    'camera=(), microphone=(), geolocation=(), payment=()',
  );
  response.headers.set('X-XSS-Protection', '0');

  return response;
}

export const config = { matcher: ['/((?!api(?:/|$)|_next/static/|_next/image/).*)'] };
