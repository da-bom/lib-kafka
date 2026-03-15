# Error Handling Policy

이 문서는 `lib-kafka v1.0.0`의 consumer 공통 에러 처리 정책을 설명한다.

구현 클래스:

- `KafkaExceptionClassifier`
- `KafkaErrorHandlerConfig`

## 1. 처리 액션

### RETRY

일시 장애로 판단하고 재시도한다.

### IGNORE

처리 가치가 없는 이벤트로 판단하고 skip한다.

### DLQ

현재 consumer 흐름에서 처리할 수 없다고 보고 DLT로 보낸다.

## 2. 현재 분류 규칙

| Exception | Action | Code |
| --- | --- | --- |
| `KafkaMessageDeserializationException` | `DLQ` | `DESERIALIZATION_FAILED` |
| `DeserializationException` | `DLQ` | `DESERIALIZATION_FAILED` |
| `SerializationException` | `DLQ` | `DESERIALIZATION_FAILED` |
| `IllegalArgumentException` | `IGNORE` | `INVALID_EVENT` |
| `TimeoutException` | `RETRY` | `TRANSIENT_NETWORK` |
| `SocketTimeoutException` | `RETRY` | `TRANSIENT_NETWORK` |
| `RetriableException` | `RETRY` | `TRANSIENT_NETWORK` |
| `TransientDataAccessException` | `RETRY` | `TRANSIENT_DB` |
| `KafkaMessageProcessingException` | `RETRY` | `PROCESSING_FAILED` |
| `NonRetryableKafkaMessageProcessingException` | `DLQ` | `NON_RETRYABLE_PROCESSING_FAILED` |
| 기타 예외 | `DLQ` | `UNKNOWN` |

classifier는 cause chain까지 확인한다.

## 3. DefaultErrorHandler 동작

- retry는 exponential backoff 사용
- recovered record는 commit
- DLT 토픽명은 `{원본토픽}.DLT`
- DLT 헤더
  - `x-error-code`
  - `x-error-action`

기본 retry 설정:

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

## 4. 개발 규칙

### 역직렬화 실패

payload 파싱이나 역직렬화 실패는 `KafkaMessageDeserializationException`으로 다루는 것이 좋다.

### 재시도 가능한 처리 실패

일시 장애, 외부 시스템 재시도 가치가 있는 실패는 `KafkaMessageProcessingException`으로 감싼다.

### 재시도 불필요한 처리 실패

재시도해도 성공하지 않을 비즈니스 실패는 `NonRetryableKafkaMessageProcessingException`을 사용한다.

예:

- 필수 도메인 데이터가 영구적으로 없음
- 상태 전이가 이미 종료됨
- 이미 폐기된 입력을 다시 처리하려는 경우

### 검증 실패

계약 위반이나 입력 검증 실패는 `IllegalArgumentException`을 사용하면 현재 정책상 `IGNORE`다.

## 5. 운영 해석

- `IGNORE`: 입력 자체가 잘못됐거나 skip가 맞는 경우
- `RETRY`: 일시 장애
- `DLQ`: poison message 또는 현재 처리 경로에서 복구 불가

즉 poison message가 무한 retry로 쌓이지 않도록 `IGNORE`와 `DLQ` 경로를 분리한다.

## 6. 함께 봐야 하는 문서

- 사용 방법: [usage-guide.md](./usage-guide.md)
- 운영 기준: [operations-guide.md](./operations-guide.md)
