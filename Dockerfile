# ===== Build stage =====
FROM gradle:7.6.0-jdk17 AS build
WORKDIR /app

COPY build.gradle settings.gradle ./

COPY gradlew ./gradlew
COPY gradle/ ./gradle/
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --version

COPY src ./src

RUN gradle clean bootJar -x test

# ===== Runtime stage =====
FROM openjdk:17-jdk-slim
WORKDIR /app

RUN apt-get update && apt-get install -y \
    python3 python3-pip tzdata \
 && ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
 && echo "Asia/Seoul" > /etc/timezone \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir \
    torch torchvision torchaudio --extra-index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt

COPY scripts/*.py /app/

COPY --from=build /app/build/libs/*.jar /app/app.jar

ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar","--server.port=8080"]
