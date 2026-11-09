"""Fetch company summaries, with a factual stock-profile fallback for domestic shares."""
import html
import json
import re
import sys

import requests

REQUEST_TIMEOUT = (3, 8)
AUTOCOMPLETE_URL = "https://m.stock.naver.com/front-api/search/autoComplete"


class SummaryUnavailable(RuntimeError):
    pass


def clean_html_tags(text):
    if not isinstance(text, str):
        return ""
    text = re.sub(r"(?i)<br\s*/?>|</(?:p|div|li)\s*>", "\n", text)
    return html.unescape(re.sub(r"<[^>]+>", "", text))


def summary_lines(value):
    values = value if isinstance(value, list) else [value]
    lines = []
    for text in values:
        for line in clean_html_tags(text).splitlines():
            line = re.sub(r"\s+", " ", line).strip()
            if line and line not in lines:
                lines.append(line)
    return lines[:2]


def get_json(session, url, params=None):
    response = session.get(url, params=params, timeout=REQUEST_TIMEOUT)
    response.raise_for_status()
    data = response.json()
    if not isinstance(data, dict):
        raise SummaryUnavailable("Summary provider returned an invalid payload")
    return data


def get_korea_summaries(symbol, session):
    base = "https://m.stock.naver.com/api/stock/" + symbol
    integration = get_json(session, base + "/integration")
    descriptions = summary_lines(integration.get("description"))
    if descriptions:
        return descriptions

    # This provider supplies ETF descriptions but no company narrative for ordinary shares.
    # Return labelled, real stock facts rather than an empty or fabricated company description.
    basic = get_json(session, base + "/basic")
    name = clean_html_tags(basic.get("stockName")).strip()
    exchange = basic.get("stockExchangeType")
    exchange = exchange if isinstance(exchange, dict) else {}
    market = clean_html_tags(exchange.get("nameKor") or basic.get("stockExchangeName")).strip()
    if not name or not market:
        raise SummaryUnavailable("Stock profile is unavailable")

    kind = {"etf": "ETF", "etn": "ETN"}.get(str(basic.get("stockEndType", "")).lower(), "주식")
    summaries = [f"종목 기본정보: {name}({symbol})는 {market}에 상장된 {kind}입니다."]
    details = []
    infos = integration.get("totalInfos")
    for info in infos if isinstance(infos, list) else []:
        if not isinstance(info, dict) or info.get("code") not in {"marketValue", "foreignRate"}:
            continue
        label = clean_html_tags(info.get("key")).strip()
        value = clean_html_tags(info.get("value")).strip()
        if label and value and value not in {"-", "N/A"}:
            details.append(f"{label}: {value}")
    if details:
        traded_at = basic.get("localTradedAt")
        date = traded_at[:10] if isinstance(traded_at, str) and re.match(r"^\d{4}-\d{2}-\d{2}", traded_at) else ""
        prefix = date + " 기준 " if date else ""
        summaries.append(prefix + ", ".join(details))
    return summaries


def resolve_oversea_code(symbol, session):
    # Already resolved Reuters codes can be used directly.
    if re.fullmatch(r"[A-Za-z0-9._/-]+\.(?:O|N|K|A|P)", symbol):
        return symbol
    data = get_json(session, AUTOCOMPLETE_URL, {
        "query": symbol + " ",
        "target": "stock,index,marketindicator,coin,ipo",
    })
    result = data.get("result")
    items = result.get("items") if isinstance(result, dict) else None
    candidates = [item for item in items if isinstance(item, dict)
                  and isinstance(item.get("reutersCode"), str)] if isinstance(items, list) else []
    exact = [item for item in candidates if str(item.get("code", "")).casefold() == symbol.casefold()]
    if not exact:
        raise SummaryUnavailable("No matching overseas stock was found")
    code = exact[0]["reutersCode"]
    if not re.fullmatch(r"[A-Za-z0-9._/-]+", code):
        raise SummaryUnavailable("Invalid overseas stock identifier")
    return code


def get_oversea_summaries(symbol, session):
    from urllib.parse import quote

    code = resolve_oversea_code(symbol, session)
    data = get_json(session, "https://api.stock.naver.com/stock/" + quote(code, safe="") + "/overview")
    nested = data.get("summaries")
    nested = nested if isinstance(nested, dict) else {}
    descriptions = summary_lines(data.get("summary") or nested.get("summary"))
    if not descriptions:
        raise SummaryUnavailable("Company description is unavailable")
    return descriptions


def get_summaries(symbol, country, session=None):
    if not isinstance(symbol, str) or not re.fullmatch(r"[A-Za-z0-9._/-]{1,32}", symbol):
        raise ValueError("Invalid stock symbol")
    country = country.upper()
    if country not in {"KOREA", "OVERSEA"}:
        raise ValueError("Unsupported country")
    if country == "KOREA" and not re.fullmatch(r"[A-Z0-9]{6}", symbol):
        raise ValueError("Invalid domestic stock symbol")
    if session is None:
        with requests.Session() as owned_session:
            return get_summaries(symbol, country, owned_session)
    if country == "KOREA":
        return get_korea_summaries(symbol, session)
    return get_oversea_summaries(symbol, session)


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) != 2:
        print(json.dumps({"error": "Expected a stock symbol and country", "summarys": []}))
        return 1
    try:
        summaries = get_summaries(argv[0], argv[1])
        print(json.dumps({"summarys": summaries}, ensure_ascii=False))
        return 0
    except (requests.RequestException, ValueError, SummaryUnavailable) as error:
        print(json.dumps({"error": str(error), "summarys": []}, ensure_ascii=False))
        return 1


if __name__ == "__main__":
    sys.exit(main())
