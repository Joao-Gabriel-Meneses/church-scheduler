-- Restaura um dump do Object Storage em OUTRO schema (nunca por cima do de produção). Compatível com Oracle 19c.
-- Executar como ADMIN em Database Actions → SQL (modo script, F5). Troque todos os <...>.
-- <arquivo>: nome do dump no bucket, sem a extensão (ex.: escala_app_20260101_030000).
-- <schema-origem>: schema exportado (ESCALA_APP ou ESCALA_VALIDACAO). <schema-destino>: ex. ESCALA_RESTAURADO.

-- 1. Baixa o dump do bucket para o DATA_PUMP_DIR.
begin
    dbms_cloud.get_object(
        credential_name => 'ESCALA_BACKUP',
        object_uri      =>
            'https://objectstorage.sa-saopaulo-1.oraclecloud.com/n/<namespace>/b/escala-backup/o/<arquivo>.dmp',
        directory_name  => 'DATA_PUMP_DIR');
end;
/

-- 2. Importa trocando o schema. O Data Pump cria o schema de destino se ele não existir.
declare
    l_handle  number;
    l_estado  varchar2(30);
begin
    l_handle := dbms_datapump.open(operation => 'IMPORT', job_mode => 'SCHEMA');
    dbms_datapump.add_file(l_handle, '<arquivo>.dmp', 'DATA_PUMP_DIR');
    dbms_datapump.add_file(l_handle, '<arquivo>-import.log', 'DATA_PUMP_DIR',
                           filetype => dbms_datapump.ku$_file_type_log_file);
    dbms_datapump.metadata_remap(l_handle, 'REMAP_SCHEMA', '<schema-origem>', '<schema-destino>');
    dbms_datapump.start_job(l_handle);
    dbms_datapump.wait_for_job(l_handle, l_estado);
    dbms_output.put_line('Import: ' || l_estado);
end;
/

-- 3. Confira os dados restaurados.
select count(*) as usuarios from <schema-destino>.usuario;

-- 4. Limpe o arquivo baixado (e, depois de conferir, o schema restaurado).
begin
    dbms_cloud.delete_file('DATA_PUMP_DIR', '<arquivo>.dmp');
    dbms_cloud.delete_file('DATA_PUMP_DIR', '<arquivo>-import.log');
end;
/
