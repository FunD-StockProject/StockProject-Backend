#!/bin/bash

# 프론트엔드-백엔드 연동 테스트 스크립트
# 사용법: ./test-api-integration.sh [BACKEND_URL]
# 예: ./test-api-integration.sh http://localhost:8080

BACKEND_URL=${1:-http://localhost:8080}
echo "🔍 백엔드 URL: $BACKEND_URL"
echo "=========================================="

# 테스트 함수
test_endpoint() {
    local method=$1
    local endpoint=$2
    local description=$3
    local data=$4
    
    echo ""
    echo "📌 테스트: $description"
    echo "   $method $endpoint"
    
    if [ "$method" = "GET" ]; then
        response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" "$BACKEND_URL$endpoint")
    else
        response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X "$method" "$BACKEND_URL$endpoint" \
            -H "Content-Type: application/json" \
            -d "$data")
    fi
    
    http_code=$(echo "$response" | grep "HTTP_STATUS" | cut -d: -f2)
    body=$(echo "$response" | sed '/HTTP_STATUS/d')
    
    if [ "$http_code" = "200" ] || [ "$http_code" = "204" ]; then
        echo "   ✅ 성공 (HTTP $http_code)"
        if [ ! -z "$body" ] && [ "$body" != "null" ]; then
            echo "   응답: $(echo "$body" | head -c 200)..."
        fi
    else
        echo "   ❌ 실패 (HTTP $http_code)"
        echo "   응답: $body"
    fi
}

echo "🚀 프론트엔드-백엔드 연동 테스트 시작"
echo "=========================================="

# 1. Score API 테스트
echo ""
echo "📊 Score API 테스트"
test_endpoint "GET" "/904/score/KOREA" "Score 조회 (삼성전자, KOREA)"

# 2. Stock API 테스트
echo ""
echo "📈 Stock API 테스트"
test_endpoint "GET" "/stock/rankings/hot" "인기 검색어 조회"
test_endpoint "GET" "/stock/hot/KOREA" "Hot 종목 조회 (KOREA)"
test_endpoint "GET" "/stock/autocomplete?keyword=삼성" "자동완성 테스트"
test_endpoint "GET" "/stock/search/삼성전자/KOREA" "종목 검색"

# 3. Keyword API 테스트
echo ""
echo "🔤 Keyword API 테스트"
test_endpoint "GET" "/keyword/popular/KOREA" "인기 키워드 조회"
test_endpoint "GET" "/keyword/rankings" "키워드 순위 조회"

# 4. Index Score API 테스트
echo ""
echo "📊 Index Score API 테스트"
test_endpoint "GET" "/score/index" "Index 점수 조회"

# 5. WordCloud API 테스트
echo ""
echo "☁️ WordCloud API 테스트"
test_endpoint "GET" "/wordcloud/005930/KOREA" "워드클라우드 조회"

# 6. Chart API 테스트 (country 파라미터 확인)
echo ""
echo "📉 Chart API 테스트"
test_endpoint "GET" "/stock/904/chart/KOREA?periodCode=D" "차트 조회 (KOREA)"

echo ""
echo "=========================================="
echo "✅ 테스트 완료!"
echo ""
echo "💡 인증이 필요한 API 테스트:"
echo "   - GET /experiment (실험 현황)"
echo "   - GET /shortview (숏뷰 추천)"
echo "   - POST /preference/bookmark/{stockId} (북마크)"
echo ""
echo "💡 백엔드 서버 실행 방법:"
echo "   ./gradlew bootRun --args='--server.port=8080 --server.ssl.enabled=false'"
echo "   또는"
echo "   java -jar build/libs/stockProject-0.0.1-SNAPSHOT.jar --server.port=8080 --server.ssl.enabled=false"

