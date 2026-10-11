# Multi-stage build for WildStep Spring Boot backend
FROM maven:3.9.9-eclipse-temurin-21-jammy AS builder
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage: minimal Java 21 JRE
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Non-root user for security
RUN groupadd -r wildstep && useradd -r -g wildstep wildstep
USER wildstep:wildstep

COPY --from=builder /workspace/target/wildstep-backend-*.jar app.jar

ENV PORT=8080
EXPOSE 8080

# Memory-conscious flags for 512MB RAM free instances:
# - MaxRAMPercentage=75.0 allows up to 384MB max heap, keeping ~128MB headroom for JVM native memory & thread stacks
# - InitialRAMPercentage=40.0 sets sensible initial footprint
# - Xss256k lowers thread stack size from 1024k to 256k to minimize memory per concurrent request thread
# - Production profile activated by default
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=40.0 -Xss256k -Dspring.profiles.active=prod -Dserver.port=${PORT} -jar app.jar"]
