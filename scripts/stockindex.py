import warnings
warnings.filterwarnings("ignore", category=UserWarning, module="urllib3")
warnings.filterwarnings("ignore", message="Some weights of")

import requests
import re
import sys
import json
import fear_and_greed
from datetime import datetime, timedelta

def runVixKospi():
    """Korea Fear & Greed Index 계산 (CNN 로직 기반)"""
    try:
        # CNBC에서 KOSPI VIX 값 가져오기
        vix_value = fetch_kospi_vix_from_cnbc()
        if vix_value is None:
            return 50
        
        # KOSPI 데이터 가져오기
        kospi_data = get_kospi_data()
        if kospi_data is None:
            vix_score = calculate_vix_score(vix_value)
            momentum_score = 50
            stock_score = 50
        else:
            vix_score = calculate_vix_score(vix_value)
            momentum_score = calculate_momentum_score(kospi_data['change_pct'])
            stock_score = calculate_stock_price_score(kospi_data['ma_ratio'])
        
        # 가중 평균 계산 (CNN 로직 기반)
        final_score = (
            vix_score * 0.4 +      # VIX 40%
            momentum_score * 0.3 + # 모멘텀 30%
            stock_score * 0.3      # 주가 30%
        )
        
        return round(final_score)
        
    except Exception as e:
        return 50

def fetch_kospi_vix_from_cnbc():
    """CNBC에서 KOSPI VIX 값을 가져오기"""
    url = "https://www.cnbc.com/quotes/.KSVKOSPI"
    
    session = requests.Session()
    session.headers.update({
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
        'Accept-Language': 'ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3'
    })
    
    try:
        response = session.get(url, timeout=10)
        if response.status_code == 200:
            content = response.text
            
            # VIX 값 찾기
            vix_patterns = [
                r'(\d+\.\d+).*?Volatility',
                r'Last.*?(\d+\.\d+).*?KRW',
                r'(\d+\.\d+).*?quote price.*?arrow'
            ]
            
            for pattern in vix_patterns:
                matches = re.findall(pattern, content)
                if matches:
                    for match in matches:
                        try:
                            value = float(match)
                            if 15 <= value <= 50:  # VIX 일반 범위
                                return value
                        except ValueError:
                            continue
            
            return None
            
    except Exception as e:
        return None

def get_kospi_data():
    """KOSPI 지수 데이터 가져오기"""
    try:
        import yfinance as yf
        kospi = yf.Ticker("^KS11")
        hist = kospi.history(period="30d")
        
        if len(hist) >= 2:
            current_price = hist['Close'].iloc[-1]
            prev_price = hist['Close'].iloc[-2]
            change_pct = ((current_price - prev_price) / prev_price) * 100
            
            # 20일 이동평균
            ma_20 = hist['Close'].rolling(window=20).mean().iloc[-1]
            ma_ratio = (current_price / ma_20 - 1) * 100
            
            return {
                'current_price': current_price,
                'change_pct': change_pct,
                'ma_ratio': ma_ratio
            }
    except Exception as e:
        return None

def calculate_vix_score(vix_value):
    """VIX 값을 점수로 변환 (CNN 로직 기반)"""
    if vix_value <= 15:
        return 80  # 매우 낮은 공포
    elif vix_value <= 20:
        return 70  # 낮은 공포
    elif vix_value <= 25:
        return 60  # 약간 낮은 공포
    elif vix_value <= 30:
        return 50  # 중립
    elif vix_value <= 35:
        return 40  # 약간 높은 공포
    elif vix_value <= 40:
        return 30  # 높은 공포
    else:
        return 20  # 매우 높은 공포

def calculate_momentum_score(change_pct):
    """모멘텀 점수 계산 (CNN 로직 기반)"""
    if change_pct >= 2.0:
        return 80  # 강한 상승
    elif change_pct >= 1.0:
        return 70  # 상승
    elif change_pct >= 0.5:
        return 60  # 약간 상승
    elif change_pct >= -0.5:
        return 50  # 중립
    elif change_pct >= -1.0:
        return 40  # 약간 하락
    elif change_pct >= -2.0:
        return 30  # 하락
    else:
        return 20  # 강한 하락

def calculate_stock_price_score(ma_ratio):
    """주가 대비 이동평균 점수 계산 (CNN 로직 기반)"""
    if ma_ratio >= 5.0:
        return 80  # 강한 상승세
    elif ma_ratio >= 2.0:
        return 70  # 상승세
    elif ma_ratio >= 0.5:
        return 60  # 약간 상승세
    elif ma_ratio >= -0.5:
        return 50  # 중립
    elif ma_ratio >= -2.0:
        return 40  # 약간 하락세
    elif ma_ratio >= -5.0:
        return 30  # 하락세
    else:
        return 20  # 강한 하락세

