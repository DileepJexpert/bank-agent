# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# Copy parent POM and module POMs first for dependency caching
COPY pom.xml ./pom.xml
COPY common-spring-boot-starter/pom.xml ./common-spring-boot-starter/pom.xml
COPY vault-identity-service/pom.xml ./vault-identity-service/pom.xml

# Download dependencies (cached unless POMs change)
RUN mvn dependency:go-offline -pl vault-identity-service -am -B -q || true

# Copy source code
COPY common-spring-boot-starter/src ./common-spring-boot-starter/src
COPY vault-identity-service/src ./vault-identity-service/src

# Build the application
RUN mvn clean package -pl vault-identity-service -am -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/vault-identity-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
