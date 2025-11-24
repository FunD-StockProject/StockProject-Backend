#!/usr/bin/env python3
"""
주식 종목 마스터 데이터를 가져와서 Stock 엔티티에 저장하기 위한 데이터 수집 스크립트
open-trading-api의 종목 마스터 파일을 사용하여 종목 정보를 수집합니다.
"""

import sys
import os
import json
import pandas as pd

# stocks_info 모듈 경로 추가 (scripts/stocks_info 폴더 사용)
stocks_info_path = os.path.join(os.path.dirname(__file__), 'stocks_info')
# 로컬 개발 환경에서는 open-trading-api/stocks_info도 시도
if not os.path.exists(stocks_info_path):
    stocks_info_path = os.path.join(os.path.dirname(__file__), '..', 'open-trading-api', 'stocks_info')
sys.path.insert(0, stocks_info_path)

try:
    from kis_kospi_code_mst import get_kospi_master_dataframe, kospi_master_download
    from kis_kosdaq_code_mst import get_kosdaq_master_dataframe, kosdaq_master_download
    from overseas_stock_code import get_overseas_master_dataframe
except ImportError as e:
    print(f"Error importing modules: {e}")
    print("Please ensure open-trading-api/stocks_info modules are available")
    sys.exit(1)

def map_korea_exchange(exchange_name):
    """한국 거래소 이름을 EXCHANGENUM으로 매핑"""
    if '코스피' in exchange_name or 'KOSPI' in exchange_name:
        return 'KOSPI'
    elif '코스닥' in exchange_name or 'KOSDAQ' in exchange_name:
        return 'KOSDAQ'
    elif 'ETF' in exchange_name or 'ETP' in exchange_name:
        return 'KOREAN_ETF'
    return 'KOSPI'  # 기본값

def map_overseas_exchange(exchange_id, exchange_code):
    """해외 거래소를 EXCHANGENUM으로 매핑"""
    # Exchange id: NAS=512, NYS=513, AMS=529
    exchange_id_str = str(exchange_id).strip()
    if exchange_id_str == '512' or 'nas' in str(exchange_code).lower():
        return 'NAS'
    elif exchange_id_str == '513' or 'nys' in str(exchange_code).lower():
        return 'NYS'
    elif exchange_id_str == '529' or 'ams' in str(exchange_code).lower():
        return 'AMS'
    return None

def process_korea_stocks():
    """국내 주식 종목 데이터 처리"""
    base_dir = os.path.join(os.path.dirname(__file__), '..', 'open-trading-api', 'stocks_info')
    os.makedirs(base_dir, exist_ok=True)
    
    stocks = []
    
    # 코스피 종목
    print("Processing KOSPI stocks...")
    try:
        # 항상 최신 데이터 다운로드
        print("Downloading latest KOSPI master file...")
        kospi_master_download(base_dir)
        
        df_kospi = get_kospi_master_dataframe(base_dir)
        
        for _, row in df_kospi.iterrows():
            symbol = str(row['단축코드']).strip()
            symbol_name = str(row['한글명']).strip()
            # 지수업종대분류 코드 추출 (GICS 형식일 수 있음: 첫 2자리가 Sector)
            sector_code = str(row.get('지수업종대분류', '')).strip() if '지수업종대분류' in row else ''
            
            if not symbol or not symbol_name or symbol == 'nan' or symbol_name == 'nan':
                continue
            
            stock_data = {
                'symbol': symbol,
                'symbolName': symbol_name,
                'securityName': symbol_name,
                'exchangeNum': 'KOSPI',
                'valid': True
            }
            
            # 섹터 코드가 있으면 추가
            if sector_code and sector_code != 'nan' and sector_code:
                stock_data['sectorCode'] = sector_code
            
            stocks.append(stock_data)
        
        print(f"Processed {len(stocks)} KOSPI stocks")
    except Exception as e:
        print(f"Error processing KOSPI stocks: {e}")
    
    # 코스닥 종목
    print("Processing KOSDAQ stocks...")
    try:
        # 항상 최신 데이터 다운로드
        print("Downloading latest KOSDAQ master file...")
        kosdaq_master_download(base_dir)
        
        df_kosdaq = get_kosdaq_master_dataframe(base_dir)
        
        for _, row in df_kosdaq.iterrows():
            symbol = str(row['단축코드']).strip()
            symbol_name = str(row['한글종목명']).strip()
            # 지수업종 대분류 코드 추출 (GICS 형식일 수 있음: 첫 2자리가 Sector)
            sector_code = str(row.get('지수업종 대분류 코드', '')).strip() if '지수업종 대분류 코드' in row else ''
            
            if not symbol or not symbol_name or symbol == 'nan' or symbol_name == 'nan':
                continue
            
            stock_data = {
                'symbol': symbol,
                'symbolName': symbol_name,
                'securityName': symbol_name,
                'exchangeNum': 'KOSDAQ',
                'valid': True
            }
            
            # 섹터 코드가 있으면 추가
            if sector_code and sector_code != 'nan' and sector_code:
                stock_data['sectorCode'] = sector_code
            
            stocks.append(stock_data)
        
        print(f"Processed {len([s for s in stocks if s['exchangeNum'] == 'KOSDAQ'])} KOSDAQ stocks")
    except Exception as e:
        print(f"Error processing KOSDAQ stocks: {e}")
    
    return stocks

