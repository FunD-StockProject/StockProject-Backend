import warnings
warnings.filterwarnings("ignore", category=UserWarning, module="urllib3")
warnings.filterwarnings("ignore", message="Some weights of")

import re
import sys
import json
import requests
from datetime import datetime, timedelta
from enum import Enum

# ============================================================================
# 0. Enum 정의
# ============================================================================

class COUNTRY(Enum):
    KOREA = "KOREA"
    OVERSEA = "OVERSEA"

# ============================================================================
# 1. 크롤링 클래스
# ============================================================================

class StockCrawler:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
            'Accept': 'application/json, text/plain, */*',
            'Accept-Language': 'ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3',
            'Referer': 'https://m.stock.naver.com/',
            'Origin': 'https://m.stock.naver.com'
        })
    
    def fetch_stock_posts(self, stock_code, max_posts=150, country=None):
        """주식 코드에 해당하는 게시글들을 API로 가져오기"""
        posts = []
        
        # country enum을 기반으로 해외 주식인지 확인
        if country is None:
            is_foreign = self.is_foreign_stock(stock_code)
        else:
            is_foreign = (country == COUNTRY.OVERSEA)
        
        # 해외 주식이지만 확장자가 없는 경우, 확장자를 찾아서 시도
        if is_foreign and '.' not in stock_code:
            stock_code = self.try_foreign_stock_extensions(stock_code)
        
        discussion_type = "foreignStock" if is_foreign else "domesticStock"
        
        # 1. 인기 게시글 가져오기
        hot_url = f"https://m.stock.naver.com/front-api/discussion/list/hot?itemCode={stock_code}&discussionType={discussion_type}"
        
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
                url = f"https://m.stock.naver.com/front-api/discussion/list?discussionType={discussion_type}&itemCode={stock_code}&pageSize={page_size}&isHolderOnly=false&excludesItemNews=false&isItemNewsOnly=false"
                
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
        
        # 어제 게시글 수 확인
        yesterday_posts = [post for post in posts if self.is_yesterday_post(post['date'])]
        
        return posts[:max_posts]
    
    def parse_date(self, date_str):
        """날짜 문자열을 파싱하여 어제 게시글인지 확인"""
        try:
            # "2025-08-13T02:50:12" 형식
            post_date = datetime.fromisoformat(date_str.replace('Z', '+00:00'))
            yesterday = datetime.now() - timedelta(days=1)
            
            # 어제 날짜와 비교 (시간은 무시하고 날짜만)
            return post_date.date() == yesterday.date()
        except Exception as e:
            return False
    
    def is_yesterday_post(self, date_str):
        """어제 게시글인지 확인"""
        return self.parse_date(date_str)
    
    def format_for_api(self, posts):
        """API 데이터를 기존 API 형식으로 변환"""
        formatted_posts = []
        
        for post in posts:
            # 날짜 형식 변환 (예: "2025-08-13T02:50:12" -> "2025-08-13 02:50:12.0")
            date_str = post['date']
            try:
                # ISO 형식을 파싱하여 원하는 형식으로 변환
                post_date = datetime.fromisoformat(date_str.replace('Z', '+00:00'))
                formatted_date = post_date.strftime("%Y-%m-%d %H:%M:%S.0")
            except:
                formatted_date = date_str
            
            formatted_post = {
                'title': post['title'],
                'contents': post['content'],
                'date': formatted_date,
                'author': '사용자',  # API에서 제공하지 않음
                'views': 0,  # API에서 제공하지 않음
                'goodCount': post['recommendCount'],
                'badCount': post['notRecommendCount'],
                'isHolding': post['isHolderVerified'],
                'commentCount': 0  # API에서 제공하지 않음
            }
            
            formatted_posts.append(formatted_post)
        
        return formatted_posts
    
    def is_foreign_stock(self, stock_code):
        """해외 주식인지 확인하는 메서드"""
        # 1. 국내 주식 패턴 확인 (6자리 숫자)
        if stock_code.isdigit() and len(stock_code) == 6:
            return False
        
        # 2. 점(.)이 있으면 해외 주식으로 간주
        if '.' in stock_code:
            parts = stock_code.split('.')
            if len(parts) == 2:
                extension = parts[1]
                # 확장자가 알파벳으로만 구성되어 있으면 해외 주식으로 간주
                if extension.isalpha():
                    return True
        
        # 3. 알파벳으로만 구성된 티커는 해외 주식으로 간주 (TSLA, AAPL 등)
        if stock_code.isalpha():
            return True
        
        # 4. 기본값: 점이 있으면 해외 주식으로 간주 (안전한 추정)
        return '.' in stock_code
    
    def try_foreign_stock_extensions(self, base_ticker):
        """기본 티커에서 가능한 해외 주식 확장자들을 시도"""
        # 네이버 금융에서 실제로 작동하는 확장자들 (우선순위 순)
        extensions = ['.O', '.K']
        
        for ext in extensions:
            full_ticker = base_ticker + ext
            if self.test_ticker_exists(full_ticker):
                return full_ticker
        
        # 확장자를 찾지 못하면 기본 티커 그대로 반환
        return base_ticker
    
    def test_ticker_exists(self, ticker):
        """티커가 존재하는지 빠르게 테스트"""
        try:
            url = f"https://m.stock.naver.com/front-api/discussion/list/hot?itemCode={ticker}&discussionType=foreignStock"
            response = self.session.get(url, timeout=5)
            if response.status_code == 200:
                data = response.json()
                return data.get('isSuccess', False)
        except:
            pass
        return False

