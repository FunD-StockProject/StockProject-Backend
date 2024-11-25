# Build stage
FROM gradle:7.6.0-jdk17 AS build
WORKDIR /app
COPY --chown=gradle:gradle . /app
RUN gradle build -x test

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app

# Python3 및 pip 설치 (추가)
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# 필요한 Python 패키지 설치 (score.py 실행에 필요한 모듈)
COPY config/requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt

# EC2의 score.py 파일을 컨테이너로 복사
COPY config/score.py /app/score.py

# Jasypt 암호화 비밀번호 환경 변수 설정
ARG JASYPT_ENCRYPTOR_PASSWORD
ENV JASYPT_ENCRYPTOR_PASSWORD=$JASYPT_ENCRYPTOR_PASSWORD

# JAR 파일 복사
COPY --from=build /app/build/libs/stockProject-0.0.1-SNAPSHOT.jar /app/app.jar

# keystore.p12 파일 복사
COPY keystore.p12 /app/config/keystore.p12

ENTRYPOINT ["java", "-jar", "/app/app.jar"]