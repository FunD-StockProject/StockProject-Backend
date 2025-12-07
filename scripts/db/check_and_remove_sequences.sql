-- Hibernate 시퀀스 테이블 확인 및 삭제 스크립트
-- 이 스크립트는 MySQL에서 hibernate_sequences 테이블이 존재하는지 확인하고 삭제합니다.

-- ============================================
-- 1단계: 시퀀스 테이블 확인
-- ============================================
SELECT 
    TABLE_NAME,
    TABLE_TYPE
FROM 
    INFORMATION_SCHEMA.TABLES
WHERE 
    TABLE_SCHEMA = DATABASE()
    AND (TABLE_NAME = 'hibernate_sequences' 
         OR TABLE_NAME = 'stock_sequence'
         OR TABLE_NAME LIKE '%_sequence%');

-- ============================================
-- 2단계: 시퀀스 테이블 삭제 (확인 후 실행)
-- 주의: 이 명령을 실행하기 전에 반드시 백업을 수행하세요!
-- ============================================
-- hibernate_sequences 테이블 삭제
DROP TABLE IF EXISTS hibernate_sequences;

-- stock_sequence 테이블 삭제 (만약 있다면)
DROP TABLE IF EXISTS stock_sequence;

-- ============================================
-- 3단계: stock 테이블의 AUTO_INCREMENT 확인
-- ============================================
-- stock 테이블 구조 확인 (AUTO_INCREMENT가 설정되어 있는지 확인)
SHOW CREATE TABLE stock;

-- stock 테이블의 현재 AUTO_INCREMENT 값 확인
SELECT 
    AUTO_INCREMENT
FROM 
    INFORMATION_SCHEMA.TABLES
WHERE 
    TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'stock';

-- ============================================
-- 4단계: stock 테이블의 ID 컬럼이 AUTO_INCREMENT인지 확인
-- ============================================
SELECT 
    COLUMN_NAME,
    COLUMN_TYPE,
    EXTRA
FROM 
    INFORMATION_SCHEMA.COLUMNS
WHERE 
    TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'stock'
    AND COLUMN_NAME = 'id';

