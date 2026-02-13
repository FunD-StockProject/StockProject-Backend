import warnings

warnings.filterwarnings("ignore", category=UserWarning, module="urllib3")
warnings.filterwarnings("ignore", message="Some weights of")

import json
import re
import sys
from collections import Counter

try:
    import requests
    REQUESTS_IMPORT_ERROR = None
except Exception as import_error:
    requests = None
    REQUESTS_IMPORT_ERROR = str(import_error)


def build_session():
    session = requests.Session()
    session.headers.update(
        {
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/91.0.4472.124 Safari/537.36"
            ),
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": "ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3",
            "Referer": "https://m.stock.naver.com/",
            "Origin": "https://m.stock.naver.com",
        }
    )
    return session


def safe_get_json(session, url, timeout=10):
    response = session.get(url, timeout=timeout)
    response.raise_for_status()

    content_type = response.headers.get("Content-Type", "")
    if "json" not in content_type.lower():
        preview = response.text[:200].replace("\n", " ")
        raise ValueError(f"non-json response: {preview}")

    return response.json()


def resolve_oversea_item_code(session, symbol):
    url = (
        "https://m.stock.naver.com/front-api/search/autoComplete"
        f"?query={symbol}%20&target=stock%2Cindex%2Cmarketindicator%2Ccoin%2Cipo"
    )
    data = safe_get_json(session, url)
    items = data.get("result", {}).get("items", [])
    if not items:
        raise ValueError("no overseas items from autocomplete")

    for item in items:
        reuters_code = item.get("reutersCode")
        if reuters_code:
            return reuters_code

    raise ValueError("missing reutersCode in autocomplete result")


def collect_posts(session, item_code, discussion_type, max_posts=150):
    texts = []

    hot_url = (
        "https://m.stock.naver.com/front-api/discussion/list/hot"
        f"?itemCode={item_code}&discussionType={discussion_type}"
    )
    try:
        hot_data = safe_get_json(session, hot_url)
        if hot_data.get("isSuccess"):
            for post in hot_data.get("result", {}).get("hotPosts", []):
                title = post.get("title", "")
                contents = post.get("contentSwReplacedButImg") or post.get(
                    "contentSwReplaced", ""
                )
                text = (title + " " + contents).replace("<br>", " ").strip()
                if text:
                    texts.append(text)
    except Exception:
        pass

    page = 1
    page_size = 50
    while len(texts) < max_posts:
        url = (
            "https://m.stock.naver.com/front-api/discussion/list"
            f"?discussionType={discussion_type}&itemCode={item_code}"
            f"&page={page}&pageSize={page_size}"
            "&isHolderOnly=false&excludesItemNews=false&isItemNewsOnly=false"
        )
        try:
            data = safe_get_json(session, url)
        except Exception:
            break

        if not data.get("isSuccess"):
            break

        posts = data.get("result", {}).get("posts", [])
        if not posts:
            break

        for post in posts:
            title = post.get("title", "")
            contents = post.get("contentSwReplacedButImg") or post.get(
                "contentSwReplaced", ""
            )
            text = (title + " " + contents).replace("<br>", " ").strip()
            if text:
                texts.append(text)
            if len(texts) >= max_posts:
                break

        if len(posts) < page_size:
            break
        page += 1

    return texts[:max_posts]


def preprocess_text(text):
    text = re.sub(r"[^가-힣a-zA-Z0-9\s]", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip().lower()


def calculate_word_frequencies_with_scores(all_text):
    word_counts = Counter(all_text.split())

    word_freq_list = []
    for word, freq in word_counts.items():
        if word in STOP_WORDS:
            continue
        if any(re.fullmatch(pattern, word) for pattern in FILTER_PATTERNS):
            continue

        word_freq_list.append({"word": word, "freq": freq})

    return sorted(word_freq_list, key=lambda x: x["freq"], reverse=True)


STOP_WORDS = [
    "하",
    "이거",
    "종목",
    "다시",
    "종토방",
    "그럼",
    "이런",
    "다들",
    "지금",
    "뉴스로배우는세상",
    "또",
    "주식",
    "세상",
    "대신",
    "죄",
    "우리",
    "뭐",
    "찢",
    "좀",
    "너무",
    "아",
    "더",
    "다",
    "이",
    "그",
    "것",
    "수",
    "들",
    "를",
    "은",
    "는",
    "에",
    "의",
    "가",
    "와",
    "과",
    "역시",
    "해",
    "당장",
    "현재",
    "한",
    "로",
    "으로",
    "을",
    "하고",
    "그리고",
    "그러나",
    "하지만",
    "해서",
    "및",
    "또한",
    "근데",
    "흠",
    "진짜",
    "이제",
    "그리고",
    "며",
    "이다",
    "에서",
    "에게",
    "와의",
    "하고",
    "에서의",
    "난",
    "왜",
    "잘",
    "오",
    "딱",
    "말",
    "할",
    "한다",
    "오늘",
    "어제",
    "내일",
    "통해",
    "경우",
    "관련",
    "지난해",
    "현물",
    "시장",
    "대한",
    "따르면",
]

FILTER_PATTERNS = [
    r"[ㅋㅎ]+",
    r"[ㅜㅠ]+",
    r".*다$",
    r".*오늘.*",
    r".*어제.*",
    r".*내일.*",
    r".*있.*",
    r".*의$",
    r".*이$",
    r".*가$",
    r".*는$",
    r".*은$",
    r".*을$",
    r".*로$",
    r".*를$",
    r".*가$",
]


def main():
    if len(sys.argv) != 3:
        print(json.dumps({"word_cloud": [], "error": "invalid args"}, ensure_ascii=False))
        return 0

    if requests is None:
        print(
            json.dumps(
                {"word_cloud": [], "error": f"requests import failed: {REQUESTS_IMPORT_ERROR}"},
                ensure_ascii=False,
            )
        )
        return 0

    symbol = sys.argv[1].strip()
    country = sys.argv[2].strip().upper()
    session = build_session()

    try:
        if country == "OVERSEA":
            item_code = resolve_oversea_item_code(session, symbol)
            discussion_type = "foreignStock"
        else:
            item_code = symbol
            discussion_type = "domesticStock"

        texts = collect_posts(session, item_code, discussion_type, max_posts=150)
        normalized_texts = [preprocess_text(text) for text in texts if text]
        all_text = " ".join(t for t in normalized_texts if t).strip()

        if not all_text:
            print(json.dumps({"word_cloud": []}, ensure_ascii=False))
            return 0

        freq_list = calculate_word_frequencies_with_scores(all_text)
        print(json.dumps({"word_cloud": freq_list}, ensure_ascii=False))
        return 0
    except Exception as e:
        print(json.dumps({"word_cloud": [], "error": str(e)}, ensure_ascii=False))
        return 0


if __name__ == "__main__":
    sys.exit(main())
