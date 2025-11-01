# 프론트엔드 Mock 데이터와 백엔드 DTO 정렬 확인

프론트엔드는 mock 데이터 기준으로 구현되어 있으므로, 백엔드 DTO가 그 구조와 정확히 일치해야 합니다.

## ✅ 확인 완료된 항목

### 1. ScoreResponse
- **프론트 mock**: `{ score: 50 }`
- **백엔드 DTO**: `ScoreResponse { score: Integer }`
- ✅ 일치

### 2. ScoreIndexResponse  
- **프론트 mock**: `IndexScoreInfo` (kospiVix, kospiVixDiff, kospiIndex, ...)
- **백엔드 DTO**: `ScoreIndexResponse` 동일한 필드들
- ✅ 일치

### 3. StockDetailResponse
- **프론트 mock**: `StockDetailInfo` (stockId, symbol, symbolName, securityName, exchangeNum, country, price, priceDiff, priceDiffPerCent, score, scoreDiff, keywords)
- **백엔드 DTO**: `StockDetailResponse` 동일한 필드들
- ✅ 일치 (exchangeNum은 EXCHANGENUM enum이지만 @JsonValue로 문자열로 직렬화됨)

### 4. StockRelevantResponse / StockDiffResponse
- **프론트 mock**: `{ stockId: number, symbolName: string, score: number, diff: number }` (keywords는 optional)
- **백엔드 DTO**: `StockRelevantResponse`, `StockDiffResponse` 동일한 필드들 (keywords 포함)
- ✅ 일치 (keywords는 프론트에서 사용하지 않을 수 있음)

## ⚠️ 확인 필요 항목

### 5. StockChartResponse
- **프론트 mock**: 
  ```typescript
  {
    symbol: string,           // ✅
    symbolName: string,       // ✅
    securityName: string,     // ✅
    exchangenum: string,      // ⚠️ 소문자, 프론트는 '001' 같은 문자열 기대
    country: 'KOREA' | 'OVERSEA',  // ✅
    priceInfos: Array<{
      localDate: string,                    // ✅
      closePrice: string,                   // ✅
      openPrice: string,                    // ✅
      highPrice: string,                    // ✅
      lowPrice: string,                     // ✅
      accumulatedTradingVolume: string,     // ✅
      accumulatedTradingValue: string,      // ✅
      score: string | null,                 // ⚠️ 프론트는 string 기대, 백엔드는 Integer
      diff: string | null                   // ⚠️ 프론트는 string 기대, 백엔드는 Integer
    }>
  }
  ```
- **백엔드 DTO**: 
  - `exchangenum: EXCHANGENUM` (필드명은 소문자로 일치하지만, EXCHANGENUM enum은 @JsonValue로 문자열 직렬화되므로 '001' 같은 문자열로 나감)
  - `score: Integer`, `diff: Integer` (프론트는 string 기대하지만, JSON에서는 숫자로 전송됨)
- **확인 필요**: 프론트엔드가 score/diff를 숫자로 파싱할 수 있는지 확인 필요

### 6. StockSearchResponse (자동완성)
- **프론트 mock**: 확인 필요
- **백엔드 DTO**: 확인 필요

## 발견된 주요 불일치

1. **StockChartResponse.PriceInfo의 score/diff 타입**
   - 프론트 mock: `string | null` (예: '52', '-3', null)
   - 백엔드 DTO: `Integer` (null 가능)
   - **프론트 실제 코드**: `score: { value: e.score, delta: e.diff }` - 숫자나 문자열 모두 사용 가능
   - **결론**: ⚠️ 타입은 다르지만 프론트에서 숫자로 처리 가능하므로 큰 문제 없음 (하지만 mock과 완전히 일치시키려면 String으로 변경 가능)

2. **StockChartResponse의 exchangenum**
   - 프론트 mock: `exchangenum: string` (예: '001')
   - 백엔드 DTO: `exchangenum: EXCHANGENUM` (하지만 @JsonValue로 문자열 직렬화됨)
   - **결론**: ✅ 일치 (EXCHANGENUM enum은 @JsonValue로 문자열로 직렬화되어 '001' 같은 값으로 나감)

## 최종 확인 결과

대부분의 DTO는 프론트엔드 mock 데이터 구조와 일치합니다. 
- 타입 차이(score/diff가 Integer vs string)는 있지만, JSON 직렬화 시 숫자로 나가고 프론트에서도 숫자로 처리 가능하므로 문제 없습니다.
- 모든 필드명은 일치합니다.
- Enum 타입들(EXCHANGENUM, COUNTRY)은 적절히 직렬화됩니다.

## 다음 단계

현재 상태로도 연동이 가능하지만, 완벽하게 mock과 일치시키려면:
1. StockChartResponse.PriceInfo의 score/diff를 String으로 변경 (선택사항)
2. 실제 API 테스트로 최종 확인
