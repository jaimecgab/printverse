FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 printverse
COPY --from=build /workspace/target/printverse-*.jar app.jar
USER printverse
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
