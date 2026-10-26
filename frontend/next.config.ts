import type { NextConfig } from 'next';
import { PHASE_PRODUCTION_SERVER } from 'next/constants';

import { getEnvironment } from './src/config/environment';

/** 공통 서버 옵션은 실행 시 환경 설정을 요구하지 않는다. */
const baseConfig: NextConfig = {
  // 저장소 지침은 설계 담당이 관리하므로 개발 서버의 지침 자동 생성을 사용하지 않는다.
  agentRules: false,
  poweredByHeader: false,
  productionBrowserSourceMaps: false,
  async headers() {
    return [
      {
        source: '/_next/static/:path*',
        headers: [{ key: 'X-Content-Type-Options', value: 'nosniff' }],
      },
    ];
  },
};

/** 입력은 build/dev에서만 검증하고 Next 표준 산출물에 필요한 설정만 반영한다. */
export default function configureNext(phase: string): NextConfig {
  if (phase === PHASE_PRODUCTION_SERVER) {
    return baseConfig;
  }

  const { appEnv, localApiOrigin } = getEnvironment();

  return {
    ...baseConfig,
    // 공개 가능한 환경명 하나만 정적 치환한다. origin·비밀·전체 env는 복사하지 않는다.
    env: { APP_ENV: appEnv },
    async rewrites() {
      return localApiOrigin
        ? [{ source: '/api/:path*', destination: `${localApiOrigin}/api/:path*` }]
        : [];
    },
  };
}
