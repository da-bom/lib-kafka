# 사용 가이드

이 문서는 `lib-kafka`를 서비스에 적용할 때 필요한 기본 사용법을 정리한다.
에러 처리 기준은 [error-handling-policy.md](./error-handling-policy.md)를 따른다.

## 1. 기본 정보

- Group: `com.github.da-bom`
- Artifact: `lib-kafka`
- Java: 21
- Gradle Wrapper: 8.10.2
- 루트 패키지: `com.dabom.messaging.kafka`

버전은 항상 고정 태그를 사용한다.

예시:

```gradle
implementation 'com.github.da-bom:lib-kafka:v0.4.0'
```

## 2. 설치

### 2.1 JitPack 저장소 추가

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

### 2.2 의존성 추가

```gradle
dependencies {
    implementation 'com.github.da-bom:lib-kafka:v0.4.0'
}
```

## 3. 스프링에서 인식시키기

라이브러리 빈은 `com.dabom.messaging.kafka` 하위에 있다.
애플리케이션 루트 패키지가 다르면 component scan 범위를 추가해야 한다.

```java
@SpringBootApplication(scanBasePackages = {
    "com.myservice",
    "com.dabom.messaging.kafka"
})
public class Application {}
```

## 4. 자동 설정으로 제공되는 것

### `autoconfigure.KafkaConfig`

다음 빈을 기본으로 제공한다.

- `ProducerFactory<String, String>`
- `KafkaTemplate<String, String>`
- `ConsumerFactory<String, String>`
- `ConcurrentKafkaListenerContainerFactory<String, String>`

특징:

- producer/consumer 모두 문자열 payload를 기준으로 동작한다.
- listener container에는 metrics interceptor와 공통 error handler가 연결된다.
- `KafkaTemplate`에는 producer metrics listener가 연결된다.

기본 프로퍼티 예시:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

### `autoconfigure.KafkaErrorHandlerConfig`

다음 역할을 담당한다.

- 예외를 `RETRY`, `IGNORE`, `DLQ`로 분류
- DLT 발행
- 재시도 backoff 적용
- invalid/retry/dlt 메트릭 집계

기본 재시도 관련 프로퍼티:

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

## 5. 이벤트 계약 사용법

### `EventEnvelope<T>`

이 라이브러리의 기본 메시지 포맷은 `EventEnvelope<T>`다.

위치:

- `com.dabom.messaging.kafka.event.dto.EventEnvelope`

주요 필드:

- `eventId`
- `eventType`
- `subType`
- `timestamp`
- `payload`

생성 예시:

```java
EventEnvelope<UsageRealtimePayload> envelope =
        EventEnvelope.of("USAGE_REALTIME", payload);
```

subType이 필요한 경우:

```java
EventEnvelope<NotificationPayload> envelope =
        EventEnvelope.of("NOTIFICATION", "QUOTA_UPDATED", payload);
```

### 내장 payload 타입

현재 중앙 관리하는 payload 카테고리는 다음과 같다.

- `event.dto.notification`
- `event.dto.policy`
- `event.dto.usage`

이들은 예시 DTO가 아니라 조직 공통 이벤트 계약으로 취급한다.

## 6. `KafkaEventMessageSupport` 사용법

위치:

- `com.dabom.messaging.kafka.event.KafkaEventMessageSupport`

제공 기능:

- raw JSON을 `JsonNode`로 파싱
- `eventType` 추출
- `EventEnvelope<T>`로 변환
- `eventType` 기준 소비 분기
- `EventEnvelope<?>` 직렬화

### 소비 예시

```java
@KafkaListener(topics = "usage-events", groupId = "usage-consumer")
public void consume(ConsumerRecord<String, String> record) {
    kafkaEventMessageSupport.consumeByEventType(
            record,
            "USAGE_REALTIME",
            new TypeReference<EventEnvelope<UsageRealtimePayload>>() {},
            (envelope, key) -> usageService.handle(envelope.payload(), key)
    );
}
```

### 발행 예시

