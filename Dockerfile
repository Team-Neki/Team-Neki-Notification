# Layer extraction stage
FROM amazoncorretto:21-alpine@sha256:600e99e39da14b6a909027a924136c423e9334cb7347a8a77d5908bdaa3011b7 AS builder

WORKDIR /app

# GitHub Actions에서 빌드된 batch 앱 bootJar 복사 (apps:batch 모듈)
# plain jar는 apps/batch/build.gradle.kts에서 비활성화되어 boot jar 하나만 존재한다.
COPY apps/batch/build/libs/*.jar app.jar

# Spring Boot Layered JAR에서 레이어 추출
RUN java -Djarmode=layertools -jar app.jar extract

# Runtime stage
# Java4Shell(CVE-2026-114938) 대응: eclipse-temurin 패치 이미지가 아직 게시되지 않아
# 패치된 Corretto 21.0.13.1.2 (alpine 3.24) 를 digest 로 고정한다. Temurin 21.0.13.1+ 게시 후 되돌려도 된다.
FROM amazoncorretto:21-alpine@sha256:600e99e39da14b6a909027a924136c423e9334cb7347a8a77d5908bdaa3011b7

WORKDIR /app

# 보안을 위해 non-root 사용자 생성
RUN addgroup -S spring && adduser -S spring -G spring

# 레이어별로 복사 (변경 빈도가 낮은 순서대로)
# 1. dependencies: 외부 라이브러리 (거의 변경 안 됨)
COPY --from=builder --chown=spring:spring /app/dependencies/ ./

# 2. spring-boot-loader: Spring Boot 로더
COPY --from=builder --chown=spring:spring /app/spring-boot-loader/ ./

# 3. snapshot-dependencies: SNAPSHOT 의존성
COPY --from=builder --chown=spring:spring /app/snapshot-dependencies/ ./

# 4. application: 애플리케이션 코드 (자주 변경됨)
COPY --from=builder --chown=spring:spring /app/application/ ./

# non-root 사용자로 전환
USER spring:spring

# 환경변수 설정 (기본값, 런타임에 오버라이드 가능)
# 앱 내부 @Scheduled cron으로 잡을 구동하며, actuator 헬스(8080)를 K8s 프로브에 노출한다.
ENV TZ=Asia/Seoul
ENV SPRING_PROFILES_ACTIVE=prod

# 애플리케이션 실행
# Layered JAR는 org.springframework.boot.loader.launch.JarLauncher를 사용
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
