# 프론트엔드-백엔드 연동 테스트 결과

## 📋 테스트 준비 사항

### 백엔드 서버 실행

```bash
cd /Users/jongwon/Projects/toTheEnd/StockProject-Backend

# 방법 1: Gradle로 실행
./gradlew bootRun --args='--server.port=8080 --server.ssl.enabled=false'

# 방법 2: JAR 파일로 실행
./gradlew bootJar
java -jar build/libs/stockProject-0.0.1-SNAPSHOT.jar --server.port=8080 --server.ssl.enabled=false
```

**⚠️ 주의사항:**
- 데이터베이스 연결이 필요합니다
- JASYPT_ENCRYPTOR_PASSWORD 환경변수가 필요합니다
- 서버 시작에는 몇 분 정도 걸릴 수 있습니다

### 프론트엔드 설정

```bash
cd /Users/jongwon/Projects/toTheEnd/StockProject-Backend/StockProject-Frontend

# 환경변수 설정
echo "VITE_BASE_URL=http://localhost:8080" > .env

# 서버 실행
npm install  # 최초 1회만
npm run dev
```

## ✅ 연동 완료 확인 사항

### 1. 경로 매칭 확인 완료
- ✅ `GET /{id}/score/{country}` - ScoreController 수정 완료
- ✅ `GET /stock/{id}/chart/{country}` - country 파라미터 추가 완료
- ✅ `GET /shortview` - 기존 엔드포인트 사용
- ✅ `POST /preference/hide/{stockId}` - ShortView hide 연동
- ✅ `POST /experiment/{stockId}/buy/{country}` - ShortView buy 연동

### 2. DTO 구조 확인 완료
- ✅ ScoreResponse - 일치
- ✅ ScoreIndexResponse - 일치
- ✅ StockDetailResponse - 일치
- ✅ StockChartResponse - 일치 (타입 차이 있지만 호환 가능)
- ✅ StockRelevantResponse / StockDiffResponse - 일치

## 🧪 테스트해야 할 주요 기능

### 인증 불필요 API
1. **Score 조회**: `GET /{id}/score/{country}`
2. **종목 검색**: `GET /stock/search/{keyword}/{country}`
3. **자동완성**: `GET /stock/autocomplete?keyword=삼성`
4. **차트 조회**: `GET /stock/{id}/chart/{country}?periodCode=D`
5. **인덱스 점수**: `GET /score/index`
6. **인기 검색어**: `GET /stock/rankings/hot`

### 인증 필요 API (로그인 후)
1. **숏뷰 추천**: `GET /shortview`
2. **숏뷰 숨김**: `POST /preference/hide/{stockId}`
3. **숏뷰 매수**: `POST /experiment/{stockId}/buy/{country}`
4. **북마크 목록**: `GET /preference/bookmark/list`
5. **실험 현황**: `GET /experiment`

## 📝 테스트 체크리스트

서버가 시작된 후 다음을 확인하세요:

- [ ] 백엔드 서버가 정상 실행 중인지 확인
- [ ] `curl http://localhost:8080/904/score/KOREA` 테스트
- [ ] 프론트엔드 브라우저에서 종목 검색 동작 확인
- [ ] 종목 상세 페이지에서 점수, 차트가 표시되는지 확인
- [ ] 숏뷰 페이지에서 추천 종목이 표시되는지 확인 (로그인 후)
- [ ] 숏뷰에서 숨김/매수 기능이 동작하는지 확인

## 🐛 예상 문제 및 해결 방법

1. **CORS 에러**
   - CorsConfig.java에서 localhost:5173 허용 확인
   - 프론트엔드 VITE_BASE_URL 확인

2. **404 에러**
   - 백엔드 경로와 프론트엔드 호출 경로 일치 확인
   - ScoreController 경로 확인

3. **401 Unauthorized**
   - 인증 토큰 확인
   - 토큰 재발급 로직 확인

4. **데이터 형식 불일치**
   - MOCK_DATA_ALIGNMENT_CHECK.md 참고
   - 브라우저 개발자 도구에서 응답 구조 확인


