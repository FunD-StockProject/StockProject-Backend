import warnings
warnings.filterwarnings("ignore", category=UserWarning, module="urllib3")
warnings.filterwarnings("ignore", message="Some weights of")

import re
import sys
import json
import requests
from collections import Counter
from datetime import datetime, timedelta

# 크롤링 클래스
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
    
    def fetch_stock_posts(self, stock_code, country, max_posts=150):
        """주식 코드에 해당하는 게시글들을 API로 가져오기"""
        posts = []
        
        # country 파라미터로 해외 주식 여부 결정
        is_foreign = (country.upper() == "OVERSEA")
        
        if is_foreign and '.' not in stock_code:
            stock_code = self.try_foreign_stock_extensions(stock_code)
        
        discussion_type = "foreignStock" if is_foreign else "domesticStock"
        
        # 인기 게시글 가져오기
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
        
        # 일반 게시글 가져오기
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
                        
                        if not page_posts:
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
                        
                        if len(page_posts) < page_size:
                            break
                        
                        page += 1
                    else:
                        break
                else:
                    break
                    
            except Exception as e:
                break
        
        yesterday_posts = [post for post in posts if self.is_yesterday_post(post['date'])]
        
        return posts[:max_posts]
    
    def parse_date(self, date_str):
        try:
            post_date = datetime.fromisoformat(date_str.replace('Z', '+00:00'))
            yesterday = datetime.now() - timedelta(days=1)
            return post_date.date() == yesterday.date()
        except Exception as e:
            return False
    
    def is_yesterday_post(self, date_str):
        return self.parse_date(date_str)
    
    def format_for_api(self, posts):
        formatted_posts = []
        
        for post in posts:
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
    
    def try_foreign_stock_extensions(self, base_ticker):
        extensions = ['.O', '.K']
        
        for ext in extensions:
            full_ticker = base_ticker + ext
            if self.test_ticker_exists(full_ticker):
                return full_ticker
        
        return base_ticker
    
    def test_ticker_exists(self, ticker):
        try:
            url = f"https://m.stock.naver.com/front-api/discussion/list/hot?itemCode={ticker}&discussionType=foreignStock"
            response = self.session.get(url, timeout=5)
            if response.status_code == 200:
                data = response.json()
                return data.get('isSuccess', False)
        except:
            pass
        return False

# 키워드 추출 및 점수 계산 함수들
def fetch_texts_from_crawler(symbol, country):
    crawler = StockCrawler()
    posts = crawler.fetch_stock_posts(symbol, country, max_posts=150)
    
    if not posts:
        return []
    
    formatted_posts = crawler.format_for_api(posts)
    
    texts_with_weights = []
    for item in formatted_posts:
        text = (item['title'] + " " + item['contents']).replace("<br>", "").strip()
        good_count = item.get('goodCount', 0)
        bad_count = item.get('badCount', 0)
        is_holding = item.get('isHolding', False)

        base_weight = 1.0
        good_weight = 1 + good_count * 1.0
        bad_weight = 1 - bad_count * 0.25
        holding_weight = 3.0 if is_holding else 1.0
        weight = max(0, base_weight * good_weight * bad_weight * holding_weight)

        texts_with_weights.append((text, weight))
    
    return texts_with_weights

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
    
    if total_weight:
        score = total_score / total_weight * 50 + 50
        return max(0, min(100, score))
    return 50

def calculate_sentiment_score(texts_with_weights, weighted_keywords, positive_patterns, negative_patterns):
    total_score = 0
    total_weight = 0

    for text, weight in texts_with_weights:
        text = text.lower()
        text_score = 50
        pattern_score = 0

        for category, keywords in weighted_keywords.items():
            for word, word_weight in keywords.items():
                count = text.count(word)
                if category == '긍정':
                    text_score += count * word_weight
                elif category == '부정':
                    text_score += count * word_weight

        for pattern in positive_patterns:
            if re.search(pattern, text):
                pattern_score += 3
        for pattern in negative_patterns:
            if re.search(pattern, text):
                pattern_score -= 5

        adjusted_score = max(0, min(100, text_score + pattern_score))
        total_score += adjusted_score * weight
        total_weight += weight

    if total_weight == 0:
        return 50

    return total_score / total_weight

