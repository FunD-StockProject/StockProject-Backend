# Build stage
FROM gradle:7.6.0-jdk17 AS build
WORKDIR /app
COPY --chown=gradle:gradle . /app
RUN gradle build -x test

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app

# Python3 및 pip 설치, 시간대 설정
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    tzdata \
    && ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# 2. PyTorch 및 관련 라이브러리 설치 (CPU 전용)
RUN pip3 install --no-cache-dir \
    torch torchvision torchaudio --extra-index-url https://download.pytorch.org/whl/cpu

# 3. Python 패키지 설치 (Transformers 및 Requests 등)
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt

## Hugging Face 모델과 토크나이저 데이터 미리 다운로드
#RUN python3 -c "\
#from transformers import AutoTokenizer, AutoModelForSequenceClassification; \
#tokenizer = AutoTokenizer.from_pretrained('klue/roberta-small'); \
#model = AutoModelForSequenceClassification.from_pretrained('klue/roberta-small')"

# 4. 변경 가능성이 높은 파일 복사
COPY score.py /app/score.py
COPY wc.py /app/wc.py
COPY update.py /app/update.py
COPY stockindex.py /app/stockindex.py
COPY hotsearch.py /app/hotsearch.py
COPY summary.py /app/summary.py
COPY fear-and-greed-0.4.tar.gz /app/fear-and-greed-0.4.tar.gz

# tar.gz 압축 해제 및 설치
RUN pip3 install /app/fear-and-greed-0.4.tar.gz

# 5. Jasypt 암호화 비밀번호 환경 변수 설정
ARG JASYPT_ENCRYPTOR_PASSWORD
ENV JASYPT_ENCRYPTOR_PASSWORD=$JASYPT_ENCRYPTOR_PASSWORD

# 6. JAR 파일 복사
COPY --from=build /app/build/libs/stockProject-0.0.1-SNAPSHOT.jar /app/app.jar

# 7. keystore.p12 파일 복사
COPY keystore.p12 /app/config/keystore.p12

# 8. Apple .p8 파일 복사
COPY AuthKey_DKATK95R7J_humanzipyo.p8 /app/config/AuthKey_DKATK95R7J_humanzipyo.p8

# 9. FCM 서비스 계정 키 파일 복사
COPY humanzipyo-fcm-service-account.json /app/config/humanzipyo-fcm-service-account.json

# 9-1. 복사된 파일에 읽기 권한 부여
RUN chmod 644 /app/config/humanzipyo-fcm-service-account.json

# 10. 실행 파일 최소화
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
