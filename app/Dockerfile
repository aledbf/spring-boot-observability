FROM curlimages/curl:8.13.0 AS download
ARG OTEL_AGENT_VERSION="2.16.0"
ARG PYROSCOPE_AGENT_VERSION="2.1.2"
RUN curl --silent --fail -L "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" \
    -o "$HOME/opentelemetry-javaagent.jar" && \
    curl --silent --fail -L "https://github.com/grafana/pyroscope-java/releases/download/v${PYROSCOPE_AGENT_VERSION}/pyroscope.jar" \
    -o "$HOME/pyroscope.jar"

FROM maven:3-amazoncorretto-24-alpine AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests --quiet

FROM amazoncorretto:24-alpine3.21

RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

WORKDIR /app

COPY --from=build /build/target/*.jar /app/app.jar
COPY --from=download /home/curl_user/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
COPY --from=download /home/curl_user/pyroscope.jar /app/pyroscope.jar

RUN chown -R appuser:appgroup /app

USER appuser

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-javaagent:/app/opentelemetry-javaagent.jar", \
    "-javaagent:/app/pyroscope.jar", \
    "-jar", "/app/app.jar" \
]
