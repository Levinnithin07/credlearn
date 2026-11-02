FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
# Regression tests require a separate MySQL instance and run before deployment.
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system credlearn && useradd --system --gid credlearn credlearn
COPY --from=build --chown=credlearn:credlearn /build/target/credlearn-0.1.0-SNAPSHOT.jar /app/app.jar
COPY --chown=credlearn:credlearn scripts/container-entrypoint.sh /app/start.sh
USER credlearn
ENV SPRING_PROFILES_ACTIVE=hosted
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx192m -Xss512k -XX:+UseSerialGC -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=16m -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
ENTRYPOINT ["sh", "/app/start.sh"]
