package com.dabom.messaging.kafka.event.dto.usage;

// 실시간 조회나 알림에 쓰는 현재 사용량 스냅샷이다.
public record UsageRealtimePayload(
        Long familyId,
        Long customerId,
        Long totalUsedBytes,
        Long totalLimitBytes,
        Long remainingBytes,
        Double usedPercent,
        Long monthlyUsedBytes,
        Double userUsagePercent,
        Long monthlyLimitBytes) {}