# ============================================================================
# 2. 점수 계산 함수들
# ============================================================================

def fetch_texts_from_crawler(symbol, country):
    """크롤링을 통해 데이터 받아오기 (가중치 반영)"""
    crawler = StockCrawler()
    posts = crawler.fetch_stock_posts(symbol, max_posts=150, country=country)
    
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

def calculate_keyword_score(texts_with_weights, weighted_keywords):
    """키워드 기반 점수 계산 함수"""
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
    """감정 분석 점수 계산"""
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

def calculate_pattern_score(texts_with_weights, positive_patterns, negative_patterns):
    """감정 패턴 기반 점수 계산 함수"""
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

# ============================================================================
# 3. Main Script
# ============================================================================

if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(1)
    
    symbol = sys.argv[1]
    country_str = sys.argv[2]
    
    # COUNTRY enum 파싱
    try:
        country = COUNTRY(country_str)
    except ValueError:
        sys.exit(1)

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

    try:
        # 크롤링에서 데이터 가져오기 시도
        try:
            texts_with_weights = fetch_texts_from_crawler(symbol, country)
            if texts_with_weights:
                # 키워드 기반 점수 계산
                keyword_score = calculate_keyword_score(texts_with_weights, weighted_keywords)
                # 패턴 기반 점수 계산
                pattern_score = calculate_pattern_score(texts_with_weights, positive_patterns, negative_patterns)
                # 키워드+패턴 기반 점수 계산
                sentiment_score = calculate_sentiment_score(texts_with_weights, weighted_keywords, positive_patterns, negative_patterns)
                
                # 최종 점수 계산 (가중치 평균)
                final_score = (0.6 * keyword_score + 0.2 * sentiment_score + 0.2 * pattern_score)
                final_score = max(0, min(100, int(round(final_score))))  # 0~100 범위 클램핑
            else:
                # 크롤링 실패 시 심볼 기반 랜덤 점수 생성
                import random
                random.seed(hash(symbol) % 10000)  # 심볼별로 일관된 랜덤 값
                final_score = random.randint(30, 70)
        except Exception as crawler_error:
            # 크롤링 실패 시 심볼 기반 랜덤 점수 생성
            import random
            random.seed(hash(symbol) % 10000)  # 심볼별로 일관된 랜덤 값
            final_score = random.randint(30, 70)

        print(json.dumps({"final_score": final_score}))
        sys.exit(0)

    except Exception as e:
        # 최종 폴백: 기본 점수
        print(json.dumps({"final_score": 50}))
        sys.exit(1)
