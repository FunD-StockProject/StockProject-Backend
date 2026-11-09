import contextlib
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import requests


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parents[1] / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


update = load('score_update_under_test', 'update.py')
summary = load('stock_summary_under_test', 'summary.py')


class Response:
    def __init__(self, payload, status=200):
        self.payload = payload
        self.status_code = status

    def json(self):
        return self.payload

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError('Provider HTTP ' + str(self.status_code))


class Session:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def get(self, url, **kwargs):
        self.calls.append((url, kwargs))
        result = self.responses.pop(0)
        if isinstance(result, Exception):
            raise result
        return result

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


def discussion(posts, cursor=None, hot=False):
    key = 'hotPosts' if hot else 'posts'
    return Response({'isSuccess': True, 'result': {key: posts, 'lastOffset': cursor}})


class ScoreInputTests(unittest.TestCase):
    def test_null_title_body_writer_and_counts_are_safe(self):
        post = update.normalize_post({'title': None, 'contentSwReplaced': None, 'writer': None,
                                      'recommendCount': None, 'notRecommendCount': None}, 'contentSwReplaced')
        self.assertEqual(post['title'], '')
        self.assertEqual(post['content'], '')
        self.assertEqual(post['recommendCount'], 0)
        self.assertFalse(post['isHolderVerified'])

    def test_keeps_available_title_when_body_is_null(self):
        posts = [{'title': '회복 기대', 'content': None, 'date': None, 'recommendCount': None,
                  'notRecommendCount': None, 'isHolderVerified': False}]
        with patch.object(update.StockCrawler, 'fetch_stock_posts', return_value=posts):
            self.assertEqual(update.fetch_texts_from_crawler('005930', 'KOREA'), [('회복 기대', 1.0)])

    def test_keeps_available_body_when_title_is_null(self):
        posts = [{'title': None, 'content': '성장 호재', 'date': '', 'recommendCount': 2,
                  'notRecommendCount': 1, 'isHolderVerified': True}]
        with patch.object(update.StockCrawler, 'fetch_stock_posts', return_value=posts):
            self.assertEqual(update.fetch_texts_from_crawler('AAPL.O', 'OVERSEA'), [('성장 호재', 6.75)])

    def test_all_blank_posts_remain_no_data_instead_of_making_up_a_score(self):
        posts = [{'title': None, 'content': None, 'date': '', 'recommendCount': None,
                  'notRecommendCount': None, 'isHolderVerified': False}]
        with patch.object(update.StockCrawler, 'fetch_stock_posts', return_value=posts):
            self.assertEqual(update.fetch_texts_from_crawler('005930', 'KOREA'), [])

    def test_missing_primary_body_uses_the_other_available_body(self):
        post = update.normalize_post({'contentSwReplaced': None, 'contentSwReplacedButImg': '본문'},
                                     'contentSwReplaced')
        self.assertEqual(post['content'], '본문')

    def test_invalid_counts_do_not_break_weight_calculation(self):
        for value in [None, '', 'invalid', {}, float('inf'), -7]:
            self.assertEqual(update.count_or_zero(value), 0)
        self.assertEqual(update.count_or_zero('3'), 3)

    def test_hot_and_regular_lists_can_contain_null_fields_and_duplicate_ids(self):
        hot = {'id': '1', 'title': '기대', 'contentSwReplacedButImg': None, 'writer': None}
        regular = {'id': '2', 'title': None, 'contentSwReplaced': '본문', 'writer': None}
        session = Session([discussion([hot], hot=True), discussion([hot, regular, None])])
        with patch.object(update.requests.Session, 'get', side_effect=session.get):
            posts = update.StockCrawler().fetch_stock_posts('005930', 'KOREA')
        self.assertEqual(len(posts), 2)
        self.assertEqual(posts[1]['content'], '본문')

    def test_null_api_collections_are_no_data(self):
        session = Session([discussion(None, hot=True), discussion(None)])
        with patch.object(update.requests.Session, 'get', side_effect=session.get):
            self.assertEqual(update.StockCrawler().fetch_stock_posts('005930', 'KOREA'), [])

    def test_pagination_uses_the_returned_offset_cursor(self):
        first = [{'id': str(i), 'title': '첫 페이지'} for i in range(50)]
        second = [{'id': str(i), 'title': '다음 페이지'} for i in range(50, 100)]
        session = Session([discussion([], hot=True), discussion(first, '-50'), discussion(second, '-100')])
        with patch.object(update.requests.Session, 'get', side_effect=session.get):
            posts = update.StockCrawler().fetch_stock_posts('005930', 'KOREA', max_posts=100)
        self.assertEqual(len(posts), 100)
        self.assertNotIn('offset=', session.calls[1][0])
        self.assertIn('offset=-50', session.calls[2][0])

    def test_repeated_cursor_and_posts_stop_without_duplicating_the_input(self):
        posts = [{'id': str(i), 'title': '게시글'} for i in range(50)]
        session = Session([discussion([], hot=True), discussion(posts, '-50'), discussion(posts, '-50')])
        with patch.object(update.requests.Session, 'get', side_effect=session.get):
            result = update.StockCrawler().fetch_stock_posts('005930', 'KOREA', max_posts=100)
        self.assertEqual(len(result), 50)
        self.assertEqual(len(session.calls), 3)


