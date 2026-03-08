package com.project.global.tracing;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

/** Kafka 토픽 단위 트레이싱 공통 유틸. */
@Component
public class KafkaTracingSupport {

    /** OpenTelemetry 전파기. */
    private static final TextMapPropagator PROPAGATOR =
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

    /** ProducerRecord 헤더에 trace 정보를 쓰는 규칙(Setter). */
    private static final TextMapSetter<ProducerRecord<?, ?>> PRODUCER_RECORD_SETTER =
            (carrier, key, value) -> {
                if (carrier == null || key == null || value == null) {
                    return;
                }
                carrier.headers().remove(key);
                carrier.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
            };

    /** ConsumerRecord 헤더에서 trace 정보를 읽는 규칙(Getter). */
    private static final TextMapGetter<ConsumerRecord<?, ?>> CONSUMER_RECORD_GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(ConsumerRecord<?, ?> carrier) {
                    java.util.List<String> keys = new java.util.ArrayList<>();
                    if (carrier == null) {
                        return keys;
                    }
                    for (Header header : carrier.headers()) {
                        keys.add(header.key());
                    }
                    return keys;
                }

                @Override
                public String get(ConsumerRecord<?, ?> carrier, String key) {
                    if (carrier == null || key == null) {
                        return null;
                    }
                    Header header = carrier.headers().lastHeader(key);
                    return header == null
                            ? null
                            : new String(header.value(), StandardCharsets.UTF_8);
                }
            };

    private final Tracer tracer;

    public KafkaTracingSupport() {
        this.tracer = GlobalOpenTelemetry.getTracer("dabom-kafka-tracing");
    }

    /** 현재 컨텍스트를 ProducerRecord 헤더(traceparent 등)에 주입한다. */
    public void injectCurrentContext(ProducerRecord<?, ?> record) {
        PROPAGATOR.inject(Context.current(), record, PRODUCER_RECORD_SETTER);
    }

    /** ConsumerRecord 헤더에서 부모 컨텍스트를 추출한다. */
    public Context extractContext(ConsumerRecord<?, ?> record) {
        return PROPAGATOR.extract(Context.current(), record, CONSUMER_RECORD_GETTER);
    }

    /** Kafka consume 경계 span을 시작한다. (토픽 단위) */
    public Span startConsumerSpan(Context parentContext, String topic, String eventId) {
        Span span =
                tracer.spanBuilder("kafka.consume " + topic)
                        .setParent(parentContext)
                        .setSpanKind(SpanKind.CONSUMER)
                        .startSpan();
        setAttributes(span, null, topic, eventId);
        return span;
    }

    /** Kafka produce 경계 span을 시작한다. (토픽 단위) */
    public Span startProducerSpan(String topic, String eventId) {
        Span span =
                tracer.spanBuilder("kafka.produce " + topic)
                        .setParent(Context.current())
                        .setSpanKind(SpanKind.PRODUCER)
                        .startSpan();
        setAttributes(span, null, topic, eventId);
        return span;
    }

    /** Kafka 처리 단계용 내부 span을 시작한다. (예: validate, redis.lua, db.upsert) */
    public Span startStepSpan(String spanName) {
        return tracer.spanBuilder(spanName)
                .setParent(Context.current())
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
    }

    /** 현재 스코프에서 child span으로 비즈니스 로직을 실행한다. */
    public <T> T runWithStepSpan(String spanName, Supplier<T> action) {
        Span span = startStepSpan(spanName);
        try (Scope ignored = span.makeCurrent()) {
            return action.get();
        } catch (RuntimeException ex) {
            markError(span, ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    /** 현재 스코프에서 child span으로 비즈니스 로직을 실행한다. (반환값 없음) */
    public void runWithStepSpan(String spanName, Runnable action) {
        Span span = startStepSpan(spanName);
        try (Scope ignored = span.makeCurrent()) {
            action.run();
        } catch (RuntimeException ex) {
            markError(span, ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    /** 예외 정보를 span에 기록하고 오류 상태를 표시한다. */
    public void markError(Span span, Throwable throwable) {
        if (span == null) {
            return;
        }
        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR);
    }

    /** 최소 추적 속성을 span에 한 번에 기록한다. */
    public void setAttributes(
            Span span, ConsumerRecord<?, ?> record, String topic, String eventId) {
        if (span == null) {
            return;
        }
        if (topic != null) {
            span.setAttribute("kafka.topic", topic);
        }
        if (record != null) {
            span.setAttribute("kafka.topic", record.topic());
            span.setAttribute("kafka.partition", record.partition());
            span.setAttribute("kafka.offset", record.offset());
        }
        if (eventId != null) {
            span.setAttribute("event.id", eventId);
        }
    }
}
