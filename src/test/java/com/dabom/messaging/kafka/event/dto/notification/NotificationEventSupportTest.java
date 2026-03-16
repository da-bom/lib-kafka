package com.dabom.messaging.kafka.event.dto.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.dabom.messaging.kafka.contract.KafkaEventTypes;
import com.dabom.messaging.kafka.event.dto.EventEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class NotificationEventSupportTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    @DisplayName("resolveTitle은 모든 NotificationType에 대해 non-null 한글 title을 반환한다")
    void resolveTitleReturnsNonNullForAllTypes(NotificationType type) {
        String title = NotificationEventSupport.resolveTitle(type);

        assertNotNull(title);
        assertFalse(title.isBlank());
    }

    @Test
    @DisplayName("resolveTitle은 각 타입별 올바른 한글 title을 반환한다")
    void resolveTitleReturnsCorrectTitle() {
        assertEquals("데이터 잔여량 갱신", NotificationEventSupport.resolveTitle(NotificationType.QUOTA_UPDATED));
        assertEquals("데이터 경고", NotificationEventSupport.resolveTitle(NotificationType.THRESHOLD_ALERT));
        assertEquals("데이터 차단", NotificationEventSupport.resolveTitle(NotificationType.CUSTOMER_BLOCKED));
        assertEquals("데이터 차단 해제", NotificationEventSupport.resolveTitle(NotificationType.CUSTOMER_UNBLOCKED));
        assertEquals("정책 변경", NotificationEventSupport.resolveTitle(NotificationType.POLICY_CHANGED));
        assertEquals("미션 생성", NotificationEventSupport.resolveTitle(NotificationType.MISSION_CREATED));
        assertEquals("보상 요청", NotificationEventSupport.resolveTitle(NotificationType.REWARD_REQUESTED));
        assertEquals("보상 승인", NotificationEventSupport.resolveTitle(NotificationType.REWARD_APPROVED));
        assertEquals("보상 거절", NotificationEventSupport.resolveTitle(NotificationType.REWARD_REJECTED));
        assertEquals("이의제기 접수", NotificationEventSupport.resolveTitle(NotificationType.APPEAL_CREATED));
        assertEquals("이의제기 승인", NotificationEventSupport.resolveTitle(NotificationType.APPEAL_APPROVED));
        assertEquals("이의제기 거절", NotificationEventSupport.resolveTitle(NotificationType.APPEAL_REJECTED));
        assertEquals("긴급 쿼터 승인", NotificationEventSupport.resolveTitle(NotificationType.EMERGENCY_APPROVED));
    }

    @Test
    @DisplayName("toEnvelope은 eventType=NOTIFICATION인 EventEnvelope을 생성한다")
    void toEnvelopeCreatesCorrectEnvelope() {
        NotificationPayload payload = new NotificationPayload(
                1L, 2L, NotificationType.THRESHOLD_ALERT,
                "데이터 경고", "50% 남았습니다", Map.of("thresholdPercent", 50));

        EventEnvelope<NotificationPayload> envelope = NotificationEventSupport.toEnvelope(payload);

        assertEquals(KafkaEventTypes.NOTIFICATION, envelope.eventType());
        assertNotNull(envelope.eventId());
        assertNotNull(envelope.timestamp());
        assertSame(payload, envelope.payload());
    }

    @Test
    @DisplayName("NotificationPayload Jackson round-trip 직렬화/역직렬화가 정상 동작한다")
    void jacksonRoundTrip() throws Exception {
        NotificationPayload original = new NotificationPayload(
                100L, null, NotificationType.CUSTOMER_BLOCKED,
                "데이터 차단", "정책 위반으로 차단되었습니다",
                Map.of("blockReason", "POLICY_VIOLATION", "blockedAt", "2026-03-16T10:00:00"));

        EventEnvelope<NotificationPayload> envelope = NotificationEventSupport.toEnvelope(original);

        String json = objectMapper.writeValueAsString(envelope);
        EventEnvelope<NotificationPayload> deserialized = objectMapper.readValue(
                json, new TypeReference<EventEnvelope<NotificationPayload>>() {});

        assertEquals(envelope.eventId(), deserialized.eventId());
        assertEquals(envelope.eventType(), deserialized.eventType());
        assertEquals(original.familyId(), deserialized.payload().familyId());
        assertNull(deserialized.payload().customerId());
        assertEquals(original.type(), deserialized.payload().type());
        assertEquals(original.title(), deserialized.payload().title());
        assertEquals(original.message(), deserialized.payload().message());
        assertEquals("POLICY_VIOLATION", deserialized.payload().data().get("blockReason"));
    }

    @Test
    @DisplayName("customerId가 있는 경우와 null인 경우 모두 직렬화/역직렬화된다")
    void jacksonRoundTripWithAndWithoutCustomerId() throws Exception {
        // customerId가 있는 경우 (단일 전송)
        NotificationPayload withCustomer = new NotificationPayload(
                1L, 42L, NotificationType.REWARD_APPROVED,
                "보상 승인", "미션 보상이 승인되었습니다",
                Map.of("missionId", 10, "rewardId", 20));

        EventEnvelope<NotificationPayload> envelope1 = NotificationEventSupport.toEnvelope(withCustomer);
        String json1 = objectMapper.writeValueAsString(envelope1);
        EventEnvelope<NotificationPayload> result1 = objectMapper.readValue(
                json1, new TypeReference<EventEnvelope<NotificationPayload>>() {});

        assertEquals(42L, result1.payload().customerId());

        // customerId가 null인 경우 (fan-out)
        NotificationPayload withoutCustomer = new NotificationPayload(
                1L, null, NotificationType.POLICY_CHANGED,
                "정책 변경", "가족 정책이 변경되었습니다",
                Map.of("policyKey", "dailyLimit", "newValue", "5GB"));

        EventEnvelope<NotificationPayload> envelope2 = NotificationEventSupport.toEnvelope(withoutCustomer);
        String json2 = objectMapper.writeValueAsString(envelope2);
        EventEnvelope<NotificationPayload> result2 = objectMapper.readValue(
                json2, new TypeReference<EventEnvelope<NotificationPayload>>() {});

        assertNull(result2.payload().customerId());
        assertEquals("dailyLimit", result2.payload().data().get("policyKey"));
    }
}