def process_overseas_stocks():
    """해외 주식 종목 데이터 처리"""
    base_dir = os.path.join(os.path.dirname(__file__), '..', 'open-trading-api', 'stocks_info')
    os.makedirs(base_dir, exist_ok=True)
    
    stocks = []
    
    # 미국 주식만 처리 (NAS, NYS, AMS)
    overseas_markets = ['nas', 'nys', 'ams']
    
    for market in overseas_markets:
        print(f"Processing {market.upper()} stocks...")
        try:
            df = get_overseas_master_dataframe(base_dir, market)
            
            for _, row in df.iterrows():
                # Security type이 2(Stock)인 것만 처리
                security_type = str(row.get('Security type(1:Index,2:Stock,3:ETP(ETF),4:Warrant)', '')).strip()
                if security_type != '2':
                    continue
                
                symbol = str(row.get('Symbol', '')).strip()
                korea_name = str(row.get('Korea name', '')).strip()
                english_name = str(row.get('English name', '')).strip()
                exchange_id = row.get('Exchange id', '')
                # 업종분류코드 추출 (GICS 형식: 첫 2자리가 Sector, 예: 101010 -> 10)
                sector_code = str(row.get('업종분류코드', '')).strip() if '업종분류코드' in row else ''
                
                if not symbol or symbol == 'nan':
                    continue
                
                exchange_num = map_overseas_exchange(exchange_id, market)
                if not exchange_num:
                    continue
                
                # 한국 이름이 있으면 사용, 없으면 영어 이름 사용
                symbol_name = korea_name if korea_name and korea_name != 'nan' else english_name
                security_name = english_name if english_name and english_name != 'nan' else symbol_name
                
                if not symbol_name or symbol_name == 'nan':
                    continue
                
                stock_data = {
                    'symbol': symbol,
                    'symbolName': symbol_name,
                    'securityName': security_name,
                    'exchangeNum': exchange_num,
                    'valid': True
                }
                
                # 섹터 코드가 있으면 추가
                if sector_code and sector_code != 'nan' and sector_code:
                    stock_data['sectorCode'] = sector_code
                
                stocks.append(stock_data)
            
            print(f"Processed {len([s for s in stocks if s['exchangeNum'] == exchange_num])} {market.upper()} stocks")
        except Exception as e:
            print(f"Error processing {market} stocks: {e}")
    
    return stocks

def main():
    """메인 함수"""
    print("Starting stock data collection...")
    
    all_stocks = []
    
    # 국내 주식 처리
    korea_stocks = process_korea_stocks()
    all_stocks.extend(korea_stocks)
    
    # 해외 주식 처리
    overseas_stocks = process_overseas_stocks()
    all_stocks.extend(overseas_stocks)
    
    # 결과를 JSON 파일로 저장
    output_file = os.path.join(os.path.dirname(__file__), 'stocks_data.json')
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(all_stocks, f, ensure_ascii=False, indent=2)
    
    print(f"\nTotal stocks collected: {len(all_stocks)}")
    print(f"Korea stocks: {len(korea_stocks)}")
    print(f"Overseas stocks: {len(overseas_stocks)}")
    print(f"Data saved to: {output_file}")
    print("\nNext step: Import this JSON file into the database using Java service")

if __name__ == '__main__':
    main()

