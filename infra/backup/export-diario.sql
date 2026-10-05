-- Backup diário do schema ESCALA_APP para o Object Storage (Data Pump). Compatível com Oracle 19c.
-- Executar CONECTADO COMO ESCALA_APP (Database Actions → SQL, modo script, F5), depois dos GRANTs diretos do
-- docs/deploy.md (passo 7). Troque todos os <...>.
--
-- Tudo pertence ao ESCALA_APP: a credencial, a procedure (AUTHID DEFINER) e o job. Nada depende de role: privilégio
-- recebido por role não vale dentro de procedure com os direitos do dono, nem no job do Scheduler. Privilégios usados,
-- todos concedidos direto ao ESCALA_APP:
--   CREATE TABLE e quota  → tabela mestre do Data Pump
--   READ, WRITE no DATA_PUMP_DIR → dump e log antes do envio
--   EXECUTE no DBMS_CLOUD → credencial, envio ao bucket e limpeza do diretório
--   CREATE JOB            → job diário
-- O DBMS_DATAPUMP é de PUBLIC, e exportar o próprio schema não exige EXP_FULL_DATABASE.

-- 1. Credencial do Object Storage: usuário OCI só com acesso ao bucket + auth token dele.
begin
    dbms_cloud.create_credential(
        credential_name => 'ESCALA_BACKUP',
        username        => '<usuario-oci>',
        password        => '<auth-token>');
end;
/

-- 2. Exporta o próprio schema para o DATA_PUMP_DIR, envia o dump e o log ao bucket e apaga as cópias locais.
create or replace procedure escala_backup_diario
    authid definer
as
    c_bucket  constant varchar2(400) :=
        'https://objectstorage.sa-saopaulo-1.oraclecloud.com/n/<namespace>/b/escala-backup/o/';
    -- Com AUTHID DEFINER, CURRENT_USER é o dono da procedure (ESCALA_APP), quem quer que a chame.
    l_schema  constant varchar2(128) := sys_context('USERENV', 'CURRENT_USER');
    l_base    constant varchar2(200) :=
        lower(l_schema) || '_' || to_char(systimestamp at time zone 'America/Sao_Paulo', 'YYYYMMDD_HH24MISS');
    l_handle  number;
    l_estado  varchar2(30);
begin
    l_handle := dbms_datapump.open(operation => 'EXPORT', job_mode => 'SCHEMA');
    dbms_datapump.add_file(l_handle, l_base || '.dmp', 'DATA_PUMP_DIR');
    dbms_datapump.add_file(l_handle, l_base || '.log', 'DATA_PUMP_DIR',
                           filetype => dbms_datapump.ku$_file_type_log_file);
    dbms_datapump.metadata_filter(l_handle, 'SCHEMA_EXPR', 'IN (''' || l_schema || ''')');
    dbms_datapump.start_job(l_handle);
    dbms_datapump.wait_for_job(l_handle, l_estado);

    if l_estado <> 'COMPLETED' then
        raise_application_error(-20001, 'Export de ' || l_schema || ' terminou com estado ' || l_estado);
    end if;

    dbms_cloud.put_object('ESCALA_BACKUP', c_bucket || l_base || '.dmp', 'DATA_PUMP_DIR', l_base || '.dmp');
    dbms_cloud.put_object('ESCALA_BACKUP', c_bucket || l_base || '.log', 'DATA_PUMP_DIR', l_base || '.log');
    dbms_cloud.delete_file('DATA_PUMP_DIR', l_base || '.dmp');
    dbms_cloud.delete_file('DATA_PUMP_DIR', l_base || '.log');
    dbms_output.put_line('Backup enviado: ' || l_base || '.dmp');
exception
    when others then
        -- Não deixa o job do Data Pump pendurado; o erro original é relançado.
        if l_handle is not null then
            begin
                dbms_datapump.detach(l_handle);
            exception
                when others then
                    null;
            end;
        end if;
        raise;
end;
/

-- 3. Job do ESCALA_APP, todo dia às 3h no horário de Brasília.
begin
    dbms_scheduler.create_job(
        job_name        => 'ESCALA_BACKUP_DIARIO_JOB',
        job_type        => 'STORED_PROCEDURE',
        job_action      => 'ESCALA_BACKUP_DIARIO',
        start_date      => to_timestamp_tz('2026-01-01 03:00 America/Sao_Paulo', 'YYYY-MM-DD HH24:MI TZR'),
        repeat_interval => 'FREQ=DAILY;BYHOUR=3;BYMINUTE=0',
        enabled         => true,
        comments        => 'Backup diário do schema ESCALA_APP para o Object Storage');
end;
/
