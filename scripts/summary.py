import requests
import sys
import json
import re

# HTML 태그 제거 함수
def clean_html_tags(text):
    return re.sub(r'<br\s*/?>', '\n', text)  # <br> 태그를 줄바꿈으로 대체

# 국내 데이터 처리 함수
def process_korea_data(api_url):
    response = requests.get(api_url)
    if response.status_code == 200:
        data = response.json()
        try:
            # result -> company -> comment -> comments 배열 추출
            comments = data["result"]["company"]["comment"]["comments"][:2]
        except (KeyError, TypeError, AttributeError):
            # comments 조회 시 에러 발생 시 etf -> description 값으로 대체
            try:
                description = data["result"]["etf"]["description"]
                comments = [description] if description else []
            except (KeyError, TypeError, AttributeError):
                # description도 없으면 빈 리스트로 처리
                comments = []
        
        # 최대 2개 저장
        return comments[:2]
    else:
        raise Exception(f"API 요청 실패: {response.status_code}")

# 해외 데이터 처리 함수
def process_oversea_data(api_url):
    response = requests.get(api_url)
    if response.status_code == 200:
        data = response.json()
        summary = data.get("summary", "")
        # <br>로 나뉜 데이터를 리스트로 저장, 최대 2개
        summaries = clean_html_tags(summary).split("\n")
        return [item.strip() for item in summaries if item.strip()][:2]
    else:
        raise Exception(f"API 요청 실패: {response.status_code}")

# 메인 함수
if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(1)

    symbol = sys.argv[1]
    country = sys.argv[2]

    try:
        # API URL 설정
        if country.upper() == 'OVERSEA':
            url = f"https://m.stock.naver.com/front-api/search/autoComplete?query={symbol}%20&target=stock%2Cindex%2Cmarketindicator%2Ccoin%2Cipo"
            response = requests.get(url)
            if response.status_code == 200:
                data = response.json()
                try:
                    reuters_code = data["result"]["items"][0]["reutersCode"]
                except (KeyError, IndexError):
                    raise Exception("items 배열이 비어있거나 reutersCode 키가 존재하지 않습니다.")
            else:
                raise Exception(f"API 요청 실패: {response.status_code}")
            api_url = f"https://api.stock.naver.com/stock/{reuters_code}/overview"
            hot_list = process_oversea_data(api_url)
        else:
            api_url = f"https://wts-info-api.tossinvest.com/api/v2/stock-infos/A{symbol}/overview"
            hot_list = process_korea_data(api_url)

        # 결과 출력
        print(json.dumps({"summarys": hot_list}, ensure_ascii=False, indent=4))

    except Exception as e:
        print(f"오류 발생: {e}")
        sys.exit(1)