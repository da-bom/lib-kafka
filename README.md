# dabom-kafka-common

DABOM 서버들이 공통으로 사용하는 Kafka 설정/에러처리/관측성/이벤트 DTO를 제공하는 라이브러리입니다.

## 포함 구성 요소
- Kafka 설정 (`KafkaConfig`)
- Kafka 공통 에러 핸들러 (`KafkaErrorHandlerConfig`)
- Kafka 예외 분류기 및 에러 모델
- Kafka 메트릭/인터셉터
- Kafka 트레이싱 지원
- 이벤트 엔벨로프 및 DTO
- 이벤트 파싱 지원 (`KafkaEventMessageSupport`)

## JitPack 사용 방법 (개발/검증용)
1. 저장소에 릴리즈 태그를 생성합니다. 예: `v0.1.0`
2. 라이브러리를 사용하는 프로젝트에 JitPack 저장소를 추가합니다.

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

3. 의존성을 추가합니다.

```gradle
dependencies {
    implementation 'com.github.ChoiSeungeon:dabom-kafka-common:v0.1.0'
}
```

## 운영 시 참고
- `main-SNAPSHOT` 대신 태그 버전 고정을 권장합니다.
- 실제 운영 서비스에서는 사내 레지스트리 미러링 정책을 권장합니다.