```java
EventEnvelope<NotificationPayload> envelope =
        EventEnvelope.of("NOTIFICATION", "QUOTA_UPDATED", payload);

String message = kafkaEventMessageSupport.serialize(envelope);
kafkaTemplate.send("notification-events", message);
```

## 7. `KafkaEventPublisher` 사용법

위치:

- `com.dabom.messaging.kafka.event.publisher.KafkaEventPublisher`

기본 구현:

- `com.dabom.messaging.kafka.event.publisher.DefaultKafkaEventPublisher`

이 인터페이스를 쓰면 서비스 코드에서 `EventEnvelope` 생성, 직렬화, `KafkaTemplate` 전송을 한 곳으로 모을 수 있다.

### 실제 서비스 예시

```java
@Service
@RequiredArgsConstructor
public class NotificationEventPublishService {
    private static final String TOPIC = "notification-events";
    private static final String EVENT_TYPE = "NOTIFICATION";

    private final KafkaEventPublisher kafkaEventPublisher;

    public void publish(NotificationPayload payload) {
        String subType =
                switch (payload) {
                    case QuotaUpdatedPayload ignored -> "QUOTA_UPDATED";
                    case CustomerBlockedPayload ignored -> "CUSTOMER_BLOCKED";
                    case ThresholdAlertPayload ignored -> "THRESHOLD_ALERT";
                };

        kafkaEventPublisher.publish(TOPIC, EVENT_TYPE, subType, payload);
    }
}
```

이미 만들어진 envelope를 그대로 보내는 방식도 가능하다.

```java
@Service
@RequiredArgsConstructor
public class UsageEventPublishService {
    private static final String TOPIC = "usage-events";

    private final KafkaEventPublisher kafkaEventPublisher;

    public void publish(UsageRealtimePayload payload) {
        EventEnvelope<UsageRealtimePayload> envelope =
                EventEnvelope.of("USAGE_REALTIME", payload);

        kafkaEventPublisher.publish(TOPIC, envelope);
    }
}
```

## 8. `KafkaEventConsumer<T>` 사용법

위치:

- `com.dabom.messaging.kafka.event.consumer.KafkaEventConsumer`

이 인터페이스는 서비스가 구현하는 consumer 계약이다.
각 consumer는 자신이 처리할 `eventType`, `TypeReference`, 실제 처리 로직을 정의하면 된다.

### 실제 서비스 예시 1: 이벤트 처리 구현체

```java
@Component
@RequiredArgsConstructor
public class UsageRealtimeConsumer implements KafkaEventConsumer<UsageRealtimePayload> {
    private final UsageRealtimeService usageRealtimeService;

    @Override
    public String eventType() {
        return "USAGE_REALTIME";
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

### 실제 서비스 예시 2: listener adapter

listener는 얇게 유지하고 실제 처리 책임은 consumer 구현체에 위임하는 방식이 자연스럽다.

```java
@Component
@RequiredArgsConstructor
public class UsageRealtimeKafkaListener {
    private final KafkaEventMessageSupport kafkaEventMessageSupport;
    private final UsageRealtimeConsumer usageRealtimeConsumer;

