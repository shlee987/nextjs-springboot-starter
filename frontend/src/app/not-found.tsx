import Link from 'next/link';

/** 기본 404의 SSR 인라인 스타일 없이 안전한 문서를 제공한다. */
export default function NotFoundPage() {
  return (
    <section>
      <h1>페이지를 찾을 수 없습니다.</h1>
      <Link href="/">처음으로</Link>
    </section>
  );
}