def runVixSnp(api_url):
    try:
        response = requests.get(api_url)
        if response.status_code == 200:
            data = response.json()
            fear_and_greed = data.get("fear_and_greed", {})
            score = fear_and_greed.get("score", 0)
            final_score = round(score)  # 반올림하여 정수로 변환
            return final_score
        else:
            raise Exception(f"API 요청 실패: {response.status_code}")
    except Exception as e:
        return None

# 1. 지수 크롤링 클래스
class IndexCrawler:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
            'Accept': 'application/json, text/plain, */*',
            'Accept-Language': 'ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3',
            'Referer': 'https://m.stock.naver.com/',
            'Origin': 'https://m.stock.naver.com'
        })
    
    def fetch_index_posts(self, item_code, discussion_type, max_posts=150):
        """지수 게시글들을 API로 가져오기"""
        posts = []
        
        # 1. 인기 게시글 가져오기
        hot_url = f"https://m.stock.naver.com/front-api/discussion/list/hot?itemCode={item_code}&discussionType={discussion_type}"
        
        try:
            response = self.session.get(hot_url, timeout=10)
            if response.status_code == 200:
                data = response.json()
                if data.get('isSuccess') and 'result' in data:
                    hot_posts = data['result'].get('hotPosts', [])
                    for post in hot_posts:
                        post_data = {
                            'title': post.get('title', ''),
                            'content': post.get('contentSwReplacedButImg', ''),
                            'date': post.get('writtenAt', ''),
                            'recommendCount': post.get('recommendCount', 0),
                            'notRecommendCount': post.get('notRecommendCount', 0),
                            'isHolderVerified': post.get('writer', {}).get('isHolderVerified', False)
                        }
                        posts.append(post_data)
        except Exception as e:
            pass
        
        # 2. 일반 게시글 가져오기 (페이지별로)
        page_size = 50
        page = 1
        
        while len(posts) < max_posts:
            try:
                url = f"https://m.stock.naver.com/front-api/discussion/list?discussionType={discussion_type}&itemCode={item_code}&pageSize={page_size}&isHolderOnly=false&excludesItemNews=false&isItemNewsOnly=false"
                
                response = self.session.get(url, timeout=10)
                if response.status_code == 200:
                    data = response.json()
                    if data.get('isSuccess') and 'result' in data:
                        page_posts = data['result'].get('posts', [])
                        
                        if not page_posts:  # 더 이상 게시글이 없으면 중단
                            break
                        
                        for post in page_posts:
                            post_data = {
                                'title': post.get('title', ''),
                                'content': post.get('contentSwReplaced', ''),
                                'date': post.get('writtenAt', ''),
                                'recommendCount': post.get('recommendCount', 0),
                                'notRecommendCount': post.get('notRecommendCount', 0),
                                'isHolderVerified': post.get('writer', {}).get('isHolderVerified', False)
                            }
                            posts.append(post_data)
                        
                        if len(page_posts) < page_size:  # 마지막 페이지
                            break
                        
                        page += 1
                    else:
                        break
                else:
                    break
                    
            except Exception as e:
                break
        
        return posts[:max_posts]
    
    def format_for_api(self, posts):
        """API 데이터를 기존 API 형식으로 변환"""
        formatted_posts = []
        
        for post in posts:
            # 날짜 형식 변환
            date_str = post['date']
            try:
                post_date = datetime.fromisoformat(date_str.replace('Z', '+00:00'))
                formatted_date = post_date.strftime("%Y-%m-%d %H:%M:%S.0")
            except:
                formatted_date = date_str
            
            formatted_post = {
                'title': post['title'],
                'contents': post['content'],
                'date': formatted_date,
                'author': '사용자',
                'views': 0,
                'goodCount': post['recommendCount'],
                'badCount': post['notRecommendCount'],
                'isHolding': post['isHolderVerified'],
                'commentCount': 0
            }
            
            formatted_posts.append(formatted_post)
        
        return formatted_posts

