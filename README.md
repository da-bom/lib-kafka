# lib-kafka

Spring Kafka 기반 서비스에서 반복되는 설정, 에러 처리, 이벤트 계약, 메트릭 코드를 공통화한 라이브러리입니다.

## 1) 이 라이브러리가 해결하는 문제
- 서비스마다 중복되던 Kafka 설정, 에러 핸들링, 메트릭 코드를 공통 모듈로 통합
- 이벤트 엔벨로프(`EventEnvelope`)와 DTO 파싱 패턴을 표준화
- 운영 시 장애 대응(DLT, 재시도, 메트릭) 동작을 일관되게 유지

## 2) 지원 범위
- `autoconfigure`: Kafka Producer/Consumer Factory, Listener Container, Common Error Handler
- `error`: 예외 분류기, 도메인 예외, 액션/코드 모델
- `metrics`: Producer/Consumer 메트릭, 인터셉터/리스너

- `event`: `EventEnvelope`, DTO, 이벤트 타입 기반 소비 헬퍼

상세 문서:

- [usage-guide](docs/usage-guide.md)
- [kafka-architecture-overview](docs/kafka-architecture-overview.md)
- [error-handling-policy](docs/error-handling-policy.md)
- [operations-guide](docs/operations-guide.md)
- [migration-guide](docs/migration-guide.md)

## 3) 소비 프로젝트에 붙이기

### 3-1. JitPack 저장소 추가
```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

### 3-2. 의존성 추가 (v1.0.0 기준)
```gradle
dependencies {
    implementation 'com.github.da-bom:lib-kafka:v1.0.0'
}
```

주의: 운영에서는 항상 고정 태그 사용. 새 릴리즈가 나오면 태그만 올려 교체하세요.

### 3-3. 스프링 자동 인식(컴포넌트 스캔) 조건
라이브러리 클래스 패키지는 `com.dabom.messaging.kafka` 입니다.

- 애플리케이션의 `@SpringBootApplication` 루트 패키지가 `com.project` 하위면 기본 스캔으로 인식
- 루트 패키지가 다르면 명시적으로 스캔 범위를 추가

```java
@SpringBootApplication(scanBasePackages = {
    "com.myservice",
    "com.dabom.messaging.kafka"
})
public class UsageApplication {}
```

## 4) 운영/버전 정책
- 태그 버전 고정 사용: `v1.0.0` 같은 방식
- 기존 태그 재사용 금지: 변경 시 반드시 새 태그 발행
- 브레이킹 변경(호환 불가 API/동작 변경)은 메이저 버전 업
- 패치/기능 추가는 마이너/패치 올리고 새 태그 발행

## 5) 버전별 변경 요약
- `v1.0.0`
  - notification 계약을 `NotificationType` + 단일 `NotificationPayload` 구조로 단순화
  - `EventEnvelope.subType` 제거
  - notification 발행 시 `type`, `title`, `message`, `data` 중심 계약 사용
  - 폐기된 `usage-persist` 토픽 계약 제거
  - `USAGE_PERSIST`, `UsagePersistPayload`, persistence consumer group 제거
- `v0.5.0`
  - `KafkaTopics`, `KafkaEventTypes`, `KafkaConsumerGroups` 추가
  - `NotificationSubTypes`, `NotificationEventSupport` 추가
  - topic / eventType / consumer group / notification subtype 계약 중앙화
- `v0.4.0`
  - 패키지 구조를 `com.dabom.messaging.kafka` 기준으로 재정리
  - `KafkaEventPublisher`, `DefaultKafkaEventPublisher`, `KafkaEventConsumer<T>` 추가
  - tracing 지원 제거
- `v0.3.x` 이하
  - 초기 공통 Kafka 설정, 이벤트 envelope, 에러 처리, 메트릭 기능 제공

## 6) 장애 대응: JitPack 이슈 시 fallback
JitPack 장애/지연 시 `mavenLocal`로 임시 검증 가능합니다.

라이브러리 프로젝트에서:
```bash
./gradlew clean publishToMavenLocal -x test
```

소비 프로젝트에서:
```gradle
repositories {
    mavenLocal()
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.da-bom:lib-kafka:v1.0.0'
}
```

## 7) FAQ
### Q1. 왜 내 로컬 코드에 클래스가 없어도 import가 되나요?
의존성으로 받은 jar 내부 클래스이기 때문입니다. IDE가 Maven/Gradle 캐시에서 클래스를 인덱싱해 import를 제공합니다.

### Q2. 패키지가 `com.project`가 아니면 어떻게 인식시키나요?
`@SpringBootApplication(scanBasePackages = {"내 패키지", "com.dabom.messaging.kafka"})` 형태로 스캔 범위를 추가하세요.

## 8) 빠른 점검 체크리스트
```bash
./gradlew clean compileJava
./gradlew clean test
./gradlew clean publishToMavenLocal -x test
```

스모크 테스트 권장:
- 소비 프로젝트에서 라이브러리 클래스 import 확인
- Kafka 송신 1건 + 소비 1건으로 listener/metrics/error handler 빈 로딩 확인




