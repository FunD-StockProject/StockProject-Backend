# Lab(실험실) 기능 프론트엔드 연동 가이드

## 목차
1. [개요](#개요)
2. [API 엔드포인트 목록](#api-엔드포인트-목록)
3. [화면별 연동 가이드](#화면별-연동-가이드)
4. [데이터 매핑 예시](#데이터-매핑-예시)

---

## 개요

Lab(실험실) 기능은 사용자가 주식을 모의 매수하고, 5영업일 후 자동으로 매도되어 수익률을 확인할 수 있는 기능입니다.

**주요 화면 구조:**
- `Lab.tsx` - 실험실 메인 페이지 (매수현황/매수결과 탭)
- `StockSelection.tsx` - 종목 선택 화면
- `StockPurchase.tsx` - 매수 실행 화면
- `StockRecordSheet.tsx` - 실험 기록 목록 화면
- `LabResult.tsx` - 실험 결과 상세 화면

**모든 API는 인증이 필요합니다.** (`Authorization: Bearer {token}` 헤더 포함)

---

## API 엔드포인트 목록

### 실험(Experiment) API

| 메서드 | 엔드포인트 | 설명 | 사용 화면 |
|--------|-----------|------|----------|
| `GET` | `/experiment` | 실험 현황 조회 (alias) | Lab.tsx |
| `GET` | `/experiment/status` | 실험 현황 조회 | Lab.tsx |
| `GET` | `/experiment/status/{experimentId}/detail` | 실험 상세 조회 | ExperimentDetailBottomSheet |
| `POST` | `/experiment/{stockId}/buy/{country}` | 종목 모의 매수 | StockPurchase.tsx |
| `GET` | `/experiment/report` | 실험 리포트 조회 | LabResult.tsx (report) |

### 포트폴리오(Portfolio) API

| 메서드 | 엔드포인트 | 설명 | 사용 화면 |
|--------|-----------|------|----------|
| `GET` | `/portfolio/report` | 실험 리포트 조회 (alias) | LabResult.tsx |
| `GET` | `/portfolio/result` | 실험 결과 상세 조회 | LabResult.tsx |

### 종목(Stock) API (Lab에서 사용)

| 메서드 | 엔드포인트 | 설명 | 사용 화면 |
|--------|-----------|------|----------|
| `GET` | `/stock/autocomplete?keyword={keyword}` | 종목 검색 자동완성 | StockSearch.tsx |
| `GET` | `/stock/rankings/hot` | 인기 검색 종목 | StockSearch.tsx |
| `GET` | `/stock/{id}/info/{country}` | 종목 상세 정보 | StockPurchase.tsx |

---

## 화면별 연동 가이드

### 1. Lab.tsx (실험실 메인)

#### 1.1 매수현황 탭

**필요한 API:**
- `GET /experiment` 또는 `GET /experiment/status`

**연동 예시:**

```typescript
// src/controllers/api/portfolio.ts
export const fetchExperiment = () =>
  fetchAuthData(`/experiment`); // 또는 `/experiment/status`

// src/controllers/query/portfolio.ts
export const useExperimentQuery = () => {
  return useQuery(
    ['experiment'],
    fetchExperiment,
    queryOptions
  );
};

// src/pages/Lab/Lab.tsx
import { useExperimentQuery } from '@controllers/query/portfolio';

const Lab = () => {
  const { data: experimentStatus, isLoading } = useExperimentQuery();
  
  // 초기 진입 여부 체크
  const isFirstTime = !experimentStatus || experimentStatus.totalTradeCount === 0;
  
  if (isLoading) return <Loading />;
  
  return (
    <Container>
      <TabContainer>
        <Tab selected={selectedTab === '현황'}>매수현황</Tab>
        <Tab selected={selectedTab === '결과'}>매수결과</Tab>
      </TabContainer>
      
      {selectedTab === '현황' ? (
        <>
          {isFirstTime ? (
            // 첫 방문 UI
          ) : (
            <>
              <SummarySection>
                <SummaryCard>
                  <SummaryLabel>총 실험 수</SummaryLabel>
                  <SummaryValue>{experimentStatus.totalTradeCount}회</SummaryValue>
                </SummaryCard>
                <SummaryCard>
                  <SummaryLabel>성공률</SummaryLabel>
                  <SummaryValue>{experimentStatus.successRate.toFixed(1)}%</SummaryValue>
                </SummaryCard>
                <SummaryCard>
                  <SummaryLabel>평균 수익률</SummaryLabel>
                  <SummaryValue>{experimentStatus.avgRoi.toFixed(2)}%</SummaryValue>
                </SummaryCard>
              </SummarySection>
              
              <StatusSection>
                <StatusTitle>
                  진행중인 실험 {experimentStatus.progressExperiments.length} 회
                </StatusTitle>
                <ExperimentList experiment={mapToExperimentItems(experimentStatus.progressExperiments)} />
              </StatusSection>
            </>
          )}
        </>
      ) : (
        <LabResult />
      )}
    </Container>
  );
};
```

**응답 구조:**

```typescript
interface ExperimentStatusResponse {
  progressExperiments: ExperimentInfoResponse[]; // 진행중인 실험
  completeExperiments: ExperimentInfoResponse[]; // 완료된 실험
  avgRoi: number;                                // 평균 수익률
  totalTradeCount: number;                       // 총 실험 수
  progressTradeCount: number;                    // 진행중인 실험 수
  successRate: number;                           // 성공률 (%)
}

interface ExperimentInfoResponse {
  experimentId: number;
  symbolName: string;
  buyAt: string; // LocalDateTime (ISO 형식)
  buyPrice: number;
  roi: number;
  status: "PROGRESS" | "COMPLETED";
  country: "KOREA" | "OVERSEA";
}
```

#### 1.2 매수결과 탭

**필요한 API:**
- `GET /portfolio/result`

**연동 예시:**

```typescript
// src/controllers/api/portfolio.ts
export const fetchResult = () =>
  fetchAuthData(`/portfolio/result`);

// src/controllers/query/portfolio.ts
export const useResultQuery = () => {
  return useQuery(
    ['portfolioResult'],
    fetchResult,
    queryOptions
  );
};

// src/components/LabResult/LabResult.tsx
import { useResultQuery } from '@controllers/query/portfolio';

const LabResult = () => {
  const { data: resultData, isLoading } = useResultQuery();
  
  if (isLoading) return <Loading />;
  if (!resultData) return <EmptyState />;
  
  return (
    <Container>
      <ScoreTable data={resultData.scoreTable} />
      <ExperimentSummary {...resultData.experimentSummary} />
      <HumanIndexSection {...resultData.humanIndex} />
      <InvestmentPatternSection {...resultData.investmentPattern} />
      <HistorySection data={resultData.history} />
    </Container>
  );
};
```

---

### 2. StockPurchase.tsx (매수 실행 화면)

**필요한 API:**
- `POST /experiment/{stockId}/buy/{country}` - 종목 매수
- `GET /stock/{id}/info/{country}` - 종목 정보 조회 (이미 구현됨)

**연동 예시:**

```typescript
// src/controllers/api/portfolio.ts
export const postExperimentBuy = (stockId: number, country: string) =>
  fetchAuthData(`/experiment/${stockId}/buy/${country}`, { method: 'POST' });

// src/components/Lab/StockPurchase/StockPurchase.tsx
import { postExperimentBuy } from '@controllers/api/portfolio';
import { useMutation, useQueryClient } from 'react-query';

const StockPurchase = () => {
  const queryClient = useQueryClient();
  const [purchasedStocks, setPurchasedStocks] = useState<string[]>([]);
  
  const buyMutation = useMutation(
    ({ stockId, country }: { stockId: number; country: string }) =>
      postExperimentBuy(stockId, country),
    {
      onSuccess: (response) => {
        if (response.success) {
          // 매수 성공 처리
          setPurchasedStocks(prev => [...prev, stockInfo.symbol]);
          queryClient.invalidateQueries(['experiment']); // 실험 목록 갱신
          showToast(`${stockInfo.symbolName} ${formatPrice(response.price)}로 매수 완료되었습니다.`);
        } else {
          showToast(response.message); // "같은 종목 중복 구매" 등
        }
      },
      onError: (error) => {
        showToast('매수에 실패했습니다.');
      }
    }
  );
  
  const handlePurchase = (stockInfo: StockDetailInfo) => {
    if (purchasedStocks.includes(stockInfo.symbol)) return;
    
    buyMutation.mutate({
      stockId: stockInfo.stockId,
      country: stockInfo.country
    });
  };
  
  return (
    <StockCard>
      <PurchaseButton
        onClick={() => handlePurchase(stockInfo)}
        purchased={purchasedStocks.includes(stockInfo.symbol)}
        disabled={purchasedStocks.includes(stockInfo.symbol) || buyMutation.isLoading}
      >
        {purchasedStocks.includes(stockInfo.symbol) ? '매수완료' : '매수하기'}
      </PurchaseButton>
    </StockCard>
  );
};
```

**응답 구조:**

```typescript
interface ExperimentSimpleResponse {
  success: boolean;
  message: string;  // "모의 매수 성공" 또는 "같은 종목 중복 구매"
  price: number;     // 실제 매수 가격
}
```

---

### 3. StockRecordSheet.tsx (실험 기록 목록)

**필요한 API:**
- `GET /experiment/status` - 실험 현황 조회 (위에서 이미 구현)

**연동 예시:**

```typescript
// src/pages/StockRecordSheet/StockRecordSheet.tsx
import { useExperimentQuery } from '@controllers/query/portfolio';

const StockRecordSheet = () => {
  const { data: experimentStatus } = useExperimentQuery();
  const [statusFilter, setStatusFilter] = useState<'active' | 'completed'>('active');
  
  // 필터링된 실험 목록
  const filteredExperiments = useMemo(() => {
    const experiments = statusFilter === 'active' 
      ? experimentStatus?.progressExperiments || []
      : experimentStatus?.completeExperiments || [];
    
    // 정렬 옵션에 따라 정렬
    return experiments.sort((a, b) => {
      // 정렬 로직
    });
  }, [experimentStatus, statusFilter]);
  
  return (
    <Container>
      <FilterTabs>
        <FilterTab 
          selected={statusFilter === 'active'}
          onClick={() => setStatusFilter('active')}
        >
          진행중
        </FilterTab>
        <FilterTab 
          selected={statusFilter === 'completed'}
          onClick={() => setStatusFilter('completed')}
        >
          완료
        </FilterTab>
      </FilterTabs>
      
      <ExperimentList experiment={mapToExperimentItems(filteredExperiments)} />
    </Container>
  );
};
```

---

### 4. ExperimentDetailBottomSheet.tsx (실험 상세)

**필요한 API:**
- `GET /experiment/status/{experimentId}/detail`

**연동 예시:**

```typescript
// src/controllers/api/portfolio.ts
export const fetchExperimentDetail = (experimentId: number) =>
  fetchAuthData(`/experiment/status/${experimentId}/detail`);

// src/controllers/query/portfolio.ts
export const useExperimentDetailQuery = (experimentId: number) => {
  return useQuery(
    ['experimentDetail', experimentId],
    () => fetchExperimentDetail(experimentId),
    {
      ...queryOptions,
      enabled: !!experimentId
    }
  );
};

// src/components/Lab/StockRecordSheet/ExperimentList/ExperimentDetailBottomSheet.tsx
import { useExperimentDetailQuery } from '@controllers/query/portfolio';

const ExperimentDetailBottomSheet = ({ experimentId, isOpen, onClose }) => {
  const { data: detailData, isLoading } = useExperimentDetailQuery(experimentId);
  
  if (!isOpen || !detailData) return null;
  
  return (
    <BottomSheet isOpen={isOpen} onClose={onClose}>
      <SummarySection>
        <SummaryLabel>종목명</SummaryLabel>
        <SummaryValue>{detailData.symbolName}</SummaryValue>
      </SummarySection>
      
      <SummarySection>
        <SummaryLabel>최종 수익률</SummaryLabel>
        <SummaryValue>{detailData.roi.toFixed(2)}%</SummaryValue>
      </SummarySection>
      
      <ChartSection>
        {detailData.tradeInfos.map((trade, index) => (
          <ChartPoint
            key={index}
            x={trade.tradeAt}
            y={trade.roi}
            price={trade.price}
            score={trade.score}
          />
        ))}
      </ChartSection>
    </BottomSheet>
  );
};
```

**응답 구조:**

```typescript
interface ExperimentStatusDetailResponse {
  symbolName: string;
  roi: number;                        // 최종 수익률
  status: "PROGRESS" | "COMPLETED";
  tradeInfos: TradeInfo[];            // 거래 내역 (일별)
}

interface TradeInfo {
  price: number;                       // 가격
  score: number;                      // 인간지표 점수
  tradeAt: string;                    // 거래일 (LocalDateTime ISO)
  roi: number;                        // 해당 시점 수익률
}
```

---

### 5. StockSearch.tsx (종목 검색)

**필요한 API:**
- `GET /stock/autocomplete?keyword={keyword}` - 이미 구현됨
- `GET /stock/rankings/hot` - 이미 구현됨

**기존 코드 확인:**
- `fetchAutoComplete` 사용 중 ✅
- `usePopularStockFetchQuery` 사용 중 ✅

**추가 작업 없음** (이미 연동 완료)

---

## 데이터 매핑 예시

### ExperimentInfoResponse → ExperimentItem 변환

```typescript
// src/utils/experimentMapper.ts
interface ExperimentItem {
  id: number;
  name: string;
  logo: string;
  buyPrice: number;
  buyScore: number;
  currentPrice: number;
  currentScore: number;
  autoSellIn: number;
  buyDate: string; // "24.11.01" 형식
}

export const mapToExperimentItem = (
  exp: ExperimentInfoResponse,
  stockInfo?: StockDetailInfo // 현재 가격/점수를 위해 추가 조회 필요
): ExperimentItem => {
  // buyAt을 "24.11.01" 형식으로 변환
  const buyDate = formatDate(exp.buyAt); // "YYYY.MM.DD" → "YY.MM.DD"
  
  // autoSellIn 계산 (5영업일 기준)
  const buyDateTime = new Date(exp.buyAt);
  const today = new Date();
  const businessDaysPassed = calculateBusinessDays(buyDateTime, today);
  const autoSellIn = Math.max(0, 5 - businessDaysPassed);
  
  return {
    id: exp.experimentId,
    name: exp.symbolName,
    logo: getStockLogoUrl(exp.symbolName, exp.country), // 로고 URL 가져오기
    buyPrice: exp.buyPrice,
    buyScore: getBuyScore(exp.experimentId), // 매수 시점 점수 (추가 조회 필요)
    currentPrice: stockInfo?.price || exp.buyPrice,
    currentScore: stockInfo?.score || getBuyScore(exp.experimentId),
    autoSellIn,
    buyDate
  };
};

// 여러 개 변환
export const mapToExperimentItems = (
  experiments: ExperimentInfoResponse[]
): ExperimentItem[] => {
  return experiments.map(exp => mapToExperimentItem(exp));
};
```

### 날짜 포맷팅 헬퍼

```typescript
// src/utils/dateFormatter.ts
export const formatDateForDisplay = (dateString: string): string => {
  const date = new Date(dateString);
  const year = date.getFullYear().toString().slice(-2);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}.${month}.${day}`;
};

export const calculateBusinessDays = (start: Date, end: Date): number => {
  let count = 0;
  const current = new Date(start);
  
  while (current <= end) {
    const dayOfWeek = current.getDay();
    if (dayOfWeek !== 0 && dayOfWeek !== 6) { // 일요일(0), 토요일(6) 제외
      count++;
    }
    current.setDate(current.getDate() + 1);
  }
  
  return count;
};
```

---

## 주요 주의사항

### 1. 인증 토큰
모든 API 호출 시 `Authorization: Bearer {token}` 헤더가 필요합니다. `fetchAuthData` 함수를 사용하면 자동으로 포함됩니다.

### 2. 에러 처리
- `401 Unauthorized`: 토큰 재발급 또는 로그인 페이지로 이동
- `400 Bad Request`: 요청 파라미터 검증 실패
- 중복 매수: `POST /experiment/{stockId}/buy/{country}`에서 `success: false`, `message: "같은 종목 중복 구매"` 반환

### 3. 데이터 갱신
매수 완료 후 실험 목록이 자동으로 갱신되도록 `queryClient.invalidateQueries(['experiment'])`를 호출하세요.

### 4. 로딩/에러 상태
각 화면에서 `isLoading`, `isError` 상태를 체크하여 적절한 UI를 보여주세요.

### 5. 빈 데이터 처리
- 실험이 없는 경우 (`totalTradeCount === 0`): 첫 방문 안내 UI 표시
- 진행중인 실험이 없는 경우: 빈 목록 또는 안내 메시지 표시

---

## 체크리스트

- [ ] `Lab.tsx`에 `useExperimentQuery()` 연결
- [ ] `StockPurchase.tsx`에 `POST /experiment/{stockId}/buy/{country}` 호출 추가
- [ ] `ExperimentDetailBottomSheet`에 `useExperimentDetailQuery()` 연결
- [ ] `LabResult.tsx`에 `useResultQuery()` 연결
- [ ] Mock 데이터 제거 및 실제 API 응답으로 교체
- [ ] 데이터 매핑 유틸 함수 구현 (`mapToExperimentItem` 등)
- [ ] 에러 처리 및 로딩 상태 UI 추가
- [ ] 날짜 포맷팅 헬퍼 함수 구현
- [ ] 매수 완료 후 실험 목록 자동 갱신 구현

---

## 추가 참고사항

### 백엔드 응답 예시

**GET /experiment/status**
```json
{
  "progressExperiments": [
    {
      "experimentId": 1,
      "symbolName": "삼성전자",
      "buyAt": "2024-11-01T09:00:00",
      "buyPrice": 70000,
      "roi": 5.5,
      "status": "PROGRESS",
      "country": "KOREA"
    }
  ],
  "completeExperiments": [],
  "avgRoi": 3.2,
  "totalTradeCount": 5,
  "progressTradeCount": 3,
  "successRate": 60.0
}
```

**POST /experiment/{stockId}/buy/{country}**
```json
{
  "success": true,
  "message": "모의 매수 성공",
  "price": 70000.0
}
```

**GET /portfolio/result**
```json
{
  "scoreTable": [
    {
      "range": "60점 이하",
      "avg": -2.3,
      "median": -1.8
    },
    {
      "range": "60-70점",
      "avg": 1.2,
      "median": 0.9
    }
  ],
  "experimentSummary": {
    "totalExperiments": 12,
    "highestProfit": {
      "score": 78,
      "range": "70-80점"
    },
    "lowestProfit": {
      "score": 65,
      "range": "60-70점"
    }
  },
  "humanIndex": {
    "userScore": 72,
    "userType": "균형형",
    "successRate": "41~60%",
    "maintainRate": "25%",
    "purchasedCount": 12,
    "profitCount": 5
  },
  "investmentPattern": {
    "patternType": "가치 선점형",
    "patternDescription": "높아지기 전 구간에서 선제 진입을 선호"
  },
  "history": [
    {
      "x": 65,
      "y": 1.5,
      "label": "1101"
    }
  ]
}
```

---

문의사항이나 추가 정보가 필요하시면 백엔드 개발팀에 문의해주세요.

