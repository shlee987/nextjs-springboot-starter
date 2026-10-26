# nextjs-springboot-starter

## 설계문서

- [1차 프로젝트 세팅 범위·인계 기준](docs/implementation/initial-setup.md)
- [공통 API 응답·간편 반환 계약](docs/implementation/api-contract.md)

- [구현 설계 안내](docs/implementation/README.md)
- [확정 사항과 구현 전 결정 항목](docs/implementation/decisions.md)
- [공통 구현 작업](docs/implementation/engineering.md)
- [확정 기술 구성·버전표](docs/implementation/stack-versions.md)
- [상황별 대체 조합·선택 조건](docs/implementation/stack-alternatives.md)
- [기본 라이브러리 역할·통합 원칙](docs/implementation/technology-stack.md)
- [Docker 로컬 DB 환경 설계](docs/implementation/local-environment.md)
- [보안 요구사항](docs/implementation/security/baseline.md)
- [보안 검증과 완료 기준](docs/implementation/security/verification.md)
- [공통 보안 기준 검토 결과](docs/implementation/security/review-report.md)

프론트·백엔드 1차 기반 구현·코드리뷰와 로컬 PostgreSQL·Redis 기초 연동을 확인했습니다. [1차 변경 이력과 검증 범위](docs/implementation/change-history.md)를 참고하세요. 실제 검증·미검증 범위와 실행 방법은 [프론트 안내](frontend/README.md), [백엔드 안내](backend/README.md), [로컬 DB·Redis 준비](infra/local/README.md)를 확인하세요. 프론트 패키지 관리자는 사용자 결정으로 npm을 사용하며, 실제 로그인·업무 기능·다중 서버·프론트 프록시의 업무 API 연동과 운영 배포 보안 검증을 완료한 것으로 간주하지 않습니다.
