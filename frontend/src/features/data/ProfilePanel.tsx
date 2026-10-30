'use client';

import Link from 'next/link';

import { useGetMyProfile, useGetUser } from '@/api/generated/client';
import { ErrorState, Spinner } from '@/components/ui/Feedback';
import { PermissionGuard, useSession } from '@/features/auth/AuthProvider';
import { toDisplayDate } from '@/lib/dates';
import styles from '@/components/ui/ui.module.css';

import { profileSchema, readEnvelope } from './contracts';

/** 다른 사용자의 개인 정보는 목록 권한과 개인 정보 권한을 모두 확인한다. */
export function UserDetailScreen({ userId }: { userId: string }) {
  return (
    <PermissionGuard permission="USER_READ">
      <PermissionGuard permission="PERSONAL_INFO_READ">
        <div className={styles.pageStack}>
          <h1>사용자 상세</h1>
          <section className={styles.panel} aria-label="사용자 프로필">
            <ProfilePanel userId={userId} />
          </section>
          <Link href="/users">목록으로</Link>
        </div>
      </PermissionGuard>
    </PermissionGuard>
  );
}

/** 내 프로필과 사용자 상세를 실제 생성 Hook으로 읽고 원문 시각은 서울 시간으로 표시한다. */
export function ProfilePanel({ userId }: { userId?: string }) {
  const { generation, status } = useSession();
  const mine = useGetMyProfile({
    query: {
      queryKey: ['profile', generation, 'me'],
      enabled: !userId && status === 'authenticated',
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
      select: (response) => readEnvelope(profileSchema, response),
    },
  });
  const other = useGetUser(userId ?? '', {
    query: {
      queryKey: ['profile', generation, userId],
      enabled: Boolean(userId) && status === 'authenticated',
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
      select: (response) => readEnvelope(profileSchema, response),
    },
  });
  const query = userId ? other : mine;

  if (query.isPending) {
    return <Spinner label="프로필을 불러오는 중" />;
  }

  if (query.error) {
    return (
      <ErrorState
        message={query.error.message}
        traceId={query.error.traceId}
        onRetry={() => void query.refetch()}
      />
    );
  }

  const profile = query.data?.data;

  if (!profile) {
    return <ErrorState />;
  }

  return (
    <dl className={styles.profileGrid}>
      {[
        ['ID', profile.userId],
        ['이름', profile.displayName],
        ['로그인 ID', profile.loginId],
        ['이메일', profile.email ?? '등록 없음'],
        ['상태', profile.status],
        [
          '부서',
          profile.departments.map((department) => department.departmentName).join(', ') ||
            '소속 없음',
        ],
        ['생성 · Asia/Seoul', toDisplayDate(profile.createdAt, 'millisecond')],
        ['수정 · Asia/Seoul', toDisplayDate(profile.updatedAt, 'millisecond')],
      ].map(([label, value]) => (
        <div className={styles.profilePair} key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}
