# Kafka Topic & Consumer Group ID Name

이 문서는 DABOM 서비스에서 사용하는 Kafka topic, consumer group, eventType, notification subType 명명 기준을 정리한다.
현재 `lib-kafka`의 계약 상수와 맞춰 유지한다.

## 1. 공통 원칙

- topic 이름은 실제 Kafka 토픽 이름을 기준으로 정의한다.
- consumer group 이름은 `{service-name}-{role}-group` 형식을 따른다.
- eventType은 `EventEnvelope.eventType`에 들어가는 표준 계약 문자열이다.
- notification subType은 `NotificationPayload` 계층에서만 사용한다.

## 2. Topic 정의

| Topic Name | 상수 | Producer | Consumer | Description |
| --- | --- | --- | --- | --- |
| `usage-events` | `KafkaTopics.USAGE_EVENTS` | `simulator-traffic` | `dabom-processor-usage` | 원본 트래픽 사용량 이벤트 스트림 |
| `policy-updated` | `KafkaTopics.POLICY_UPDATED` | `dabom-api-core` | `dabom-processor-usage` | 정책 변경 이벤트 |
| `usage-persist` | `KafkaTopics.USAGE_PERSIST` | `dabom-processor-usage` | `dabom-processor-usage` | DB write-behind 저장 이벤트 |
| `usage-realtime` | `KafkaTopics.USAGE_REALTIME` | `dabom-processor-usage` | `dabom-api-core` | 실시간 대시보드 갱신 이벤트 |
| `notification-events` | `KafkaTopics.NOTIFICATION` | `dabom-processor-usage` | `dabom-api-notification` | 사용자 알림 이벤트 스트림 |

## 3. Consumer Group 정의

| Consumer Group ID | 상수 | Service | Purpose |
| --- | --- | --- | --- |
| `dabom-processor-usage-main-group` | `KafkaConsumerGroups.DABOM_PROCESSOR_USAGE_MAIN` | `dabom-processor-usage` | 메인 사용량 처리 |
| `dabom-processor-usage-policy-group` | `KafkaConsumerGroups.DABOM_PROCESSOR_USAGE_POLICY` | `dabom-processor-usage` | 정책 변경 반영 |
| `dabom-processor-usage-persistence-group` | `KafkaConsumerGroups.DABOM_PROCESSOR_USAGE_PERSISTENCE` | `dabom-processor-usage` | DB 저장 처리 |
| `dabom-api-core-realtime-group` | `KafkaConsumerGroups.DABOM_API_CORE_REALTIME` | `dabom-api-core` | 실시간 대시보드 갱신 |
| `dabom-notification-sender-group` | `KafkaConsumerGroups.DABOM_NOTIFICATION_SENDER` | `dabom-api-notification` | 사용자 알림 발송 |

## 4. Event Type 정의

| Event Type | 상수 | Payload |
| --- | --- | --- |
| `DATA_USAGE` | `KafkaEventTypes.DATA_USAGE` | `UsagePayload` |
| `POLICY_UPDATED` | `KafkaEventTypes.POLICY_UPDATED` | `PolicyUpdatedPayload` |
| `USAGE_PERSIST` | `KafkaEventTypes.USAGE_PERSIST` | `UsagePersistPayload` |
| `USAGE_REALTIME` | `KafkaEventTypes.USAGE_REALTIME` | `UsageRealtimePayload` |
| `NOTIFICATION` | `KafkaEventTypes.NOTIFICATION` | `NotificationPayload` |

## 5. Notification SubType 정의

`NotificationPayload`는 sealed interface이며, subtype은 아래 기준으로 사용한다.

| SubType | 상수 | Payload |
| --- | --- | --- |
| `QUOTA_UPDATED` | `NotificationSubTypes.QUOTA_UPDATED` | `QuotaUpdatedPayload` |
| `CUSTOMER_BLOCKED` | `NotificationSubTypes.CUSTOMER_BLOCKED` | `CustomerBlockedPayload` |
| `THRESHOLD_ALERT` | `NotificationSubTypes.THRESHOLD_ALERT` | `ThresholdAlertPayload` |

주의:
- 현재 기준 notification 차단 이벤트 명칭은 `CUSTOMER_BLOCKED`다.
- 문서나 서비스 코드에 남아 있는 `USER_BLOCKED` 표현은 최신 계약이 아니다.

## 6. 구현 가이드

### 6.1 단순 발행 이벤트

`UsagePersist`, `UsageRealtime`처럼 subtype이 없는 이벤트는 topic / eventType 상수만 사용하면 된다.

```java
kafkaEventPublisher.publish(
        KafkaTopics.USAGE_PERSIST,
        KafkaEventTypes.USAGE_PERSIST,
        payload);
```

### 6.2 Notification 이벤트

notification은 subtype을 직접 문자열로 계산하지 말고 helper를 사용한다.

```java
String subType = NotificationEventSupport.resolveSubType(payload);
```

또는 envelope 생성까지 helper에 맡긴다.

```java
kafkaEventPublisher.publish(
        KafkaTopics.NOTIFICATION,
        NotificationEventSupport.toEnvelope(payload));
```

### 6.3 Consumer 예시

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

## 7. 변경 원칙

- topic, consumer group, eventType, notification subType은 서비스 로컬 문자열로 새로 만들지 않는다.
- 새로운 공통 이벤트가 추가되면 먼저 `lib-kafka` 계약 상수와 DTO를 함께 정의한다.
- notification subtype 매핑 규칙은 서비스가 아니라 라이브러리 helper에서 관리한다.

## 8. 연관 클래스

- `com.dabom.messaging.kafka.contract.KafkaTopics`
- `com.dabom.messaging.kafka.contract.KafkaConsumerGroups`
- `com.dabom.messaging.kafka.contract.KafkaEventTypes`
- `com.dabom.messaging.kafka.event.dto.notification.NotificationSubTypes`
- `com.dabom.messaging.kafka.event.dto.notification.NotificationEventSupport`
