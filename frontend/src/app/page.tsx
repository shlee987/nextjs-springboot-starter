'use client';
import Link from 'next/link';
import { menuGroups, menuItems } from '@/config/navigation';
import { useSession } from '@/features/auth/AuthProvider';
import { Icon } from '@/components/ui/Icon';
import styles from '@/components/ui/ui.module.css';

/** 공통 registry의 분류·용도·현재 서버 권한으로 실행 가능한 기능을 안내한다. */
export default function HomePage() {
  const session = useSession();
  const items = menuItems.filter(
    (item) =>
      item.id !== 'home' &&
      (!item.example || process.env.APP_EXAMPLES_ENABLED === 'true') &&
      (!item.permission || session.user?.permissions.includes(item.permission)),
  );
  return (
    <section className={styles.catalog}>
      <div className={styles.exampleHeader}>
        <h1>공통 기능 카탈로그</h1>
        <p className={styles.help}>
          필요한 기능을 골라 동작과 사용 코드를 확인하세요. 예제 데이터와 실제 서버 조회는 각
          화면에서 구분합니다.
        </p>
      </div>
      {menuGroups.map((group) => {
        const entries = items.filter((item) => item.category === group.id);
        return (
          entries.length > 0 && (
            <section className={styles.catalogGroup} key={group.id}>
              <h2>{group.label}</h2>
              <div className={styles.catalogGrid}>
                {entries.map((item) => (
                  <Link className={styles.catalogCard} href={item.href} key={item.id}>
                    <div>
                      <h3>{item.label}</h3>
                      <p>{item.description}</p>
                    </div>
                    <Icon name="right" />
                  </Link>
                ))}
              </div>
            </section>
          )
        );
      })}
      <details className={styles.codePanel}>
        <summary>실행 방법과 예제 범위</summary>
        <p>
          개발: <code>npm run local</code> · 환경별 빌드: <code>npm run build:local</code> · 실행:{' '}
          <code>npm start</code>
        </p>
        <p>
          일반 컨트롤 예제는 화면 안에서만 동작합니다. 내 정보·사용자·부서·이력은 연결된 서버의
          계정과 권한이 필요합니다. 공유 환경에서는 예제 메뉴와 직접 주소를 기본 제공하지 않습니다.
        </p>
      </details>
    </section>
  );
}
