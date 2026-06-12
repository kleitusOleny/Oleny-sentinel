# Stage 1: Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

COPY gradlew ./
COPY gradle/ ./gradle/
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x ./gradlew

COPY src ./src

RUN ./gradlew clean build -x test

# Stage 2: Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]