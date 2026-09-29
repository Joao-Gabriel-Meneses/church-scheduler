-- Backup diário do schema ESCALA_APP para o Object Storage (Data Pump).
-- Executar como ADMIN em Database Actions → SQL (modo script, F5). Troque todos os <...>.
-- Guia completo: docs/deploy.md, seção "Backup diário".

-- 1. Credencial do Object Storage (usuário OCI + auth token).
begin
    dbms_cloud.create_credential(
        credential_name => 'ESCALA_BACKUP',
        username        => '<usuario-oci>',
        password        => '<auth-token>');
end;
/

-- 2. Exporta o schema para o DATA_PUMP_DIR, envia ao bucket e apaga a cópia local.
create or replace procedure escala_backup_diario as
    c_bucket  constant varchar2(400) :=
        'https://objectstorage.sa-saopaulo-1.oraclecloud.com/n/<namespace>/b/escala-backup/o/';
    l_base    varchar2(100) :=
        'escala_' || to_char(systimestamp at time zone 'America/Sao_Paulo', 'YYYYMMDD_HH24MI');
    l_handle  number;
    l_estado  varchar2(30);
begin
    l_handle := dbms_datapump.open(operation => 'EXPORT', job_mode => 'SCHEMA');
    dbms_datapump.add_file(l_handle, l_base || '.dmp', 'DATA_PUMP_DIR');
    dbms_datapump.add_file(l_handle, l_base || '.log', 'DATA_PUMP_DIR',
                           filetype => dbms_datapump.ku$_file_type_log_file);
    dbms_datapump.metadata_filter(l_handle, 'SCHEMA_EXPR', q'[= 'ESCALA_APP']');
    dbms_datapump.start_job(l_handle);
    dbms_datapump.wait_for_job(l_handle, l_estado);

    if l_estado <> 'COMPLETED' then
        raise_application_error(-20001, 'Export do backup terminou com estado ' || l_estado);
    end if;

    dbms_cloud.put_object('ESCALA_BACKUP', c_bucket || l_base || '.dmp', 'DATA_PUMP_DIR', l_base || '.dmp');
    dbms_cloud.put_object('ESCALA_BACKUP', c_bucket || l_base || '.log', 'DATA_PUMP_DIR', l_base || '.log');
    dbms_cloud.delete_file('DATA_PUMP_DIR', l_base || '.dmp');
    dbms_cloud.delete_file('DATA_PUMP_DIR', l_base || '.log');
end;
/

-- 3. Agenda para todo dia às 3h no horário de Brasília.
begin
    dbms_scheduler.create_job(
        job_name        => 'ESCALA_BACKUP_DIARIO',
        job_type        => 'STORED_PROCEDURE',
        job_action      => 'ESCALA_BACKUP_DIARIO',
        start_date      => to_timestamp_tz('2026-01-01 03:00 America/Sao_Paulo', 'YYYY-MM-DD HH24:MI TZR'),
        repeat_interval => 'FREQ=DAILY;BYHOUR=3;BYMINUTE=0',
        enabled         => true,
        comments        => 'Backup diário do schema ESCALA_APP para o Object Storage');
end;
/
