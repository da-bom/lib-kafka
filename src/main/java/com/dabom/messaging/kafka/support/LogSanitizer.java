package com.dabom.messaging.kafka.support;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class LogSanitizer {
    // 줄바꿈과 탭은 로그 포맷을 깨뜨릴 수 있어 치환한다.
    private static final Pattern LOG_DANGEROUS_PATTERN = Pattern.compile("[\\r\\n\\t]");
    // 로그 값이 지나치게 길어지는 것을 막기 위한 상한선이다.
    private static final int MAX_LOG_VALUE_LENGTH = 128;

    public String sanitize(String raw) {
        if (raw == null) {
            return "null";
        }

        String sanitized = LOG_DANGEROUS_PATTERN.matcher(raw).replaceAll("_");
        if (sanitized.length() > MAX_LOG_VALUE_LENGTH) {
            return sanitized.substring(0, MAX_LOG_VALUE_LENGTH) + "...";
        }
        return sanitized;
    }
}
