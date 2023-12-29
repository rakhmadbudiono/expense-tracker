FROM gradle:7.3.3-jdk11 AS build

WORKDIR /app
COPY . .

RUN gradle clean build

FROM openjdk:11-jdk-slim

WORKDIR /app
COPY --from=build /app/build/libs/expense-tracker-0.0.1-SNAPSHOT.jar app.jar

CMD ["java", "-jar", "app.jar"]
