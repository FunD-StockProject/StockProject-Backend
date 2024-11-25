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

# 1. 레이어 캐싱을 위해 requirements.txt를 먼저 복사
COPY requirements.txt /app/requirements.txt

# 2. Python 패키지 설치
RUN pip3 install --no-cache-dir -r /app/requirements.txt

# 3. 레이어 캐싱을 위해 변경 가능성이 높은 score.py를 나중에 복사
COPY score.py /app/score.py

# 4. Jasypt 암호화 비밀번호 환경 변수 설정
ARG JASYPT_ENCRYPTOR_PASSWORD
ENV JASYPT_ENCRYPTOR_PASSWORD=$JASYPT_ENCRYPTOR_PASSWORD

# 5. JAR 파일 복사
COPY --from=build /app/build/libs/stockProject-0.0.1-SNAPSHOT.jar /app/app.jar

# 6. keystore.p12 파일 복사
COPY keystore.p12 /app/config/keystore.p12

# 7. 컨테이너 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]