    @KafkaListener(topics = "usage-events", groupId = "usage-consumer")
    public void consume(ConsumerRecord<String, String> record) {
        usageRealtimeConsumer.consume(record, kafkaEventMessageSupport);
    }
}
```

이 패턴의 장점은 다음과 같다.

- listener는 Kafka adapter 역할만 담당한다.
- 실제 eventType 계약과 처리 로직은 `KafkaEventConsumer<T>` 구현체에 모인다.
- 테스트할 때 `handle(...)` 단위와 listener wiring 단위를 분리할 수 있다.

## 9. 메트릭

### 제공 구성

- `KafkaMetrics`
- `KafkaMetricsRecordInterceptor`
- `KafkaMetricsProducerListener`
- `KafkaMetricTagSanitizer`

### 대표 메트릭

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

## 10. 에러 관련 타입

### 위치

- `com.dabom.messaging.kafka.error`

### 주요 타입

- `KafkaExceptionClassifier`
- `KafkaErrorAction`
- `KafkaErrorCode`
- `KafkaErrorDecision`
- `KafkaMessageDeserializationException`
- `KafkaMessageProcessingException`

권장 방식:

- 파싱/역직렬화 실패는 `KafkaMessageDeserializationException`
- 비즈니스 처리 실패는 `KafkaMessageProcessingException`

## 11. 현재 제외된 기능

이 버전에서는 tracing 지원이 라이브러리에 포함되지 않는다.

제거된 항목:

- `KafkaTracingSupport`

즉, OpenTelemetry 전파는 각 서비스가 별도로 구성해야 한다.

## 12. 운영 권장사항

- 태그는 항상 고정 버전을 사용한다.
- 기존 태그를 덮어쓰지 않는다.
- 브레이킹 체인지는 새 버전으로 올린다.
- 배포 전 최소 `./gradlew test`는 실행한다.

## 13. 서비스 전용 Kafka 설정 확장

서비스마다 Kafka 설정을 조금씩 다르게 가져가야 할 수도 있다.
현재 구조에서도 가능하지만, 공통 빈을 무작정 덮어쓰기보다 "서비스 전용 빈을 별도 이름으로 추가"하는 방식을 권장한다.

### 권장 방식

- 라이브러리의 기본 `KafkaTemplate`, `ConsumerFactory`, `CommonErrorHandler`는 공통 표준으로 유지
- 서비스 고유 요구사항은 별도 이름의 Bean으로 추가
- 필요한 listener나 publisher에서 명시적으로 그 Bean을 사용

### 예시 1: 서비스 전용 listener container factory

특정 consumer만 concurrency를 다르게 주고 싶을 때:

```java
@Configuration
public class UsageConsumerKafkaConfig {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> usageKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler kafkaCommonErrorHandler,
            KafkaMetricsRecordInterceptor recordInterceptor) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaCommonErrorHandler);
        factory.setRecordInterceptor(recordInterceptor);
        factory.setConcurrency(5);
        return factory;
    }
}
```

사용 예시:

```java
@KafkaListener(
        topics = "usage-events",
        groupId = "usage-consumer",
        containerFactory = "usageKafkaListenerContainerFactory")
public void consume(ConsumerRecord<String, String> record) {
    usageRealtimeConsumer.consume(record, kafkaEventMessageSupport);
}
```

### 예시 2: 서비스 전용 KafkaTemplate

특정 발행 흐름만 별도 producer 설정을 쓰고 싶을 때:

```java
@Configuration
public class NotificationProducerKafkaConfig {
    @Bean
    public KafkaTemplate<String, String> notificationKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        kafkaTemplate.setObservationEnabled(true);
        return kafkaTemplate;
    }
}
```

이 경우 서비스 전용 publisher 구현체에서 해당 Bean을 주입받아 사용할 수 있다.

### 피하는 것이 좋은 방식

- 라이브러리가 이미 등록한 공통 Bean과 같은 이름으로 다시 등록
- 이유 없이 기본 `KafkaTemplate`, `CommonErrorHandler`를 전부 덮어쓰기
- 서비스 고유 설정과 공통 정책을 한 클래스에 섞어 넣기

### 판단 기준

아래는 서비스에서 따로 가져가도 괜찮은 편이다.

- 특정 listener의 concurrency
- 특정 listener의 container factory
- 특정 producer용 topic routing
- 서비스 고유 interceptor

반대로 아래는 가능하면 공통 표준으로 유지하는 편이 낫다.

- 기본 envelope 직렬화 규칙
- 공통 에러 분류 체계
- 공통 DLT 정책
- 공통 메트릭 이름과 태그 정책

## 14. 트러블슈팅

### 빈이 잡히지 않을 때

- `scanBasePackages`에 `com.dabom.messaging.kafka`가 포함되어 있는지 확인한다.

### import가 되지 않을 때

- Gradle refresh를 다시 수행한다.
- JitPack 태그명이 정확한지 확인한다.

### Kafka 설정 빈이 충돌할 때

- 서비스 내부에 동일한 타입의 Kafka 설정 빈이 중복 선언되어 있는지 확인한다.
- 특히 `KafkaTemplate`, `ConsumerFactory`, `CommonErrorHandler` 중복 여부를 먼저 본다.

