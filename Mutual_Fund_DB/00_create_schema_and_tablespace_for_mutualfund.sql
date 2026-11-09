-- Run as a DBA while connected to the target Oracle 19c PDB.
-- Update data_dir to a directory on the database server that the Oracle
-- service account can write to. The directory must already exist.

DEFINE data_dir = D:\Oracle_19c\oradata\ORCL

ACCEPT app_password CHAR PROMPT 'Enter a strong password for MUTUAL_FUND_APP: ' HIDE

CREATE TABLESPACE mutual_fund_app_ts
    DATAFILE '&data_dir\mutual_fund_app_01.dbf'
    SIZE 256M
    AUTOEXTEND ON NEXT 64M MAXSIZE 10G
    EXTENT MANAGEMENT LOCAL
    SEGMENT SPACE MANAGEMENT AUTO;

CREATE USER mutual_fund_app IDENTIFIED BY "mutual_fund_app"
    DEFAULT TABLESPACE mutual_fund_app_ts
    TEMPORARY TABLESPACE temp
    QUOTA 10G ON mutual_fund_app_ts;

GRANT CREATE SESSION,
      CREATE TABLE,
      CREATE VIEW,
      CREATE SEQUENCE,
      CREATE PROCEDURE,
      CREATE TRIGGER,
      CREATE TYPE
TO mutual_fund_app;

UNDEFINE app_password
