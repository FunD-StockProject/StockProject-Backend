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
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update && apt-get install -y \
    python3 python3-pip python3-venv tzdata \
 && ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
 && echo "Asia/Seoul" > /etc/timezone \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

ENV VIRTUAL_ENV=/opt/venv
RUN python3 -m venv "$VIRTUAL_ENV"
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

RUN python -m pip install --upgrade pip setuptools wheel

RUN pip install --no-cache-dir \
    --index-url https://download.pytorch.org/whl/cpu \
    torch torchvision torchaudio

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

# Python 스크립트와 필요한 모듈만 복사 (scripts 폴더에 stocks_info 포함)
COPY scripts/ /app/scripts/

COPY --from=build /app/build/libs/*.jar /app/app.jar

# 로그 디렉토리 생성 (볼륨 마운트를 위한 준비)
RUN mkdir -p /app/logs && chmod 777 /app/logs

ENV TZ=Asia/Seoul
ENV LOG_PATH=/app/logs

# 로그 디렉토리를 볼륨으로 마운트 가능하도록 설정
# docker run 시 -v $(pwd)/logs:/app/logs 옵션으로 호스트의 logs 디렉토리에 마운트 가능
VOLUME ["/app/logs"]

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar","--server.port=8080"]
