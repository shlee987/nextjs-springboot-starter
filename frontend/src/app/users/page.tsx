import { DirectoryScreen } from '@/features/data/DirectoryScreen';

/** 실제 생성 Hook의 사용자 서버 페이지를 권한 경계 안에 표시한다. */
export default function UsersPage() {
  return <DirectoryScreen kind="users" />;
}
