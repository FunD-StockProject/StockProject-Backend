'''해외주식종목코드 정제 파이썬 파일
미국 : nasmst.cod, nysmst.cod, amsmst.cod, 
중국 : shsmst.cod, shimst.cod, szsmst.cod, szimst.cod, 
일본 : tsemst.cod, 
홍콩 : hksmst.cod, 
베트남 : hnxmst.cod, hsxmst.cod'''

'''
※ 유의사항 ※
실행 환경 혹은 원본 파일의 칼럼 수의 변경으로 간혹 정제코드 파일(overseas_stock_code.py)이 실행되지 않을 수 있습니다.
해당 경우, URL에 아래 링크를 복사+붙여넣기 하여 원본 파일을 다운로드하시기 바랍니다.
. https://new.real.download.dws.co.kr/common/master/{val}mst.cod.zip
. {val} 자리에 원하시는 시장코드를 넣어주세요.
. 'nas','nys','ams','shs','shi','szs','szi','tse','hks','hnx','hsx'
. 순서대로 나스닥, 뉴욕, 아멕스, 상해, 상해지수, 심천, 심천지수, 도쿄, 홍콩, 하노이, 호치민
'''

import pandas as pd
import urllib.request
import ssl
import zipfile
import os

base_dir = os.getcwd()

def get_overseas_master_dataframe(base_dir,val):

    ssl._create_default_https_context = ssl._create_unverified_context
    
    # ZIP 파일 경로
    zip_file_path = os.path.join(base_dir, f"{val}mst.cod.zip")
    # 압축 해제된 파일 경로 (대소문자 구분 없이 찾기)
    cod_file_path_lower = os.path.join(base_dir, f"{val}mst.cod")
    cod_file_path_upper = os.path.join(base_dir, f"{val.upper()}MST.COD")
    cod_file_path = None  # 실제 파일 경로를 찾아서 설정
    
    try:
        # ZIP 파일 다운로드
        print(f"Downloading...{val}mst.cod.zip")
        urllib.request.urlretrieve(
            f"https://new.real.download.dws.co.kr/common/master/{val}mst.cod.zip", 
            zip_file_path
        )
        
        # ZIP 파일이 다운로드되었는지 확인
        if not os.path.exists(zip_file_path):
            raise FileNotFoundError(f"ZIP file not found after download: {zip_file_path}")
        
        # ZIP 파일 압축 해제
        with zipfile.ZipFile(zip_file_path, 'r') as overseas_zip:
            # ZIP 파일 내부의 파일 목록 확인
            file_list = overseas_zip.namelist()
            print(f"ZIP file contains: {file_list}")
            
            # .cod 파일 찾기
            cod_file_in_zip = None
            for file_name in file_list:
                if file_name.endswith('.cod') or file_name.endswith(f'{val}mst.cod'):
                    cod_file_in_zip = file_name
                    break
            
            if cod_file_in_zip is None:
                # .cod 파일이 없으면 첫 번째 파일 사용
                if file_list:
                    cod_file_in_zip = file_list[0]
                else:
                    raise FileNotFoundError(f"No files found in ZIP: {zip_file_path}")
            
            # 압축 해제
            overseas_zip.extractall(base_dir)
            
            # 압축 해제된 파일의 실제 경로 확인
            extracted_file_path = os.path.join(base_dir, cod_file_in_zip)
            
            # 파일이 하위 디렉토리에 있을 수 있으므로 실제 경로 확인
            if not os.path.exists(extracted_file_path):
                # 하위 디렉토리에서 찾기
                for root, dirs, files in os.walk(base_dir):
                    for file in files:
                        if file.endswith('.cod') and val in file.lower():
                            extracted_file_path = os.path.join(root, file)
                            break
                    if os.path.exists(extracted_file_path):
                        break
            
            # 최종 파일 경로로 이동 또는 복사
            if os.path.exists(extracted_file_path):
                # 실제 파일 경로 설정
                cod_file_path = extracted_file_path
                
                # base_dir에 올바른 이름으로 이동 (대소문자 무시)
                # 대문자 파일명이 이미 있으면 그대로 사용, 없으면 소문자로 이동
                if extracted_file_path.upper() != cod_file_path_upper:
                    import shutil
                    # 대문자 파일명이 이미 존재하는지 확인
                    if os.path.exists(cod_file_path_upper):
                        # 이미 대문자 파일이 있으면 extracted 파일 삭제
                        if extracted_file_path != cod_file_path_upper:
                            try:
                                os.remove(extracted_file_path)
                            except:
                                pass
                        cod_file_path = cod_file_path_upper
                    else:
                        # 대문자 파일명으로 이동
                        if os.path.exists(cod_file_path_lower):
                            os.remove(cod_file_path_lower)
                        shutil.move(extracted_file_path, cod_file_path_upper)
                        cod_file_path = cod_file_path_upper
                
                # 원본 디렉토리 정리
                extracted_dir = os.path.dirname(extracted_file_path)
                if extracted_dir != base_dir and os.path.exists(extracted_dir):
                    try:
                        if os.listdir(extracted_dir) == []:
                            os.rmdir(extracted_dir)
                    except:
                        pass
        
        # 압축 해제된 파일이 존재하는지 확인 (대소문자 구분 없이)
        if cod_file_path is None or not os.path.exists(cod_file_path):
            # 대소문자 구분 없이 파일 찾기
            found_file = None
            if os.path.exists(base_dir):
                for file in os.listdir(base_dir):
                    file_upper = file.upper()
                    expected_upper = f"{val.upper()}MST.COD"
                    if file_upper == expected_upper:
                        found_file = os.path.join(base_dir, file)
                        break
            
            if found_file:
                cod_file_path = found_file
            else:
                raise FileNotFoundError(f"Extracted file not found. Expected: {val}mst.cod or {val.upper()}MST.COD in {base_dir}")
        
        # 파일 읽기
        columns = ['National code', 'Exchange id', 'Exchange code', 'Exchange name', 'Symbol', 'realtime symbol', 'Korea name', 'English name', 'Security type(1:Index,2:Stock,3:ETP(ETF),4:Warrant)', 'currency', 'float position', 'data type', 'base price', 'Bid order size', 'Ask order size', 'market start time(HHMM)', 'market end time(HHMM)', 'DR 여부(Y/N)', 'DR 국가코드', '업종분류코드', '지수구성종목 존재 여부(0:구성종목없음,1:구성종목있음)', 'Tick size Type', '구분코드(001:ETF,002:ETN,003:ETC,004:Others,005:VIX Underlying ETF,006:VIX Underlying ETN)','Tick size type 상세']
        
        df = pd.read_table(cod_file_path, sep='\t', encoding='cp949')
        df.columns = columns
        
        # 엑셀 파일 저장 (선택사항) - openpyxl이 없으면 스킵
        try:
            excel_path = os.path.join(base_dir, f'{val}_code.xlsx')
            df.to_excel(excel_path, index=False)
        except ImportError:
            # openpyxl이 없으면 엑셀 저장 스킵
            pass
        except Exception as e:
            # 기타 에러는 무시
            print(f"Warning: Could not save Excel file: {e}")
        
        return df
        
    except Exception as e:
        # 에러 발생 시 정리
        if os.path.exists(zip_file_path):
            try:
                os.remove(zip_file_path)
            except:
                pass
        raise e

