package com.dabom.messaging.kafka.event.dto.notification;

public final class NotificationSubTypes {
    public static final String QUOTA_UPDATED = "QUOTA_UPDATED";
    public static final String CUSTOMER_BLOCKED = "CUSTOMER_BLOCKED";
    public static final String THRESHOLD_ALERT = "THRESHOLD_ALERT";

    private NotificationSubTypes() {}
}
