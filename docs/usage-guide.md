# Usage Guide

이 문서는 `lib-kafka v1.0.0`을 서비스에서 사용하는 기본 방법을 정리한다.

## 1. 설치

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.da-bom:lib-kafka:v1.0.0'
}
```

애플리케이션 루트 패키지가 다르면 component scan 범위를 추가한다.

```java
@SpringBootApplication(scanBasePackages = {
    "com.myservice",
    "com.dabom.messaging.kafka"
})
public class Application {}
```

## 2. 자동 설정으로 제공되는 것

- `ProducerFactory<String, String>`
- `KafkaTemplate<String, String>`
- `ConsumerFactory<String, String>`
- `ConcurrentKafkaListenerContainerFactory<String, String>`
- `CommonErrorHandler`

producer 기본값:

- `acks=all`
- `enable.idempotence=true`

## 3. 공통 계약 상수

문자열 리터럴 대신 아래 상수를 사용한다.

- `KafkaTopics`
- `KafkaEventTypes`
- `KafkaConsumerGroups`
- `NotificationType`

예:

```java
kafkaEventPublisher.publish(
        KafkaTopics.USAGE_REALTIME,
        KafkaEventTypes.USAGE_REALTIME,
        payload);
```

## 4. EventEnvelope 사용

기본 메시지 포맷은 `EventEnvelope<T>`다.

필드:

- `eventId`
- `eventType`
- `timestamp`
- `payload`

생성 예:

```java
EventEnvelope<UsageRealtimePayload> envelope =
        EventEnvelope.of(KafkaEventTypes.USAGE_REALTIME, payload);
```

## 5. NotificationPayload 사용

notification은 단일 payload 구조를 사용한다.

```java
NotificationPayload payload = new NotificationPayload(
        familyId,
        customerId,
        NotificationType.THRESHOLD_ALERT,
        "데이터 잔여량 알림",
        "잔여량이 10% 남았습니다.",
        Map.of("thresholdPercent", 10, "remainingMb", 512));
```

helper 사용:

```java
EventEnvelope<NotificationPayload> envelope =
        NotificationEventSupport.toEnvelope(payload);
```

## 6. KafkaEventPublisher 사용

단순 이벤트 발행:

```java
@Service
@RequiredArgsConstructor
public class UsageRealtimeEventPublishService {
    private final KafkaEventPublisher kafkaEventPublisher;

    public void publish(UsageRealtimePayload payload) {
        kafkaEventPublisher.publish(
                KafkaTopics.USAGE_REALTIME,
                KafkaEventTypes.USAGE_REALTIME,
                payload);
    }
}
```

notification 발행:

```java
@Service
@RequiredArgsConstructor
public class NotificationEventPublishService {
    private final KafkaEventPublisher kafkaEventPublisher;

    public void publish(NotificationPayload payload) {
        kafkaEventPublisher.publish(
                KafkaTopics.NOTIFICATION,
                NotificationEventSupport.toEnvelope(payload));
    }
}
```

## 7. KafkaEventMessageSupport 사용

`KafkaEventMessageSupport`는 다음 역할을 담당한다.

- raw JSON 파싱
- `eventType` 추출
- `EventEnvelope<T>` 변환
- `eventType` 기준 소비 분기
- envelope 직렬화

소비 예:

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

## 8. KafkaEventConsumer 사용

listener는 얇게 유지하고 실제 처리 책임은 `KafkaEventConsumer<T>` 구현체에 두는 것을 권장한다.

```java
@Component
@RequiredArgsConstructor
public class UsageRealtimeConsumer implements KafkaEventConsumer<UsageRealtimePayload> {
    private final UsageRealtimeService usageRealtimeService;

    @Override
    public String eventType() {
        return KafkaEventTypes.USAGE_REALTIME;
    }

    @Override
    public TypeReference<EventEnvelope<UsageRealtimePayload>> typeReference() {
        return new TypeReference<>() {};
    }

    @Override
    public void handle(EventEnvelope<UsageRealtimePayload> envelope, String recordKey) {
        usageRealtimeService.handle(envelope.payload(), recordKey);
    }
}
```

## 9. 예외 타입 사용 규칙

- 파싱/역직렬화 실패: `KafkaMessageDeserializationException`
- 재시도 가능한 처리 실패: `KafkaMessageProcessingException`
- 재시도 가치가 없는 처리 실패: `NonRetryableKafkaMessageProcessingException`
- 계약 위반이나 검증 실패: `IllegalArgumentException`

에러 처리 상세는 [error-handling-policy.md](./error-handling-policy.md)를 따른다.

## 10. 검증 체크리스트

```bash
./gradlew clean compileJava
./gradlew clean test
./gradlew clean publishToMavenLocal -x test
```

추가 확인:

- producer success/error 메트릭 증가 여부
- consumer success/retry/dlt 메트릭 증가 여부
- DLT 발행 시 헤더 포함 여부
