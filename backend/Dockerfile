# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode package -DskipTests \
 && mv target/*.jar /app.jar

FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 --create-home api
USER api
WORKDIR /home/api

COPY --from=build --chown=api:api /app.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD ["sh", "-c", "curl -fsS http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"'"]

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
