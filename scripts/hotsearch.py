import requests
import json

# API URL
url = "https://wts-info-api.tossinvest.com/api/v1/rankings/realtime/stock?size=20"

try:
    # API 요청
    response = requests.get(url)
    response.raise_for_status()  # HTTP 상태 코드 확인

    # JSON 데이터 파싱
    data = response.json()

    # "symbol" 값 추출
    hot_list = [item["symbol"] for item in data["result"]["data"]]

    # JSON 형식으로 출력
    print(json.dumps({"hot_stocks": hot_list}, ensure_ascii=False))

except requests.exceptions.RequestException as e:
    print(f"API 요청 중 오류 발생: {e}")
except KeyError as e:
    print(f"데이터 처리 중 오류 발생: {e}")