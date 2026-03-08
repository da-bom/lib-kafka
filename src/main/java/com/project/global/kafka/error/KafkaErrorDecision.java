package com.project.global.kafka.error;

// 분류 결과(action/code)를 한 번에 전달하기 위한 값 객체.
public record KafkaErrorDecision(KafkaErrorAction action, KafkaErrorCode code) {}
