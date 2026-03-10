# 패키지 구조

## 결정 요약

이 라이브러리는 DABOM 서비스 전반에서 공통으로 쓰는 메시징 표준 모듈로 정리했다.
역할은 크게 네 가지다.

- Spring Kafka 기본 설정 제공
- 조직 표준 이벤트 계약 제공
- 공통 에러 처리 규칙 제공
- 공통 메트릭 제공

사용하지 않던 tracing 지원은 이번 정리에서 제거했다. 현재 운영 계약이 없고 의존성 면적만 넓히고 있었기 때문이다.

## 루트 네임스페이스

루트 패키지는 `com.dabom.messaging.kafka` 로 통일했다.

선정 이유는 다음과 같다.

- `com.dabom`: 조직 소유 네임스페이스를 드러낸다.
- `messaging`: 이후 Kafka 외 메시징 기술로 확장할 여지를 남긴다.
- `kafka`: 현재 모듈이 Kafka 전용 라이브러리임을 바로 보여준다.

## 패키지 레이아웃

```text
com.dabom.messaging.kafka
|- autoconfigure
|  |- KafkaConfig
|  `- KafkaErrorHandlerConfig
|- error
|  |- KafkaErrorAction
|  |- KafkaErrorCode
|  |- KafkaErrorDecision
|  |- KafkaExceptionClassifier
|  |- KafkaMessageDeserializationException
|  `- KafkaMessageProcessingException
|- event
|  |- KafkaEventMessageSupport
|  |- consumer
|  `- publisher
|  |- dto
|     |- EventEnvelope
|     |- notification
|     |- policy
|     `- usage
|- metrics
|  |- KafkaMetricTagSanitizer
|  |- KafkaMetrics
|  |- consumer
|  `- producer
`- support
   `- LogSanitizer
```

## 이렇게 나눈 이유

### `autoconfigure`

`KafkaConfig`, `KafkaErrorHandlerConfig`는 단순 유틸이 아니라 라이브러리의 기본 실행 구성을 만든다.
그래서 일반적인 `config`보다 `autoconfigure`가 의도에 더 맞다.
이 패키지는 "이 라이브러리를 붙였을 때 기본으로 올라오는 런타임 wiring"을 담당한다.

### `event`

인터뷰 결과 `notification`, `usage`, `policy`는 예시 DTO가 아니라 조직 표준 이벤트 계약으로 유지하기로 했다.
그래서 `EventEnvelope`와 각 payload 타입을 계속 라이브러리 안에 둔다.
`KafkaEventMessageSupport`도 이 패키지에 두었다. 역할이 "이벤트 메시지 해석/변환"이기 때문이다.
추가로 publisher 기본 구현과 consumer 확장 인터페이스도 같은 이유로 `event` 아래에 두었다.

### `error`

기존 `kafka.error`는 루트 패키지 자체에 이미 `kafka`가 들어가 있으므로 중복이었다.
`error`로 평탄화해서 import 경로를 짧게 하고 책임도 더 분명하게 만들었다.

### `metrics`

메트릭은 기본 운영 모델의 일부로 본다.
Producer/Consumer 훅은 각각 `producer`, `consumer` 하위 패키지에 두고, 공통 메트릭 유틸은 루트에 둬서 역할을 구분했다.

### `support`

`LogSanitizer`는 이벤트 계약이나 에러 정책이 아니라 인프라 보조 기능이다.
기존 `global.util`은 범위가 너무 넓어서, 이번에 `support`로 좁혔다.

## 통합 및 제거 내용

- `common.TimeConstants` 제거
  - 실제 사용처가 consumer metrics interceptor 하나뿐이었다.
  - 그래서 상수는 해당 클래스 내부로 옮겼다.
- `event.dto.ExampleCreatedEvent` 제거
  - 표준 계약이 아니라 예시 성격의 잔재였다.
- `tracing.KafkaTracingSupport` 제거
  - 현재 사용하지 않고 있었고, 인터뷰에서도 제거 가능하다고 정리됐다.

## 공개 API 가이드

아직 강한 강제 경계까지 두지는 않았지만, 현재 기준으로는 아래처럼 보는 것이 자연스럽다.

서비스 코드에서 직접 import해도 되는 패키지:

- `com.dabom.messaging.kafka.event`
- `com.dabom.messaging.kafka.event.dto`
- `com.dabom.messaging.kafka.event.consumer`
- `com.dabom.messaging.kafka.event.publisher`
- `com.dabom.messaging.kafka.error`

주로 Spring wiring을 통해 간접적으로 쓰게 되는 패키지:

- `com.dabom.messaging.kafka.autoconfigure`
- `com.dabom.messaging.kafka.metrics`
- `com.dabom.messaging.kafka.support`

이 기준은 운영 가이드에 가깝고, 나중에 필요하면 public/internal 경계를 더 강하게 나눌 수 있다.

## 마이그레이션 영향

이번 변경은 `com.project.global...` 에서 `com.dabom.messaging.kafka...` 로 바뀌는 브레이킹 체인지다.

소비 서비스에서는 다음 항목을 바꿔야 한다.

- import 경로
- component scan base package
- 직접 참조하던 설정 클래스 경로

다만 런타임 동작 원칙 자체는 유지한다.

- 메시지 포맷은 계속 JSON 문자열 기반 `EventEnvelope`
- payload subtype 등록은 계속 중앙 관리
- metrics와 error handling은 계속 기본 활성화
