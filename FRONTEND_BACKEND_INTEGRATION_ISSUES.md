# 프론트엔드-백엔드 연동 이슈 정리

## 🔴 긴급 수정 필요

### 1. ScoreController 경로 불일치 ✅ 완료
**문제:**
- 프론트엔드: `/${id}/score/${country}` 호출 (변경 없음)
- 백엔드: `{id}/score/{country}` (RequestMapping 없음 - 루트 경로)

**수정 내용 (백엔드만 수정):**
- ✅ 백엔드 `ScoreController`에서 `@GetMapping("/{id}/score/{country}")`로 경로 수정 (앞에 `/` 추가)
- ✅ `@RequestMapping("/score")` 제거하여 루트 경로로 매핑
- 프론트엔드 수정 없음 (원래 경로 유지)

---

### 2. ShortView 관련 엔드포인트 수정 ✅ 완료
**문제:**
프론트엔드에서 별도 엔드포인트로 호출하려 했지만, 실제로는 기존 엔드포인트로 구현되어 있었음

**해결:**
- ✅ 숨김: `POST /preference/hide/{stockId}` 사용 (기존 구현)
- ✅ 매수: `POST /experiment/{stockId}/buy/{country}` 사용 (기존 구현)
- ✅ 피드: `GET /shortview` 사용 (추천 종목 조회)

**수정 내용:**
- `StockProject-Frontend/src/controllers/api/shortview.ts` 수정 완료
- `StockProject-Frontend/src/controllers/query/shortview.ts` 수정 완료

---

### 3. 차트 API 경로 오류 ✅ 완료
**문제:**
- 프론트엔드: `/stock/${id}/chart/{country}?periodCode=...` (원래 `{country}` 문자열 그대로 전달)
- 백엔드: `/stock/{id}/chart/{country}` (실제 country 값 필요)

**수정 내용:**
- ✅ `StockChart` 컴포넌트는 이미 `country` prop을 받고 있어서 이를 활용하도록 수정
- ✅ `useStockChartQuery`에 `country` 파라미터 추가하여 전달
- ✅ `fetchStockChart` 함수에 `country` 파라미터 추가
- ✅ `useChartInfoQuery`에도 `country` 파라미터 추가
- (프론트엔드에서 이미 country를 사용할 수 있었으므로 최소한의 수정)

---

## ⚠️ 확인 필요 사항

### 4. 실험(Experiment) 매수 엔드포인트 연동
**상황:**
- 백엔드: `POST /experiment/{stockId}/buy/{country}` 존재
- 프론트엔드: `StockPurchase.tsx`에서 로컬 state만 관리, 실제 API 호출 없음

**확인 필요:**
- 프론트엔드에서 실험 매수를 백엔드로 전송하는 로직이 필요한지 확인

---

## ✅ 정상 연동된 엔드포인트

### Stock API
- ✅ `GET /stock/search/{searchKeyword}/{country}`
- ✅ `GET /stock/autocomplete?keyword={keyword}`
- ✅ `GET /stock/hot/{country}`
- ✅ `GET /stock/rising/{country}`
- ✅ `GET /stock/descent/{country}`
- ✅ `GET /stock/{id}/relevant`
- ✅ `GET /stock/{id}/info/{country}`
- ✅ `GET /stock/category/{category}/{country}`
- ✅ `GET /stock/rankings/hot`
- ✅ `GET /stock/summary/{symbol}/{country}`

### Auth API
- ✅ `GET /auth/login/kakao`, `/naver`, `/google`, `/apple`
- ✅ `POST /auth/register`
- ✅ `DELETE /auth/withdraw`
- ✅ `POST /auth/logout`
- ✅ `POST /auth/reissue`

### Preference API
- ✅ `POST /preference/bookmark/{stockId}`
- ✅ `DELETE /preference/bookmark/{stockId}`
- ✅ `POST /preference/hide/{stockId}`
- ✅ `DELETE /preference/hide/{stockId}`
- ✅ `GET /preference/bookmark/list`
- ✅ `GET /preference/bookmark/count`
- ✅ `PATCH /preference/notification/toggle/{stockId}`

### Experiment/Portfolio API
- ✅ `GET /experiment` (또는 `/experiment/status`)
- ✅ `GET /portfolio/report`
- ✅ `GET /portfolio/result`

### Keyword API
- ✅ `GET /keyword/{keywordName}/stocks`
- ✅ `GET /keyword/popular/{country}`
- ✅ `GET /keyword/rankings`

### WordCloud API
- ✅ `GET /wordcloud/{symbol}/{country}`

---

## 수정 우선순위

1. ✅ **완료**: ScoreController 경로 수정 (모든 종목 상세 페이지에서 사용)
2. ✅ **완료**: 차트 API 경로 수정 (차트 기능 동작 안 함)
3. ✅ **완료**: ShortView 엔드포인트 수정 (기존 엔드포인트로 변경 완료)
4. **낮음**: 실험 매수 연동 확인 및 구현