# 자동 실행 코드 주석 처리 (테스트를 위해)
# cmd = input("1:전부 다운로드, 2:1개의 시장을 다운로드 \n")
cmd = None  # 테스트를 위해 None으로 설정

if cmd =='1': # 1. 해외종목코드전체 코드를 다운로드
    
    # 순서대로 나스닥, 뉴욕, 아멕스, 상해, 상해지수, 심천, 심천지수, 도쿄, 홍콩, 하노이, 호치민
    lst = ['nas','nys','ams','shs','shi','szs','szi','tse','hks','hnx','hsx'] 

    DF=pd.DataFrame()
    for i in lst:
        temp = get_overseas_master_dataframe(base_dir,i)
        DF = pd.concat([DF,temp],axis=0)
    print(f"Downloading...overseas_stock_code(all).xlsx")
    DF.to_excel('overseas_stock_code(all).xlsx',index=False)  # 전체 통합파일
    print("Done")

elif cmd == '2': # 2. 해외종목코드 전체 코드를 다운로드
    # 자동 실행 코드 주석 처리 (테스트를 위해)
    pass
    # while True:
    #     cmd2 = input("다운로드하시고자 하는 시장의 코드를 입력하여 주세요. \nnas:나스닥, nys:뉴욕, ams:아멕스, shs:상해, shi:상해지수, szs:심천, szi:심천지수, tse:도쿄, hks:홍콩, hnx:하노이, hsx:호치민\n")
    #     try:
    #         df = get_overseas_master_dataframe(base_dir,cmd2)
    #         print("Done")
    #         break;
    #     except:
    #         pass