# Build stage
FROM gradle:7.6.0-jdk17 AS build
WORKDIR /app
COPY --chown=gradle:gradle . /app
RUN gradle build -x test

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app

# 1. 불필요한 캐시 제거를 위한 빌드 최적화
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# 2. Python 패키지 레이어 캐싱
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt

# 3. 변경 가능성이 높은 파일은 마지막에 복사
COPY score.py /app/score.py

# 4. Jasypt 암호화 비밀번호 환경 변수 설정
ARG JASYPT_ENCRYPTOR_PASSWORD
ENV JASYPT_ENCRYPTOR_PASSWORD=$JASYPT_ENCRYPTOR_PASSWORD

# 5. JAR 파일 복사
COPY --from=build /app/build/libs/stockProject-0.0.1-SNAPSHOT.jar /app/app.jar

# 6. keystore.p12 파일 복사
COPY keystore.p12 /app/config/keystore.p12

# 7. 불필요한 파일 제거
RUN apt-get purge -y --auto-remove \
    && rm -rf /tmp/*

# 8. 실행 파일 최소화
ENTRYPOINT ["java", "-jar", "/app/app.jar"]