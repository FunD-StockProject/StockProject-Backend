# Build stage
FROM gradle:7.6.0-jdk17 AS build
WORKDIR /app
COPY --chown=gradle:gradle . /app
RUN gradle build -x test

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app
# 특정 JAR 파일 이름을 지정하여 복사
COPY --from=build /app/build/libs/stockProject-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]