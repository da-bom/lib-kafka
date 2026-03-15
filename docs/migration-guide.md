# Migration Guide

이 문서는 기존 서비스가 자체 Kafka 설정이나 유틸을 사용하고 있을 때
`lib-kafka v1.0.0`으로 옮기는 절차를 정리한다.

## 1. 전환 대상

아래 중 하나라도 해당하면 전환 대상이다.

- Kafka 설정 클래스가 서비스마다 중복됨
- retry / DLT / ignore 정책이 서비스마다 다름
- 이벤트 envelope 파싱 코드가 반복됨
- topic / eventType / consumer group 문자열이 흩어져 있음
- producer / consumer 메트릭을 공통 기준으로 맞추고 싶음

## 2. 이번 버전의 핵심 변경

- notification 계약이 `NotificationType` + 단일 `NotificationPayload`로 통합됨
- `EventEnvelope.subType` 제거
- `usage-persist` 계약 제거
- producer 기본값이 `acks=all`, `enable.idempotence=true`로 강화됨
- non-retryable processing 예외 타입 추가

## 3. 전환 절차

### Step 1. 의존성 교체

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.da-bom:lib-kafka:v1.0.0'
}
```

### Step 2. component scan 확인

```java
@SpringBootApplication(scanBasePackages = {
    "com.myservice",
    "com.dabom.messaging.kafka"
})
public class Application {}
```

### Step 3. 문자열 계약 제거

서비스 코드에서 직접 쓰던 문자열을 공통 상수로 치환한다.

- topic -> `KafkaTopics`
- eventType -> `KafkaEventTypes`
- consumer group -> `KafkaConsumerGroups`
- notification type -> `NotificationType`

### Step 4. 서비스 내부 Kafka 설정 정리

우선적으로 확인할 대상:

- `ProducerFactory`
- `KafkaTemplate`
- `ConsumerFactory`
- `ConcurrentKafkaListenerContainerFactory`
- `CommonErrorHandler`

정리 원칙:

- 공통 설정은 라이브러리 것을 사용
- 서비스 고유 설정만 별도 Bean으로 유지

### Step 5. 소비 코드 전환

직접 JSON 파싱과 문자열 비교를 제거하고 `KafkaEventMessageSupport` 기반으로 옮긴다.

```java
@KafkaListener(
        topics = KafkaTopics.USAGE_EVENTS,
        groupId = KafkaConsumerGroups.DABOM_API_CORE_REALTIME)
public void consume(ConsumerRecord<String, String> record) {
    kafkaEventMessageSupport.consumeByEventType(
            record,
            KafkaEventTypes.USAGE_REALTIME,
            new TypeReference<EventEnvelope<UsageRealtimePayload>>() {},
            (envelope, key) -> usageService.handle(envelope.payload(), key)
    );
}
```

### Step 6. notification 구조 전환

구형 subtype 기반 DTO를 쓰고 있다면 단일 `NotificationPayload`로 옮긴다.

```java
NotificationPayload payload = new NotificationPayload(
        familyId,
        customerId,
        NotificationType.THRESHOLD_ALERT,
        "데이터 잔여량 알림",
        "잔여량이 10% 남았습니다.",
        Map.of("thresholdPercent", 10, "remainingMb", 512));
```

```java
kafkaEventPublisher.publish(
        KafkaTopics.NOTIFICATION,
        NotificationEventSupport.toEnvelope(payload));
```

### Step 7. 제거된 계약 정리

다음은 더 이상 사용하지 않는다.

- `KafkaTopics.USAGE_PERSIST`
- `KafkaEventTypes.USAGE_PERSIST`
- `UsagePersistPayload`
- persistence consumer group

### Step 8. 예외 타입 정리

- retryable failure -> `KafkaMessageProcessingException`
- non-retryable failure -> `NonRetryableKafkaMessageProcessingException`

## 4. 검증 항목

- 정상 이벤트 소비 성공
- validation 실패 시 ignore 동작
- transient 장애 시 retry 동작
- non-retryable failure 시 DLT 적재
- producer / consumer 메트릭 수집 확인

## 5. 롤백 전략

- 기존 태그는 덮어쓰지 않는다.
- 문제가 생기면 이전 태그로 되돌린다.
- 수정은 새 태그로 배포한다.
