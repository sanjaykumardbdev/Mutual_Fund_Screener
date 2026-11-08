ALTER SESSION SET CONTAINER = ORCLPDB;
-- =============================================
-- 00_create_schema_and_tablespace.sql
-- Create Dedicated Tablespace + Schema (User) for Population ETL Project
-- =============================================

-- =============================================
-- STEP 1: Create Dedicated Tablespace
-- =============================================
CREATE TABLESPACE mutual_fund_etl_sys
DATAFILE 'D:/Oracle_19c/oradata/ORCL/mutual_fund_etl_sys_01.dbf'
SIZE 500M 
AUTOEXTEND ON 
NEXT 100M 
MAXSIZE UNLIMITED
EXTENT MANAGEMENT LOCAL 
SEGMENT SPACE MANAGEMENT AUTO;

-- Optional: Create additional datafile if needed later
-- ALTER TABLESPACE mutual_fund_etl_sys ADD DATAFILE '/u01/app/oracle/oradata/ORCL/mutual_fund_etl_sys_02.dbf' SIZE 300M AUTOEXTEND ON;

-- Create Index Tablespace (Recommended best practice)
CREATE TABLESPACE mutual_fund_etl_idx_ts
DATAFILE 'D:/Oracle_19c/oradata/ORCL/mutual_fund_etl_idx_ts_01.dbf' 
SIZE 300M 
AUTOEXTEND ON 
NEXT 50M 
MAXSIZE UNLIMITED
EXTENT MANAGEMENT LOCAL 
SEGMENT SPACE MANAGEMENT AUTO;

-- =============================================
-- STEP 2: Create New Schema (User)
-- =============================================
CREATE USER mutual_fund_etl IDENTIFIED BY "mutual_fund_etl"
DEFAULT TABLESPACE mutual_fund_etl_sys
TEMPORARY TABLESPACE temp
QUOTA UNLIMITED ON mutual_fund_etl_sys
QUOTA UNLIMITED ON mutual_fund_etl_idx_ts;

-- =============================================
-- STEP 3: Grant Necessary Privileges
-- =============================================
GRANT CONNECT, RESOURCE TO mutual_fund_etl;
GRANT CREATE SESSION TO mutual_fund_etl;
GRANT CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE SYNONYM TO mutual_fund_etl;

GRANT CREATE PROCEDURE TO mutual_fund_etl;
GRANT CREATE FUNCTION TO mutual_fund_etl;
GRANT CREATE PACKAGE TO mutual_fund_etl;


GRANT CREATE MATERIALIZED VIEW TO mutual_fund_etl;
GRANT CREATE TRIGGER TO mutual_fund_etl;

Grants for DBMS_SCHEDULER and advanced features
GRANT EXECUTE ON DBMS_SCHEDULER TO mutual_fund_etl;
GRANT EXECUTE ON DBMS_LOCK TO mutual_fund_etl;
GRANT SELECT ON DBA_SCHEDULER_JOBS TO mutual_fund_etl;
GRANT SELECT ON DBA_SCHEDULER_JOB_RUN_DETAILS TO mutual_fund_etl;

-- For SQL*Loader and external table access (if needed)
GRANT CREATE ANY DIRECTORY TO mutual_fund_etl;
GRANT UNLIMITED TABLESPACE TO mutual_fund_etl;   -- Optional, but useful during development

-- =============================================
-- STEP 4: Create Directory Objects (for SQL*Loader / External Tables)
-- =============================================
CREATE OR REPLACE DIRECTORY mutual_fund_etl_DATA_DIR AS 'D:/Oracle_19c/oradata/ORCL/data_files';
CREATE OR REPLACE DIRECTORY mutual_fund_etl_LOG_DIR  AS 'D:/Oracle_19c/oradata/ORCL/log_files';

GRANT READ, WRITE ON DIRECTORY mutual_fund_etl_DATA_DIR TO mutual_fund_etl;
GRANT READ, WRITE ON DIRECTORY mutual_fund_etl_LOG_DIR  TO mutual_fund_etl;

