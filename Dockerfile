# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /workspace

# Copiar primero el Wrapper y la configuración permite reutilizar la capa
# que descarga Gradle y resuelve los plugins sin invalidarla por cambios en src.
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon help

COPY src src

# El proyecto produce un JAR ejecutable y otro "-plain". Se copia
# explícitamente el ejecutable para evitar incluir el JAR sin Bootstrap.
RUN ./gradlew --no-daemon clean bootJar \
    && find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' \
       -exec cp '{}' /workspace/application.jar \; \
    && test -s /workspace/application.jar

FROM eclipse-temurin:25-jre-jammy AS runtime

RUN groupadd --system --gid 10001 hotelsearch \
    && useradd --system --uid 10001 --gid hotelsearch \
       --no-create-home --shell /usr/sbin/nologin hotelsearch

WORKDIR /app

COPY --from=builder --chown=hotelsearch:hotelsearch \
    /workspace/application.jar /app/application.jar

USER hotelsearch

EXPOSE 8080

STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
