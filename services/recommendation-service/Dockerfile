FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY common/ ./common/
COPY services/recommendation-service/ ./services/recommendation-service/
RUN mvn dependency:go-offline -pl services/recommendation-service -am \
    --no-transfer-progress -q
RUN mvn clean package -pl services/recommendation-service -am \
    -DskipTests --no-transfer-progress
RUN java -Djarmode=layertools \
    -jar services/recommendation-service/target/recommendation-service-*.jar \
    extract --destination extracted

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser
WORKDIR /app
COPY --from=builder /workspace/extracted/dependencies/          ./
COPY --from=builder /workspace/extracted/spring-boot-loader/    ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/           ./
USER appuser

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:MaxGCPauseMillis=100 \
               -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"

EXPOSE 8095
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -q --spider http://localhost:8095/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
