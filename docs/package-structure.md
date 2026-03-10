# 패키지 구조

## 결정 요약

이 라이브러리는 DABOM 서비스 전반에서 공통으로 쓰는 메시징 표준 모듈이다.
역할은 다음과 같다.

- Spring Kafka 기본 설정 제공
- 조직 표준 이벤트 계약 제공
- topic / eventType / consumer group 계약 상수 제공
- 공통 에러 처리 규칙 제공
- 공통 메트릭 제공

사용하지 않던 tracing 지원은 제거했다.

## 루트 네임스페이스

루트 패키지는 `com.dabom.messaging.kafka` 이다.

## 패키지 레이아웃

```text
com.dabom.messaging.kafka
|- contract
|  |- KafkaConsumerGroups
|  |- KafkaEventTypes
|  `- KafkaTopics
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
|  |- dto
|  |  |- EventEnvelope
|  |  |- notification
|  |  |  |- NotificationEventSupport
|  |  |  |- NotificationPayload
|  |  |  `- NotificationSubTypes
|  |  |- policy
|  |  `- usage
|  `- publisher
|- metrics
|  |- KafkaMetricTagSanitizer
|  |- KafkaMetrics
|  |- consumer
|  `- producer
`- support
   `- LogSanitizer
```

## 이렇게 나눈 이유

### `contract`

topic, eventType, consumer group은 특정 DTO보다 상위에 있는 메시징 계약이다.
서비스 코드가 문자열 리터럴 대신 라이브러리 계약 상수를 직접 쓰도록 하기 위해 별도 패키지로 분리했다.

### `event`

`notification`, `usage`, `policy`는 조직 표준 이벤트 계약이다.
그래서 DTO와 이벤트 helper, publisher/consumer 확장 포인트를 같은 축에서 관리한다.

### `event.dto.notification`

notification은 subtype 개념이 있는 유일한 이벤트 계층이다.
그래서 subtype 상수와 subtype 계산 helper를 notification 패키지 안에 두었다.
이렇게 하면 `NotificationPayload`와 관련 규칙이 한 위치에 모인다.

### `autoconfigure`

런타임 기본 Kafka wiring을 담당한다.
단순 샘플 설정이 아니라 라이브러리의 기본 실행 구성이므로 `config`보다 `autoconfigure`가 더 맞다.

### `error`

공통 예외 분류와 처리 정책을 담는다.
루트 패키지에 이미 `kafka`가 있으므로 `kafka.error` 같은 중복 계층은 제거했다.

### `metrics`

운영 메트릭과 관련된 컴포넌트를 둔다.
producer / consumer 훅은 하위 패키지로 분리하고, 공통 메트릭 유틸은 루트에 둔다.

### `support`

로깅 보조 같은 인프라 지원 기능을 둔다.

## 공개 API 가이드

서비스 코드에서 직접 import해도 되는 패키지:

- `com.dabom.messaging.kafka.contract`
- `com.dabom.messaging.kafka.event`
- `com.dabom.messaging.kafka.event.dto`
- `com.dabom.messaging.kafka.event.consumer`
- `com.dabom.messaging.kafka.event.publisher`
- `com.dabom.messaging.kafka.error`

주로 Spring wiring을 통해 간접적으로 쓰는 패키지:

- `com.dabom.messaging.kafka.autoconfigure`
- `com.dabom.messaging.kafka.metrics`
- `com.dabom.messaging.kafka.support`

## 마이그레이션 영향

이번 변경은 다음을 포함한다.

- `com.project.global...` -> `com.dabom.messaging.kafka...`
- tracing 제거
- topic / eventType / consumer group / notification subtype 계약 상수 추가

따라서 서비스에서는 import 경로뿐 아니라 문자열 계약도 상수 기반으로 치환하는 것이 권장된다.
