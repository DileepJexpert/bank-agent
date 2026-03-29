# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# Copy parent POM and module POMs first for dependency caching
COPY pom.xml ./pom.xml
COPY common-spring-boot-starter/pom.xml ./common-spring-boot-starter/pom.xml
COPY mcp-card-management-server/pom.xml ./mcp-card-management-server/pom.xml

# Download dependencies (cached unless POMs change)
RUN mvn dependency:go-offline -pl mcp-card-management-server -am -B -q || true

# Copy source code
COPY common-spring-boot-starter/src ./common-spring-boot-starter/src
COPY mcp-card-management-server/src ./mcp-card-management-server/src

# Build the application
RUN mvn clean package -pl mcp-card-management-server -am -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/mcp-card-management-server/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8089

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD wget -qO- http://localhost:8089/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
