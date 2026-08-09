FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY apex-be/pom.xml apex-be/pom.xml
RUN mvn -f apex-be/pom.xml dependency:go-offline -B

COPY apex-be/src apex-be/src
RUN mvn -f apex-be/pom.xml package -DskipTests -B \
    && jar_path="$(find apex-be/target -maxdepth 1 -type f -name 'apex-be-*.jar' ! -name '*.original' -print -quit)" \
    && test -n "$jar_path" \
    && cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    PYTHONUNBUFFERED=1 \
    PYTHONIOENCODING=utf-8 \
    PYTHONUTF8=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl python3 python3-pip \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system apex \
    && useradd --system --gid apex --home-dir /app --create-home apex

WORKDIR /app
COPY scripts/market_data/requirements.txt scripts/market_data/requirements.txt
RUN python3 -m pip install --no-cache-dir -r scripts/market_data/requirements.txt

COPY --from=builder /workspace/app.jar apex-be/app.jar
COPY scripts/market_data scripts/market_data
RUN mkdir -p /app/.mx_output /app/apex-be/logs \
    && chown -R apex:apex /app

USER apex
WORKDIR /app/apex-be
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/apex-be/app.jar"]