class SummaryTests(unittest.TestCase):
    def test_etf_description_is_returned_without_an_extra_profile_request(self):
        session = Session([Response({'description': '기초지수 추종<br/>분산 투자'})])
        self.assertEqual(summary.get_summaries('069500', 'KOREA', session), ['기초지수 추종', '분산 투자'])
        self.assertEqual(len(session.calls), 1)

    def test_domestic_company_falls_back_to_labelled_real_stock_facts(self):
        session = Session([
            Response({'description': None, 'totalInfos': [
                {'code': 'marketValue', 'key': '시총', 'value': '10조'},
                {'code': 'foreignRate', 'key': '외인소진율', 'value': '12.3%'}]}),
            Response({'stockName': '테스트전자', 'stockEndType': 'stock',
                      'stockExchangeType': {'nameKor': '코스피'}, 'localTradedAt': '2026-11-09T13:00:00+09:00'}),
        ])
        result = summary.get_summaries('005930', 'KOREA', session)
        self.assertEqual(result[0], '종목 기본정보: 테스트전자(005930)는 코스피에 상장된 주식입니다.')
        self.assertIn('2026-11-09 기준 시총: 10조', result[1])
        self.assertTrue(all('timeout' in kwargs for _, kwargs in session.calls))
        self.assertTrue(all('tossinvest' not in url for url, _ in session.calls))

    def test_empty_company_payload_does_not_invent_a_profile(self):
        session = Session([Response({'description': None}), Response({})])
        with self.assertRaises(summary.SummaryUnavailable):
            summary.get_summaries('005930', 'KOREA', session)

    def test_overseas_lookup_selects_the_exact_ticker(self):
        session = Session([
            Response({'result': {'items': [{'code': 'AAP', 'reutersCode': 'AAP.N'},
                                          {'code': 'AAPL', 'reutersCode': 'AAPL.O'}]}}),
            Response({'summary': '기업 설명<br>사업 부문<br>추가 설명'}),
        ])
        self.assertEqual(summary.get_summaries('AAPL', 'OVERSEA', session), ['기업 설명', '사업 부문'])
        self.assertIn('/AAPL.O/overview', session.calls[1][0])

    def test_already_resolved_overseas_code_skips_autocomplete(self):
        session = Session([Response({'summary': None, 'summaries': {'summary': '기업 개요'}})])
        self.assertEqual(summary.get_summaries('AAPL.O', 'OVERSEA', session), ['기업 개요'])
        self.assertEqual(len(session.calls), 1)

    def test_a_different_ticker_is_not_returned_for_an_unknown_symbol(self):
        session = Session([Response({'result': {'items': [{'code': 'OTHER', 'reutersCode': 'OTHER.N'}]}})])
        with self.assertRaises(summary.SummaryUnavailable):
            summary.get_summaries('UNKNOWN', 'OVERSEA', session)

    def test_null_overseas_items_are_handled(self):
        session = Session([Response({'result': {'items': None}})])
        with self.assertRaises(summary.SummaryUnavailable):
            summary.get_summaries('UNKNOWN', 'OVERSEA', session)

    def test_html_entities_and_null_lines_are_normalized(self):
        self.assertEqual(summary.summary_lines([None, '<p>기업 &amp; 사업</p>', '사업<br/>추가']),
                         ['기업 & 사업', '사업'])

    def test_provider_outage_remains_an_explicit_cli_failure(self):
        session = Session([requests.Timeout('provider timed out')])
        output = io.StringIO()
        with patch.object(summary.requests, 'Session', return_value=session), contextlib.redirect_stdout(output):
            exit_code = summary.main(['005930', 'KOREA'])
        payload = json.loads(output.getvalue())
        self.assertEqual(exit_code, 1)
        self.assertIn('error', payload)
        self.assertEqual(payload['summarys'], [])

    def test_invalid_symbol_is_rejected_before_a_network_request(self):
        session = Session([])
        with self.assertRaises(ValueError):
            summary.get_summaries('../secret', 'KOREA', session)
        self.assertEqual(session.calls, [])

    def test_cli_success_keeps_the_existing_summarys_json_contract(self):
        output = io.StringIO()
        with patch.object(summary, 'get_summaries', return_value=['검증된 설명']), contextlib.redirect_stdout(output):
            exit_code = summary.main(['005930', 'KOREA'])
        self.assertEqual(exit_code, 0)
        self.assertEqual(json.loads(output.getvalue()), {'summarys': ['검증된 설명']})


if __name__ == '__main__':
    unittest.main()
