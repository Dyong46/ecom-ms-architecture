FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring spring
WORKDIR /app

COPY --chown=spring:spring target/api-gateway-*.jar app.jar

USER 10001:10001
EXPOSE 8222

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
