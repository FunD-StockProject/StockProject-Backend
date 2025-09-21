import warnings
warnings.filterwarnings("ignore", category=UserWarning, module="urllib3")
warnings.filterwarnings("ignore", message="Some weights of")

import requests
import sys
import json
import re
from collections import Counter

# 1. API를 통해 데이터 받아오기 (가중치 반영 및 전체 텍스트 저장)
def fetch_texts_from_api(api_url):
    headers = {'User-Agent': 'Mozilla/5.0'}
    response = requests.get(api_url, headers=headers)
    if response.status_code == 200:
        data = response.json()
        all_text = ""  # 전체 텍스트를 누적할 변수

        for item in data:
            # 제목과 내용을 결합한 뒤 <br> 태그 제거
            text = (item['title'] + " " + item['contents']).replace("<br>", "").strip()
            text = text.lower()  # 텍스트를 소문자로 변환
            text = preprocess_text(text)  # 전처리
            all_text += text + " "  # 전체 텍스트 누적
        # 텍스트와 전체 텍스트를 함께 반환
        return all_text.strip()
    else:
        raise Exception(f"API 요청 실패: {response.status_code}")
    
def preprocess_text(text):
    text = re.sub(r'[^가-힣a-zA-Z0-9\s]', '', text)  # 특수문자 제거
    text = re.sub(r'\s+', ' ', text)  # 연속된 공백 제거
    return text.strip().lower()  # 소문자 변환 및 양 끝 공백 제거    

# 전체 텍스트 단어 빈도수 계산 및 긍정, 부정 점수 추가
def calculate_word_frequencies_with_scores(all_text):
    # 단어 빈도수 계산
    word_counts = Counter(all_text.split())

    # 긍정 및 부정 점수 추가
    word_freq_list = []
    for word, freq in word_counts.items():
        if word in STOP_WORDS:
            continue  # 불용어는 건너뛰기
        if any(re.fullmatch(pattern, word) for pattern in FILTER_PATTERNS):
            continue

        # 단어 정보와 빈도수, 긍정 및 부정 점수를 저장
        word_freq_list.append({
            "word": word,
            "freq": freq
        })

    # 정렬: 빈도수가 높은 순으로 정렬
    sorted_word_freq_list = sorted(word_freq_list, key=lambda x: x['freq'], reverse=True)

    return sorted_word_freq_list

# 4. Main Script
if __name__ == "__main__":
    # 국내, 해외 구분
    api_url = ""
    symbol = sys.argv[1]
    country = sys.argv[2]
    
    if country == 'OVERSEA':
        # 검색 API 요청
        url = f"https://m.stock.naver.com/front-api/search/autoComplete?query={symbol}%20&target=stock%2Cindex%2Cmarketindicator%2Ccoin%2Cipo"
        response = requests.get(url)
        if response.status_code == 200:
            data = response.json()
            try:
                # 첫 번째 items의 reutersCode 값 저장
                reuters_code = data["result"]["items"][0]["reutersCode"]
                print(f"Reuters Code: {reuters_code}")
            except (KeyError, IndexError):
                print("items 배열이 비어있거나 reutersCode 키가 존재하지 않습니다.")
        else:
            print(f"API 요청 실패: {response.status_code}")
        api_url = f"https://m.stock.naver.com/api/discuss/globalStock/{reuters_code}?rsno=0&size=100&filter="
    else:
        api_url = f"https://m.stock.naver.com/api/discuss/localStock/{symbol}?rsno=0&size=100&filter="

    # 불용어 리스트 정의
    STOP_WORDS = [
        '하', '이거', '종목', '다시', '종토방', '그럼', '이런', '다들', '지금', '뉴스로배우는세상', '또', '주식', '세상', '대신', '죄', '우리', '뭐', '찢', '좀', '너무', '아', '더', '다', '이', '그', '것', '수', '들', '를', '은', '는', '에', '의', '가', '와', '과', '역시', '해', '당장', '현재',
        '한', '로', '으로', '을', '하고', '그리고', '그러나', '하지만', '해서', '및', '또한', '근데', '흠', '진짜', '이제',
        '그리고', '며', '이다', '에서', '에게', '와의', '하고', '에서의', '난', '왜', '잘', '오', '딱', '말', '할', '한다', '오늘', '어제', '내일', '통해', '경우', '관련', '지난해', '현물', '시장', '대한', '따르면'
    ]

    # 필터링할 패턴 정의
    FILTER_PATTERNS = [
        r'[ㅋㅎ]+',           # ㅋ, ㅎ이 하나 이상 반복된 패턴 (예: ㅋ, ㅋㅋ, ㅎㅎ 등)
        r'[ㅜㅠ]+',           # ㅜ, ㅠ가 하나 이상 반복된 패턴 (예: ㅜㅜ, ㅠㅠ 등)
        r'.*다$',            # ~다로 끝나는 단어
        r'.*오늘.*',         # '오늘'이 포함된 단어
        r'.*어제.*',         # '어제'가 포함된 단어
        r'.*내일.*',          # '내일'이 포함된 단어
        r'.*있.*',          # '있'이 포함된 단어
        r'.*의$',             # ~의로 끝나는 단어
        r'.*이$',             # ~이로 끝나는 단어
        r'.*가$',             # ~가로 끝나는 단어
        r'.*는$',             # ~는로 끝나는 단어
        r'.*은$',             # ~은로 끝나는 단어
        r'.*을$',             # ~을로 끝나는 단어
        r'.*로$',             # ~로 끝나는 단어
        r'.*를$',             # ~를로 끝나는 단어
        r'.*가$'             # ~가로 끝나는 단어
    ]

    try:
        # API에서 텍스트 데이터 가져오기
        all_text = fetch_texts_from_api(api_url)
        freq_list = calculate_word_frequencies_with_scores(all_text)
        print(json.dumps({"word_cloud": freq_list}, ensure_ascii=False))
        sys.exit(0)  # 성공 시 exit code 0
    
    except Exception as e:
        print(f"오류 발생: {e}")
        sys.exit(1)  # 성공 시 exit code 0