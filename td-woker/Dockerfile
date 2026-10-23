# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Cache dependencies trước (chỉ khi pom không đổi)
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---------- Stage 2: Runtime (image nhẹ) ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/td-worker.jar app.jar

# Worker KHÔNG có controller nghiệp vụ - cổng này chỉ cho actuator/health/prometheus.
EXPOSE 8090

# Graceful shutdown đúng cách (drain executor + đóng pool)
# Trích nội dung tệp giữ tệp trong bộ nhớ (tối đa WORKER_FILE_EXTRACT_MAX_FILE_BYTES/tệp × số tenant song song) → heap 1GB.
ENV JAVA_OPTS="-Xms256m -Xmx1024m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]