# 2. 크롤링을 통해 데이터 받아오기 (가중치 반영)
def fetch_texts_from_crawler(item_code, discussion_type):
    """크롤링을 통해 지수 데이터 받아오기"""
    crawler = IndexCrawler()
    posts = crawler.fetch_index_posts(item_code, discussion_type, max_posts=150)
    
    if not posts:
        return []
    
    # API 형식으로 변환
    formatted_posts = crawler.format_for_api(posts)
    
    texts_with_weights = []
    for item in formatted_posts:
        text = (item['title'] + " " + item['contents']).replace("<br>", "").strip()
        good_count = item.get('goodCount', 0)
        bad_count = item.get('badCount', 0)
        is_holding = item.get('isHolding', False)

        # 가중치 계산
        base_weight = 1.0
        good_weight = 1 + good_count * 1.0
        bad_weight = 1 - bad_count * 0.25
        holding_weight = 3.0 if is_holding else 1.0
        weight = max(0, base_weight * good_weight * bad_weight * holding_weight)

        texts_with_weights.append((text, weight))
    
    return texts_with_weights

# 2. 키워드 기반 점수 계산 함수
def calculate_keyword_score(texts_with_weights, weighted_keywords):
    total_score = 0
    total_weight = 0
    for text, weight in texts_with_weights:
        text = text.lower()
        text_score = 0
        for category, keywords in weighted_keywords.items():
            for word, word_weight in keywords.items():
                count = text.count(word)
                text_score += count * word_weight
        total_score += text_score * weight
        total_weight += weight
    
    # 점수 계산 및 클램핑 (0~100)
    if total_weight:
        score = total_score / total_weight * 50 + 50
        return max(0, min(100, score))
    return 50  # 데이터가 없는 경우 기본 점수 반환

def calculate_sentiment_score(texts_with_weights, weighted_keywords, positive_patterns, negative_patterns):
    total_score = 0
    total_weight = 0

    for text, weight in texts_with_weights:
        text = text.lower()
        text_score = 50  # 기본 점수
        pattern_score = 0

        # 키워드 기반 점수 계산
        for category, keywords in weighted_keywords.items():
            for word, word_weight in keywords.items():
                count = text.count(word)
                if category == '긍정':
                    text_score += count * word_weight
                elif category == '부정':
                    text_score += count * word_weight

        # 패턴 기반 점수 계산
        for pattern in positive_patterns:
            if re.search(pattern, text):
                pattern_score += 3  # 긍정 패턴 점수 증가
        for pattern in negative_patterns:
            if re.search(pattern, text):
                pattern_score -= 5  # 부정 패턴 점수 감소

        # 점수와 가중치 반영
        adjusted_score = max(0, min(100, text_score + pattern_score))
        total_score += adjusted_score * weight
        total_weight += weight

    if total_weight == 0:
        return 50  # 데이터가 없는 경우 기본 점수 반환

    # 가중 평균 점수 계산
    return total_score / total_weight

# 3. 감정 패턴 기반 점수 계산 함수
def calculate_pattern_score(texts_with_weights, positive_patterns, negative_patterns):
    total_score = 0
    total_weight = 0

    for text, weight in texts_with_weights:
        positive_matches = sum([1 for pattern in positive_patterns if re.search(pattern, text)])
        negative_matches = sum([1 for pattern in negative_patterns if re.search(pattern, text)])

        # 감정 패턴 점수 계산: 부정적 패턴의 영향을 더 강하게 반영
        pattern_score = (positive_matches - 1.5 * negative_matches) * weight
        
        # 스코어를 누적
        total_score += max(0, pattern_score)  # 부정점수는 0으로 처리
        total_weight += weight

    # 최종 스코어 정규화 (0 ~ 100)
    if total_weight == 0:
        return 50  # 가중치가 없는 경우 중립값 반환
    return min(100, max(0, (total_score / total_weight) * 50 + 50))

def runScore(item_code, discussion_type):
    try:
        texts_with_weights = fetch_texts_from_crawler(item_code, discussion_type)
        if not texts_with_weights:
            return 50  # 데이터가 없는 경우 기본 점수
        
        # 키워드 기반 점수 계산
        keyword_score = calculate_keyword_score(texts_with_weights, weighted_keywords)

        # 패턴 기반 점수 계산
        pattern_score = calculate_pattern_score(texts_with_weights, positive_patterns, negative_patterns)

        # 키워드+패턴 기반 점수 계산
        sentiment_score = calculate_sentiment_score(texts_with_weights, weighted_keywords, positive_patterns, negative_patterns)

        # 최종 점수 계산 (가중치 평균)
        final_score = (0.6 * keyword_score + 0.2 * sentiment_score + 0.2 * pattern_score)
        final_score = max(0, min(100, int(round(final_score))))  # 0~100 범위 클램핑

        return final_score

    except Exception as e:
        return 50  # 오류 시 기본 점수 반환    

