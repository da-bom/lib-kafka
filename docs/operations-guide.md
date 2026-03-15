# Operations Guide

이 문서는 `lib-kafka v1.0.0`의 운영 관점 기준을 정리한다.

## 1. producer 성공 기준

현재 라이브러리에서 publish 성공은 `ProducerListener.onSuccess(...)` 기준이다.

운영 해석:

- broker ack 기준 성공
- downstream consumer 도달까지는 보장하지 않음

기본 producer 설정:

- `acks=all`
- `enable.idempotence=true`

주의:

- 서비스 API 경계에서 동기식 성공을 보장하지는 않는다.
- DB 반영과 Kafka publish의 원자성은 여전히 Outbox 같은 상위 설계 책임이다.

## 2. 공통 메트릭

현재 제공 메트릭:

- `kafka.producer.send.success.count`
- `kafka.producer.send.error.count`
- `kafka.consumer.success.count`
- `kafka.consumer.invalid_event.count`
- `kafka.consumer.retryable_error.count`
- `kafka.consumer.dlt.count`
- `kafka.consumer.processing.time`
- `kafka.consumer.producer_to_consumer.latency`

기본 태그:

- `topic`
- `group`
- `eventType`

retry 메트릭은 실제 error handler가 retry로 분류한 경우에만 증가한다.

## 3. DLT 기준

- DLT topic 이름: `{원본토픽}.DLT`
- DLT 헤더:
  - `x-error-code`
  - `x-error-action`

DLT로 가는 대표 경우:

- 역직렬화 실패
- non-retryable processing failure
- 분류되지 않은 예외

## 4. 운영 체크포인트

- `kafka.consumer.dlt.count`가 0이 아닌지
- 특정 topic의 `retryable_error`가 급증하는지
- producer success/error 비율이 비정상인지
- consumer processing latency가 장기적으로 증가하는지

## 5. Outbox 구조와의 경계

`lib-kafka`가 제공하지 않는 것:

- Outbox polling
- Outbox 상태 전이
- Outbox backlog 메트릭
- Outbox retry 스케줄링

즉 `lib-kafka`는 Kafka 런타임 표준화 계층이고, Outbox 운영은 서비스 또는 배치 책임이다.

## 6. 운영 권장 규칙

- publish 성공 기준은 broker ack 기준으로 문서화한다.
- consumer retry와 Outbox retry를 별개 계층으로 관측한다.
- retryable / non-retryable 예외를 서비스 코드에서 명시적으로 구분한다.
- 브레이킹 변경은 새 메이저 태그로 배포한다.
