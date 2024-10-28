# Build stage
FROM gradle:7.6.0-jdk17 AS build
WORKDIR /app
COPY --chown=gradle:gradle . /app
RUN gradle build -x test

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/
ENTRYPOINT ["java", "-jar", "/app/*.jar"]