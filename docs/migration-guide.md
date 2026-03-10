# 마이그레이션 가이드

이 문서는 기존 서비스가 자체 Kafka 설정이나 유틸 코드를 사용하고 있을 때
`lib-kafka` 기반 구조로 옮기는 절차를 설명한다.

## 1. 적용 대상

아래에 하나 이상 해당하면 전환 대상이다.

- 서비스마다 Kafka 설정 클래스가 중복된다.
- 재시도, DLT, 무시 정책이 서비스별로 제각각이다.
- 이벤트 엔벨로프 파싱 코드가 반복된다.
- producer/consumer 메트릭을 공통 기준으로 맞추고 싶다.
- topic / eventType / subType 문자열이 서비스마다 흩어져 있다.

## 2. 이번 버전에서 달라진 점

주요 변경:

- `com.project.global...` -> `com.dabom.messaging.kafka...`
- `config` -> `autoconfigure`
- `kafka.error` -> `error`
- tracing 지원 제거
- `contract` 패키지 추가
- `KafkaTopics`, `KafkaEventTypes`, `KafkaConsumerGroups` 추가
- `NotificationSubTypes`, `NotificationEventSupport` 추가

즉, import 경로 수정뿐 아니라 계약 상수 사용 방식으로의 전환도 포함된다.

## 3. 전환 전 체크리스트

- 현재 서비스에서 직접 선언한 Kafka 관련 `@Configuration` 목록 정리
- 현재 listener가 참조하는 `containerFactory` 이름 확인
- 현재 예외 처리 정책 정리
- 사용 중인 topic, groupId, eventType, subType 문자열 목록 확보

## 4. 전환 절차

### Step 1. 의존성 버전 교체

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.da-bom:lib-kafka:v0.5.0'
}
```

### Step 2. component scan 범위 수정

```java
@SpringBootApplication(scanBasePackages = {
    "com.myservice",
    "com.dabom.messaging.kafka"
})
public class Application {}
```

### Step 3. import 경로 변경

대표 예시:

- `com.project.global.event.KafkaEventMessageSupport`
  -> `com.dabom.messaging.kafka.event.KafkaEventMessageSupport`
- `com.project.global.event.dto.EventEnvelope`
  -> `com.dabom.messaging.kafka.event.dto.EventEnvelope`
- `com.project.global.config.KafkaConfig`
  -> `com.dabom.messaging.kafka.autoconfigure.KafkaConfig`
- `com.project.global.config.KafkaErrorHandlerConfig`
  -> `com.dabom.messaging.kafka.autoconfigure.KafkaErrorHandlerConfig`
- `com.project.global.kafka.error.KafkaExceptionClassifier`
  -> `com.dabom.messaging.kafka.error.KafkaExceptionClassifier`

문자열 상수 직접 사용도 아래 계약 상수로 전환하는 것이 좋다.

- `KafkaTopics`
- `KafkaEventTypes`
- `KafkaConsumerGroups`
- `NotificationSubTypes`

### Step 4. 서비스 내부 Kafka 설정 정리

라이브러리와 역할이 겹치는 설정은 제거하는 것이 좋다.

우선적으로 확인할 대상:

- `ProducerFactory`
- `KafkaTemplate`
- `ConsumerFactory`
- `ConcurrentKafkaListenerContainerFactory`
- `CommonErrorHandler`
- `DefaultErrorHandler`

정리 원칙:

- 공통 설정은 라이브러리 것을 사용한다.
- 서비스 고유의 라우팅, concurrency, 개별 listener factory만 남긴다.
- 동일 타입 Bean이 중복되면 충돌 위험이 크다.

### Step 5. 소비 코드 전환

기존 패턴:

- listener 안에서 직접 JSON 파싱
- `eventType` 문자열 직접 비교
- `groupId` 문자열 직접 사용

권장 패턴:

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

### Step 6. 발행 코드 전환

단순 이벤트는 `KafkaEventPublisher`를 직접 사용할 수 있다.

```java
kafkaEventPublisher.publish(
        KafkaTopics.USAGE_PERSIST,
        KafkaEventTypes.USAGE_PERSIST,
        payload);
```

notification은 `NotificationEventSupport`를 사용하는 것을 권장한다.

```java
kafkaEventPublisher.publish(
        KafkaTopics.NOTIFICATION,
        NotificationEventSupport.toEnvelope(payload));
```

### Step 7. notification subtype 전환

기존에 서비스에서 아래처럼 직접 매핑하고 있었다면:

```java
String subType = switch (payload) {
    case QuotaUpdatedPayload ignored -> "QUOTA_UPDATED";
    case CustomerBlockedPayload ignored -> "CUSTOMER_BLOCKED";
    case ThresholdAlertPayload ignored -> "THRESHOLD_ALERT";
};
```

다음처럼 교체한다.

```java
String subType = NotificationEventSupport.resolveSubType(payload);
```

또는 envelope 생성까지 라이브러리에 맡긴다.

```java
EventEnvelope<NotificationPayload> envelope =
        NotificationEventSupport.toEnvelope(payload);
```

### Step 8. 메트릭 확인

전환 후에는 다음 메트릭이 기대대로 올라오는지 본다.

- producer success/error
- consumer success
- consumer retryable error
- consumer invalid event
- consumer dlt

## 5. 서비스 전용 설정을 유지해야 할 때

모든 Kafka 설정을 라이브러리로 완전히 흡수해야 하는 것은 아니다.
서비스마다 필요한 설정이 있으면 남길 수 있다.
다만 공통 표준을 깨지 않도록 경계를 분명히 두는 것이 중요하다.

### 예시: 서비스 전용 listener factory

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

```java
@KafkaListener(
        topics = KafkaTopics.USAGE_EVENTS,
        groupId = KafkaConsumerGroups.DABOM_API_CORE_REALTIME,
        containerFactory = "usageKafkaListenerContainerFactory")
public void consume(ConsumerRecord<String, String> record) {
    usageRealtimeConsumer.consume(record, kafkaEventMessageSupport);
}
```

## 6. 검증 시나리오

### 기본 검증

```bash
./gradlew clean test
```

### 서비스 스모크 테스트

1. 정상 이벤트 1건 송신
2. 잘못된 payload 1건 송신
3. 일시 장애 상황에서 retry 동작 확인
4. DLT 적재 여부 확인
5. 메트릭 카운터 증가 확인

## 7. 롤백 전략

- 적용 후 문제가 있으면 이전 태그로 즉시 되돌린다.
- 기존 태그를 덮어쓰지 않는다.
- 수정이 필요하면 새 태그를 발행한다.

## 8. 전환 완료 기준

- 서비스가 새 패키지 경로로 정상 컴파일된다.
- topic / eventType / groupId 문자열이 계약 상수로 치환된다.
- notification subtype 매핑이 라이브러리 helper 기준으로 통일된다.
- Kafka listener가 정상 동작한다.
- retry / ignore / dlt 정책이 기대대로 동작한다.
- 메트릭이 정상 수집된다.

