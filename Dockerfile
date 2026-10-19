FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build /workspace/target/settlement-hub-*.jar app.jar

USER app

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
