plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.server.sentinel"
version = "0.0.1-SNAPSHOT"
description = "server-sentinel"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    // Thêm starter-web để hỗ trợ gửi HTTP request
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Thêm các thư viện Docker client
    implementation("com.github.docker-java:docker-java-core:3.3.4")

    implementation("com.github.docker-java:docker-java-transport-zerodep:3.3.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    
    // Discord Bot JDA library
    implementation("net.dv8tion:JDA:5.0.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
