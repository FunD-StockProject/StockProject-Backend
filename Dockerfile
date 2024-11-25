# Build stage
FROM gradle:7.6.0-jdk17 AS build
WORKDIR /app
COPY --chown=gradle:gradle . /app
RUN gradle build -x test

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app

# Python3 및 pip 설치
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Python 패키지 설치
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt

# Hugging Face 모델과 토크나이저 데이터 미리 다운로드
RUN python3 -c "\
from transformers import AutoTokenizer, AutoModelForSequenceClassification; \
tokenizer = AutoTokenizer.from_pretrained('klue/roberta-small'); \
model = AutoModelForSequenceClassification.from_pretrained('klue/roberta-small')"

# score.py 복사
COPY score.py /app/score.py

# Jasypt 암호화 비밀번호 환경 변수 설정
ARG JASYPT_ENCRYPTOR_PASSWORD
ENV JASYPT_ENCRYPTOR_PASSWORD=$JASYPT_ENCRYPTOR_PASSWORD

# JAR 파일 복사
COPY --from=build /app/build/libs/stockProject-0.0.1-SNAPSHOT.jar /app/app.jar

# keystore.p12 파일 복사
COPY keystore.p12 /app/config/keystore.p12

# ENTRYPOINT 설정
ENTRYPOINT ["java", "-jar", "/app/app.jar"]