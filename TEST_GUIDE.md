# 프론트엔드-백엔드 연동 테스트 가이드

## 🚀 빠른 시작

### 1. 백엔드 서버 실행

**방법 1: Gradle로 실행 (개발 모드)**
```bash
cd /Users/jongwon/Projects/toTheEnd/StockProject-Backend
./gradlew bootRun --args='--server.port=8080 --server.ssl.enabled=false'
```

**방법 2: Docker Compose로 실행**
```bash
cd /Users/jongwon/Projects/toTheEnd/StockProject-Backend
docker-compose up -d
```

**방법 3: JAR 파일로 실행**
```bash
./gradlew bootJar
java -jar build/libs/stockProject-0.0.1-SNAPSHOT.jar --server.port=8080 --server.ssl.enabled=false
```

### 2. 프론트엔드 서버 실행

```bash
cd /Users/jongwon/Projects/toTheEnd/StockProject-Backend/StockProject-Frontend

# .env 파일 생성 (없는 경우)
echo "VITE_BASE_URL=http://localhost:8080" > .env

# 의존성 설치 (최초 1회)
npm install

# 개발 서버 실행
npm run dev
```

프론트엔드는 기본적으로 `http://localhost:5173`에서 실행됩니다.

### 3. API 테스트 스크립트 실행

```bash
cd /Users/jongwon/Projects/toTheEnd/StockProject-Backend
./test-api-integration.sh http://localhost:8080
```

## 📋 수동 테스트 항목

### ✅ 인증 불필요 API

1. **Score API**
   ```bash
   curl http://localhost:8080/904/score/KOREA
   ```
   예상 응답: `{"score": 50}`

2. **Stock API**
   ```bash
   curl http://localhost:8080/stock/rankings/hot
   curl http://localhost:8080/stock/hot/KOREA
   curl http://localhost:8080/stock/autocomplete?keyword=삼성
   ```

3. **Index Score API**
   ```bash
   curl http://localhost:8080/score/index
   ```

4. **Chart API**
   ```bash
   curl "http://localhost:8080/stock/904/chart/KOREA?periodCode=D"
   ```

### 🔐 인증 필요 API (토큰 필요)

1. **Experiment API**
   ```bash
   curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/experiment
   ```

2. **ShortView API**
   ```bash
   curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/shortview
   ```

3. **Preference API**
   ```bash
   curl -X POST -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/preference/bookmark/904
   ```

## 🐛 문제 해결

### 백엔드 서버가 시작되지 않을 때
- 데이터베이스 연결 확인
- JASYPT_ENCRYPTOR_PASSWORD 환경변수 설정 확인
- 포트 충돌 확인 (8080 포트가 사용 중인지)

### 프론트엔드에서 API 호출 실패할 때
- CORS 설정 확인 (CorsConfig.java에서 localhost:5173 허용 확인)
- VITE_BASE_URL 환경변수 확인
- 브라우저 개발자 도구 Network 탭에서 실제 요청 URL 확인

### 응답 형식이 다를 때
- MOCK_DATA_ALIGNMENT_CHECK.md 참고
- 백엔드 DTO 필드명과 타입 확인
- 프론트엔드 TypeScript 타입 정의 확인

## ✅ 성공 확인

프론트엔드 브라우저에서 다음이 정상 작동하면 성공:
- ✅ 종목 검색 (자동완성)
- ✅ 종목 상세 페이지 로드
- ✅ 차트 표시
- ✅ 점수 조회
- ✅ 인덱스 점수 표시
- ✅ 숏뷰 추천 (로그인 후)


