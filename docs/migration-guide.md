# 마이그레이션 가이드

이 문서는 기존 서비스가 자체 Kafka 설정이나 유틸 코드를 사용하고 있을 때
`lib-kafka` 기반 구조로 옮기는 절차를 설명한다.

## 1. 적용 대상

아래에 하나 이상 해당하면 전환 대상이다.

- 서비스마다 Kafka 설정 클래스가 중복된다.
- 재시도, DLT, 무시 정책이 서비스별로 제각각이다.
- 이벤트 엔벨로프 파싱 코드가 반복된다.
- producer/consumer 메트릭을 공통 기준으로 맞추고 싶다.

## 2. 이번 버전에서 달라진 점

이번 구조 개편 버전은 단순 기능 추가가 아니라 패키지 리네임을 포함한다.

주요 변경:

- `com.project.global...` -> `com.dabom.messaging.kafka...`
- `config` -> `autoconfigure`
- `kafka.error` -> `error`
- tracing 지원 제거
- `ExampleCreatedEvent` 제거

즉, import 경로 수정이 필요한 브레이킹 변경이다.

## 3. 전환 전 체크리스트

- 현재 서비스에서 직접 선언한 Kafka 관련 `@Configuration` 목록 정리
- 현재 listener가 참조하는 `containerFactory` 이름 확인
- 현재 예외 처리 정책이 어떻게 되어 있는지 표로 정리
- 사용 중인 topic, groupId, eventType 목록 확보

## 4. 전환 절차

### Step 1. 의존성 추가 또는 버전 교체

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.da-bom:lib-kafka:v0.4.0'
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

기존에 `com.project.global`을 스캔하고 있었다면 반드시 바꿔야 한다.

### Step 3. import 경로 변경

대표 예시:

- `com.project.global.event.KafkaEventMessageSupport`
  -> `com.dabom.messaging.kafka.event.KafkaEventMessageSupport`
- `com.project.global.config.KafkaConfig`
  -> `com.dabom.messaging.kafka.autoconfigure.KafkaConfig`
- `com.project.global.config.KafkaErrorHandlerConfig`
  -> `com.dabom.messaging.kafka.autoconfigure.KafkaErrorHandlerConfig`
- `com.project.global.kafka.error.KafkaExceptionClassifier`
  -> `com.dabom.messaging.kafka.error.KafkaExceptionClassifier`
- `com.project.global.event.dto.EventEnvelope`
  -> `com.dabom.messaging.kafka.event.dto.EventEnvelope`

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
- 서비스 고유의 토픽 라우팅이나 도메인 특화 설정만 남긴다.
- 동일 타입 Bean이 중복되면 충돌 위험이 크다.

### Step 5. 소비 코드 전환

기존 패턴:

- listener 안에서 직접 JSON 파싱
- `eventType` 수동 분기
- 예외를 매번 개별 처리

권장 패턴:

- `KafkaEventMessageSupport.consumeByEventType(...)` 사용

예시:

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

### Step 6. 발행 코드 전환

권장 패턴:

- `EventEnvelope.of(...)`로 생성
- `KafkaEventMessageSupport.serialize(...)`로 직렬화
- `KafkaTemplate<String, String>`로 전송

예시:

```java
EventEnvelope<NotificationPayload> envelope =
        EventEnvelope.of("NOTIFICATION", "QUOTA_UPDATED", payload);

kafkaTemplate.send(topic, kafkaEventMessageSupport.serialize(envelope));
```

### Step 7. 에러 정책 확인

현재 공통 규칙은 `KafkaExceptionClassifier`가 기준이다.

- `RETRY`
- `IGNORE`
- `DLQ`

상세 기준은 [error-handling-policy.md](./error-handling-policy.md)를 본다.

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

### 권장 원칙

- 공통 설정은 라이브러리에서 가져간다.
- 서비스 전용 요구사항은 별도 이름의 Bean으로 추가한다.
- 기본 공통 Bean을 무조건 덮어쓰기보다, 필요한 listener나 publisher에서만 선택 사용한다.

### 남겨도 되는 설정 예시

- 특정 listener 전용 `ConcurrentKafkaListenerContainerFactory`
- 특정 consumer의 concurrency 조정
- 특정 producer 흐름만 위한 별도 `KafkaTemplate`
- 서비스 전용 interceptor 또는 라우팅 정책

### 가급적 공통으로 유지할 것

- `EventEnvelope` 기반 직렬화 규칙
- 예외 분류 정책
- DLT 처리 규칙
- 공통 메트릭 이름과 태그 정책

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
        topics = "usage-events",
        groupId = "usage-consumer",
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
- Kafka listener가 정상 동작한다.
- retry/ignore/dlt 정책이 기대대로 동작한다.
- 메트릭이 정상 수집된다.
- 적용 버전 태그가 릴리스 노트와 일치한다.

