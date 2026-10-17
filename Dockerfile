FROM node:24-alpine3.21 AS frontend-builder
WORKDIR /tmp/frontend
COPY ./frontend/package*.json ./
RUN npm ci
COPY ./frontend .
RUN npm run build

FROM gradle:8.10-jdk21-alpine AS gradle-builder
WORKDIR /tmp/src/

RUN apk add --no-cache gcompat libc6-compat

# Copy Gradle wrapper + build scripts first for better layer caching
COPY ./backend/gradle ./gradle
COPY ./backend/gradlew ./backend/settings.gradle.kts ./backend/build.gradle.kts ./
# (add ./backend/gradle.properties here too if your project has one)

RUN chmod +x ./gradlew

# Warm the dependency cache — this layer is reused as long as the build files don't change
RUN ./gradlew dependencies --no-daemon

# Copy the frontend build into the Spring Boot static resources folder
COPY --from=frontend-builder /tmp/frontend/dist/ ./src/main/resources/META-INF/resources/

# Copy the rest of the source code and build
COPY ./backend .
RUN ./gradlew clean build -x test --no-daemon

# DJL tokenizer JNI requires GLIBC_2.29+; UBI 8 provides an older glibc.
# Pin the multi-architecture UBI 9 manifest (includes arm64 and s390x).
FROM registry.access.redhat.com/ubi9/openjdk-21@sha256:bd995811a45eb587f97a105796f8f43df466a4269411a2a1642f0f9799f885db

ENV LANGUAGE='en_US:en'

# Single Spring Boot "fat" jar — this is what `./gradlew bootJar` produces.
COPY --from=gradle-builder --chown=185 /tmp/src/build/libs/app.jar /deployments/app.jar

EXPOSE 8080
USER 185

# Spring Boot already binds 0.0.0.0 by default; this is belt-and-braces
# and lets you flip it per-environment.
ENV JAVA_OPTS_APPEND="-Dserver.address=0.0.0.0"
ENV JAVA_APP_JAR="/deployments/app.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
