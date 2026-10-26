import styles from './page.module.css';

/** 기능 화면을 추가하기 전 프로젝트의 기본 문서를 제공한다. */
export default function HomePage() {
  return (
    <main className={styles.main}>
      <h1>Starter</h1>
      <p>프로젝트 기본 구성을 준비했습니다.</p>
    </main>
  );
}
