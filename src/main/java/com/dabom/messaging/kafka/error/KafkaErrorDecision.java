package com.dabom.messaging.kafka.error;

// 예외 분류 결과를 action/code 쌍으로 전달한다.
public record KafkaErrorDecision(KafkaErrorAction action, KafkaErrorCode code) {}