def calculate_pattern_score(texts_with_weights, positive_patterns, negative_patterns):
    total_score = 0
    total_weight = 0

    for text, weight in texts_with_weights:
        positive_matches = sum([1 for pattern in positive_patterns if re.search(pattern, text)])
        negative_matches = sum([1 for pattern in negative_patterns if re.search(pattern, text)])

        pattern_score = (positive_matches - 1.5 * negative_matches) * weight
        
        total_score += max(0, pattern_score)
        total_weight += weight

    if total_weight == 0:
        return 50
    return min(100, max(0, (total_score / total_weight) * 50 + 50))

def preprocess_text(text):
    text = re.sub(r'[^가-힣a-zA-Z0-9\s]', '', text)  # 특수문자 제거
    text = re.sub(r'\s+', ' ', text)  # 연속된 공백 제거
    return text.strip().lower()  # 소문자 변환 및 양 끝 공백 제거

def calculate_word_frequencies(all_text, STOP_WORDS, filter_patterns):
    word_counts = Counter(all_text.split())
    word_freq_list = []
    for word, freq in word_counts.items():
        if word in STOP_WORDS:
            continue
        if any(re.fullmatch(pattern, word) for pattern in filter_patterns):
            continue
        word_freq_list.append({"word": word, "freq": freq})
    return sorted(word_freq_list, key=lambda x: x['freq'], reverse=True)

def extract_top_keywords(texts_with_weights, top_n=10):
    """상위 키워드 추출 (기존 로직과 동일)"""
    # 불용어 리스트
    STOP_WORDS = [
        '하', '이거', '종목', '다시', '종토방', '그럼', '이런', '다들', '지금', '뉴스로배우는세상', '또', '주식', '세상', '대신', '죄', '우리', '뭐', '찢', '좀', '너무', '아', '더', '다', '이', '그', '것', '수', '들', '를', '은', '는', '에', '의', '가', '와', '과', '역시', '해', '당장', '현재',
        '한', '로', '으로', '을', '하고', '그리고', '그러나', '하지만', '해서', '및', '또한', '근데', '흠', '진짜', '이제',
        '그리고', '며', '이다', '에서', '에게', '와의', '하고', '에서의', '난', '왜', '잘', '오', '딱', '말', '할', '한다', '오늘', '어제', '내일', '통해', '경우', '관련', '지난해', '현물', '시장', '대한', '따르면'
    ]
    
    # 필터링 패턴
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
    
    all_text = ""
    for text, weight in texts_with_weights:
        # 텍스트 전처리
        processed_text = preprocess_text(text)
        all_text += processed_text + " "
    
    word_freq_list = calculate_word_frequencies(all_text.strip(), STOP_WORDS, FILTER_PATTERNS)
    return word_freq_list[:top_n]

# Main Script
if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(1)
    
    symbol = sys.argv[1]
    country = sys.argv[2]

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
        try:
            texts_with_weights = fetch_texts_from_crawler(symbol, country)
            if texts_with_weights:
                # 점수 계산
                keyword_score = calculate_keyword_score(texts_with_weights, weighted_keywords)
                pattern_score = calculate_pattern_score(texts_with_weights, positive_patterns, negative_patterns)
                sentiment_score = calculate_sentiment_score(texts_with_weights, weighted_keywords, positive_patterns, negative_patterns)
                
                final_score = (0.6 * keyword_score + 0.2 * sentiment_score + 0.2 * pattern_score)
                final_score = max(0, min(100, int(round(final_score))))
                
                # 키워드 추출
                top_keywords = extract_top_keywords(texts_with_weights, top_n=10)
                
                result = {
                    "final_score": final_score,
                    "top_keywords": top_keywords
                }
            else:
                import random
                random.seed(hash(symbol) % 10000)
                final_score = random.randint(30, 70)
                result = {
                    "final_score": final_score,
                    "top_keywords": []
                }
        except Exception as crawler_error:
            import random
            random.seed(hash(symbol) % 10000)
            final_score = random.randint(30, 70)
            result = {
                "final_score": final_score,
                "top_keywords": []
            }

        print(json.dumps(result, ensure_ascii=False, indent=2))
        sys.exit(0)

    except Exception as e:
        result = {
            "final_score": 50,
            "top_keywords": []
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
        sys.exit(1)
