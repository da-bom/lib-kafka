# dabom-kafka-common

Common Kafka library for DABOM services.

## Features
- Kafka configuration (`KafkaConfig`)
- Common Kafka error handling (`KafkaErrorHandlerConfig`)
- Exception classification and error model
- Kafka metrics interceptor/listener
- Kafka tracing support
- Event envelope and DTO helpers (`KafkaEventMessageSupport`)

## JitPack Usage
1. Create and push a git tag (example: `v0.1.3`).
2. Add JitPack repository:

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

3. Add dependency:

```gradle
dependencies {
    implementation 'com.github.ChoiSeungeon:dabom-kafka-common:v0.1.3'
}
```

## Local Publish (mavenLocal)
From this repository:

```bash
./gradlew clean publishToMavenLocal -x test
```

From consumer project:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'com.github.ChoiSeungeon:dabom-kafka-common:0.1.2'
}
```

## Version Rule
- Gradle project version: `0.1.x`
- Git tag for JitPack: `v0.1.x`

Keep these aligned for predictable resolution.

## Build Requirements
- Java 21
- Gradle Wrapper 8.10.2
