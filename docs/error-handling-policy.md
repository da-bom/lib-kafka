# 에러 처리 정책

이 문서는 `lib-kafka`가 Kafka 소비 중 발생한 예외를 어떻게 분류하고 처리하는지 정리한다.
현재 기준 구현체는 다음 두 클래스다.

- `com.dabom.messaging.kafka.error.KafkaExceptionClassifier`
- `com.dabom.messaging.kafka.autoconfigure.KafkaErrorHandlerConfig`

## 1. 목적

- 서비스마다 다른 재시도 정책을 하나의 기준으로 통일한다.
- 같은 종류의 예외에 대해 같은 처리 결과를 보장한다.
- 운영자가 retry, ignore, dlt 동작을 예측 가능하게 만든다.

## 2. 적용 범위

이 정책은 기본적으로 consumer 처리 흐름에 적용된다.

포함 대상:

- listener에서 발생한 예외
- payload 파싱/역직렬화 실패
- 비즈니스 처리 중 발생한 공통 예외
- `CommonErrorHandler`가 받아 처리하는 예외

## 3. 처리 액션 정의

### `RETRY`

일시 장애로 판단하고 재시도한다.

예:

- 네트워크 timeout
- Kafka broker 일시 오류
- DB transient 예외
- 처리 단계의 재시도 가능 예외

### `IGNORE`

이벤트를 더 처리할 가치가 없다고 보고 skip한다.

예:

- 입력값 자체가 계약에 맞지 않음
- 잘못된 eventType 조합
- 검증 실패

### `DLQ`

현재 consumer 흐름에서는 처리할 수 없다고 보고 DLT로 보낸다.

예:

- 역직렬화 실패
- 분류되지 않은 예외

## 4. 현재 분류 규칙

아래 규칙은 현재 `KafkaExceptionClassifier` 구현을 그대로 반영한다.

### 4.1 `KafkaMessageDeserializationException`

- Action: `DLQ`
- Code: `DESERIALIZATION_FAILED`

### 4.2 `DeserializationException`

- Action: `DLQ`
- Code: `DESERIALIZATION_FAILED`

### 4.3 `SerializationException`

- Action: `DLQ`
- Code: `DESERIALIZATION_FAILED`

### 4.4 `IllegalArgumentException`

- Action: `IGNORE`
- Code: `INVALID_EVENT`

### 4.5 `TimeoutException`

- Action: `RETRY`
- Code: `TRANSIENT_NETWORK`

### 4.6 `SocketTimeoutException`

- Action: `RETRY`
- Code: `TRANSIENT_NETWORK`

### 4.7 `RetriableException`

- Action: `RETRY`
- Code: `TRANSIENT_NETWORK`

### 4.8 `TransientDataAccessException`

- Action: `RETRY`
- Code: `TRANSIENT_DB`

### 4.9 `KafkaMessageProcessingException`

- Action: `RETRY`
- Code: `PROCESSING_FAILED`

### 4.10 그 외 모든 예외

- Action: `DLQ`
- Code: `UNKNOWN`

## 5. 분류 기준의 특징

- 예외는 단일 타입만 보는 것이 아니라 cause chain까지 확인한다.
- 즉 wrapper 예외 안에 실제 원인 예외가 감싸져 있어도 분류 가능하다.
- 소비 코드에서 정책을 의도대로 타게 하려면 적절한 예외 타입으로 감싸는 것이 중요하다.

## 6. `KafkaErrorHandlerConfig` 기본 동작

### DLT 발행 규칙

- 기본 DLT topic 이름은 `{원본토픽}.DLT`
- partition은 원본 record의 partition을 따른다

### DLT 헤더

DLT로 보낼 때 아래 헤더를 추가한다.

- `x-error-code`
- `x-error-action`

### `IGNORE` 처리

- DLT로 보내지 않는다
- `kafka.consumer.invalid_event.count` 메트릭을 증가시킨다
- 경고 로그를 남긴다

### `RETRY` 처리

- exponential backoff를 사용한다
- retry 발생 시 `kafka.consumer.retryable_error.count`를 증가시킨다

### `DLQ` 처리

- DLT로 발행한다
- `kafka.consumer.dlt.count`를 증가시킨다

## 7. 재시도 설정

기본값:

- `app.kafka.error-handler.retry.max-attempts=2`
- `app.kafka.error-handler.retry.initial-interval-ms=1000`
- `app.kafka.error-handler.retry.multiplier=2.0`
- `app.kafka.error-handler.retry.max-interval-ms=10000`

예시:

```yaml
app:
  kafka:
    error-handler:
      retry:
        max-attempts: 2
        initial-interval-ms: 1000
        multiplier: 2.0
        max-interval-ms: 10000
```

## 8. 개발 규칙

### 파싱 실패

payload 파싱이나 역직렬화 실패는 `KafkaMessageDeserializationException`으로 다루는 것이 좋다.

### 처리 실패

비즈니스 로직이나 후처리 실패는 `KafkaMessageProcessingException`으로 감싸는 것이 좋다.

### 검증 실패

계약 위반이나 입력 검증 실패는 `IllegalArgumentException`을 사용하면 현재 정책상 `IGNORE`로 분류된다.

## 9. 운영 확인 포인트

반드시 확인할 메트릭:

- `kafka.consumer.invalid_event.count`
- `kafka.consumer.retryable_error.count`
- `kafka.consumer.dlt.count`

DLT 소비 서비스가 있다면 다음 헤더를 우선 확인한다.

- `x-error-code`
- `x-error-action`

새로운 예외 패턴이 반복되면 classifier 규칙을 추가할지 검토해야 한다.

## 10. 변경 관리

에러 분류 규칙 변경은 단순 내부 수정이 아니라 동작 정책 변경으로 본다.

변경 시 권장 절차:

1. 문서를 먼저 갱신한다.
2. 관련 테스트를 추가하거나 수정한다.
3. 새 버전을 발행한다.
4. 기존 태그는 재사용하지 않는다.
