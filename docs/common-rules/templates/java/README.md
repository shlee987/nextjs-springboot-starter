# Java 새 파일 템플릿

- 일반 객체: `class.java.template`
- 필요한 계약: `interface.java.template`
- 해당 Java 버전에서 지원하며 데이터 계약에 적합한 불변 데이터: `record.java.template`

package는 기존 구조에 맞춘다. 클래스 설명과 추가하는 생성자·메서드 설명을 한국어 Javadoc으로 작성한다.
프레임워크 애너테이션·상속·의존성은 실제 요구가 있을 때만 추가한다. 내부 값이 가변 객체이면 record가 깊은 불변성을 보장한다고 가정하지 않는다.