# 4. Main Script
if __name__ == "__main__":
    #snp 공포지수
    api_vix_snp = "https://production.dataviz.cnn.io/index/fearandgreed/graphdata"

    # 키워드와 가중치 설정
    weighted_keywords = {
        '긍정': {
            '상한가': 3, '호재': 3, '기대': 2, '반등': 2, '우상향': 2, '실적 개선': 3, '성장': 3, '기회': 2, '최고': 2,
            '시장 선점': 3, '확실': 2, '강세': 2, '매수': 2, '수익': 3, '탄력': 2, '목표': 2, '성공': 3, '긍정적': 2,
            '안심': 2, '믿음': 2, '비전': 2, '신뢰': 2, '지지': 2, '대박': 3, '유망': 2, '회복': 2, '승리': 3,
            '안정적': 2, '좋은 소식': 3, '호황': 3, '상승세': 2, '리더': 3, '강력': 2, '지속적': 2, '돌파': 2,
            '흑자': 3, '견조': 2, '매력적': 2, '유지': 1, '추천': 2, '신규 매수': 3, '견고': 2, '견실': 2,
            '신뢰성': 2, '초과 달성': 3, '긍정적 전망': 3, '지속 가능': 2, '복귀': 2, '재상승': 3, '최대': 2
        },
        '부정': {
            '나락': -3, '설거지': -3, '갭 하락': -2, '떡락': -2, '무너짐': -3, '하락': -2, '지겹다': -2, '폭락': -3,
            '조롱': -2, '손절': -2, '회의적': -3, '위험': -3, '부정적': -2, '리스크': -2, '의심': -2, '하락장': -3,
            '불확실': -3, '맹신': -2, '실패': -3, '조정': -2, '불안': -2, '약세': -2, '악재': -3, '실망': -2,
            '무책임': -3, '개잡주': -3, '속임수': -3, '탐욕': -3, '지배력': -2, '약탈': -3, '괴롭힘': -2, '추락': -3,
            '문제': -2, '공포': -3, '패배': -3, '손실': -2, '파산': -3, '불황': -3, '폭락세': -3, '실적 악화': -3,
            '적자': -3, '낙폭': -2, '실망감': -2, '위기': -3, '추가 손실': -3, '하향': -2, '경고': -2, '부진': -2,
            '악화': -3, '속임수': -3, '절망': -3, '상장폐지': -3, '부도': -3, '자산 축소': -3, '대규모 손실': -3,
            '연체': -3, '감소세': -2, '기회 상실': -2, '추락세': -3, '비관적': -2, '무대책': -2, '퇴보': -3, '참나': -1,
            '최저가': -3, '탈출': -3
        }
    }

    negative_patterns = [
    r"왜.*내리", r"망했", r"끝났", r"죽었", r"더럽", r"쓰레기", r"손실.*엄청", r"사기", r"못.*팔", r"폭락", r"안.*돼",
    r"허매도", r"개잡주", r"물렸", r"눈물.*난다", r"꼬라지", r"손절", r"팔아야겠", r"하락.*심각", r"망해라", r"실망",
    r"폭탄", r"끝장", r"빼버린다", r"조작", r"안.*살", r"본전", r"ㅜㅜ", r"ㅠㅠ"
    ]

    positive_patterns = [
        r"상한가", r"대박", r"반등", r"좋아질", r"기대", r"갈.*같", r"오르", r"최고", r"확실", r"수익", r"탄력", r"매수",
        r"목표가", r"성공", r"긍정적", r"믿음", r"신뢰", r"비전", r"강세", r"지지", r"홀딩", r"기회", r"수직 상승",
        r"새.*시작", r"터진다", r"우상향"
    ]

if __name__ == "__main__":
    try:
        # 점수 계산 (크롤링 기반)
        score_kospi = runScore("KOSPI", "domesticIndex")
        score_kosdaq = runScore("KOSDAQ", "domesticIndex")
        score_snp = runScore(".INX", "foreignIndex")
        score_nasdaq = runScore(".IXIC", "foreignIndex")
        score_kospi_vix = runVixKospi()
        
        # S&P500 VIX (fear-and-greed 패키지 사용)
        try:
            fear_greed_data = fear_and_greed.get()
            score_snp_vix = round(fear_greed_data.value) if fear_greed_data else 50
        except Exception as e:
            score_snp_vix = 50

        # JSON 출력
        results = {
            "kospi": score_kospi or 50,
            "kosdaq": score_kosdaq or 50,
            "snp500": score_snp or 50,
            "nasdaq": score_nasdaq or 50,
            "vixKospi": score_kospi_vix or 50,
            "vixSnp": score_snp_vix or 50
        }
        print(json.dumps(results, ensure_ascii=False, indent=4))
        sys.exit(0)
    except Exception as e:
        sys.exit(1)  # 예외 발생 시 종료 코드
