# PLAN

## 배경

이 문서는 2026-03-10 인터뷰 결과를 바탕으로 현재 구조 개편의 결정을 기록한다.

## 인터뷰 결론

- 이 라이브러리는 공통 유틸이면서 동시에 조직 표준 Spring Kafka 모듈이다.
- `notification`, `usage`, `policy`는 라이브러리 안에 남겨야 하는 표준 이벤트 계약이다.
- `EventEnvelope`의 subtype 중앙 등록 방식은 의도된 운영 모델이다.
- 사용성은 zero-configuration 쪽을 우선한다.
- metrics는 기본 기능으로 유지한다.
- tracing은 현재 필수가 아니므로 제거 가능하다.
- 패키지 리네임은 브레이킹 체인지지만 지금 정리하는 편이 낫다.
- 목표 네임스페이스는 `com.dabom.messaging.kafka` 이다.

## 이번 리팩터링 범위

1. 루트 패키지를 `com.project.global`에서 `com.dabom.messaging.kafka`로 변경한다.
2. Spring 런타임 설정은 `autoconfigure`로 이동한다.
3. `kafka.error` 계층은 `error`로 평탄화한다.
4. 표준 이벤트 계약은 `event.dto` 아래에 유지한다.
5. 메트릭 관련 코드는 `metrics` 아래에 유지한다.
6. 보조 인프라 성격의 코드는 `support`로 이동한다.
7. 사용하지 않는 tracing 지원은 제거한다.
8. 예시성 DTO는 제거한다.
9. 기본 메시지 포맷은 JSON 문자열 기반 envelope로 유지한다.

## 후속 작업

1. 소비 서비스의 import 경로를 `com.dabom.messaging.kafka` 기준으로 수정한다.
2. 소비 서비스의 component scan 설정을 새 패키지 기준으로 수정한다.
3. 브레이킹 체인지로 보고 새 태그를 발행한다.
4. 필요하면 이후 버전에서 진짜 Spring Boot auto-configuration 모듈로 발전시킬지 검토한다.
5. 사용처가 안정화되면 public/internal 경계를 더 명확히 나눈다.

## 버전 정책 메모

패키지 경로가 바뀌었기 때문에, 런타임 동작이 거의 같더라도 소비자 입장에서는 브레이킹 변경으로 취급하는 것이 맞다.
