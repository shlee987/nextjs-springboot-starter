'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';
import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import { SidePanel } from '@/components/ui/Overlays';
import { useToast } from '@/components/ui/Feedback';
import { getMenuItem, menuGroups, menuItems, type MenuItem } from '@/config/navigation';
import { useSession } from '@/features/auth/AuthProvider';
import { useUiStore } from '@/stores/UiStore';
import styles from './AppShell.module.css';

/** 같은 registry의 분류·접근 이름·현재 위치를 PC와 drawer에서 함께 사용한다. */
function MenuLinks({
  items,
  activeId,
  collapsed,
  onNavigate,
}: {
  items: MenuItem[];
  activeId?: string;
  collapsed?: boolean;
  onNavigate: () => void;
}) {
  return (
    <nav aria-label="주 메뉴">
      <Link
        className={styles.homeLink}
        href="/"
        aria-label="기능 목차"
        aria-current={activeId === 'home' ? 'page' : undefined}
        onClick={onNavigate}
      >
        <Icon name="menu" />
        {!collapsed && '기능 목차'}
      </Link>
      {menuGroups.map((group) => {
        const entries = items.filter((item) => item.id !== 'home' && item.category === group.id);
        return (
          entries.length > 0 && (
            <section className={styles.menuGroup} key={group.id}>
              {!collapsed && <h2 className={styles.menuGroupLabel}>{group.label}</h2>}
              <ul>
                {entries.map((entry) => (
                  <li key={entry.id}>
                    <Link
                      href={entry.href}
                      aria-label={entry.label}
                      aria-current={activeId === entry.id ? 'page' : undefined}
                      onClick={onNavigate}
                    >
                      <Icon name={entry.example ? 'right' : 'info'} />
                      {!collapsed && entry.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )
        );
      })}
    </nav>
  );
}

/** 메뉴·사용자 표시·본문을 조합하며 카탈로그에서 인증 폴링을 시작하지 않는다. */
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const collapsed = useUiStore((state) => state.collapsed);
  const mobileOpen = useUiStore((state) => state.mobileOpen);
  const setCollapsed = useUiStore((state) => state.setCollapsed);
  const setMobileOpen = useUiStore((state) => state.setMobileOpen);
  const session = useSession();
  const notify = useToast();
  const item = getMenuItem(pathname);
  const items = menuItems.filter(
    (entry) =>
      (!entry.example || process.env.APP_EXAMPLES_ENABLED === 'true') &&
      (!entry.permission ||
        (session.status === 'authenticated' &&
          session.user?.permissions.includes(entry.permission))),
  );
  useEffect(() => {
    const media = window.matchMedia('(min-width: 992px)');
    /** 넓은 화면으로 전환하면 drawer를 닫아 본문·스크롤 잠금 상태를 복구한다. */
    function closeDesktopDrawer() {
      if (media.matches) setMobileOpen(false);
    }
    media.addEventListener('change', closeDesktopDrawer);
    return () => media.removeEventListener('change', closeDesktopDrawer);
  }, [setMobileOpen]);
  if (pathname === '/login')
    return (
      <main id="main" className={styles.login}>
        {children}
      </main>
    );
  return (
    <div className={styles.shell}>
      <a href="#main" className={styles.skip}>
        본문으로 이동
      </a>
      <header className={styles.header}>
        <div className={styles.headerActions}>
          <Button
            className={styles.mobileButton}
            aria-controls="primary-menu-mobile"
            aria-expanded={mobileOpen}
            onClick={() => setMobileOpen(true)}
            leadingIcon={<Icon name="menu" />}
          >
            메뉴
          </Button>
          <Link className={styles.brand} href="/">
            FOUNDATION <span>공통 기능</span>
          </Link>
        </div>
        <div className={styles.headerActions}>
          <span className={styles.accountName}>{session.user?.displayName ?? '인증 확인 전'}</span>
          {session.status === 'authenticated' ? (
            <Button
              size="small"
              onClick={() => {
                void session
                  .logout()
                  .catch(() =>
                    notify('서버 로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.', 'error'),
                  );
              }}
            >
              로그아웃
            </Button>
          ) : (
            <Link className={styles.loginLink} href="/login">
              로그인
            </Link>
          )}
        </div>
      </header>
      <div className={styles.body} data-collapsed={collapsed}>
        <aside className={styles.menu} aria-label="기본 메뉴">
          <Button
            size="small"
            aria-label={collapsed ? '메뉴 펼치기' : '메뉴 접기'}
            onClick={() => setCollapsed(!collapsed)}
            leadingIcon={<Icon name={collapsed ? 'right' : 'left'} />}
          >
            {!collapsed && '메뉴 접기'}
          </Button>
          <MenuLinks
            items={items}
            activeId={item?.id}
            collapsed={collapsed}
            onNavigate={() => setMobileOpen(false)}
          />
        </aside>
        <main id="main" className={styles.content}>
          <div className={styles.breadcrumb}>Foundation / {item?.label ?? '페이지'}</div>
          <div className={styles.page}>{children}</div>
        </main>
      </div>
      <SidePanel open={mobileOpen} onOpenChange={setMobileOpen} title="주 메뉴">
        <div id="primary-menu-mobile" className={styles.drawerMenu}>
          <MenuLinks items={items} activeId={item?.id} onNavigate={() => setMobileOpen(false)} />
        </div>
      </SidePanel>
    </div>
  );
}
