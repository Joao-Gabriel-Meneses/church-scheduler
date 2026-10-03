-- Restaura um dump do Object Storage num schema VAZIO (nunca por cima do de produção). Compatível com Oracle 19c.
-- Executar como ADMIN em Database Actions → SQL (modo script, F5), com o DBMS_OUTPUT ligado. Troque todos os <...>.
-- <arquivo>: nome do dump no bucket, sem a extensão (ex.: escala_app_20261003_030000).
-- Roda em blocos anônimos, em que as roles do ADMIN (IMP_FULL_DATABASE) valem; nada aqui é procedure.
-- Guia: docs/deploy.md, passo 7 ("Testar o restore").

-- 1. Credencial do ADMIN para ler o bucket (uma vez só; se já existir, pule este bloco).
--    A ESCALA_BACKUP pertence ao ESCALA_APP e não serve aqui; use o mesmo usuário OCI e auth token.
begin
    dbms_cloud.create_credential(
        credential_name => 'ESCALA_RESTAURO',
        username        => '<usuario-oci>',
        password        => '<auth-token>');
end;
/

-- 2. Schema de destino vazio. O dump foi feito pelo próprio ESCALA_APP e não traz o CREATE USER: sem o usuário
--    criado antes, o import falha.
create user escala_restaurado identified by "<senha-forte>";
grant create session, create table, create sequence, create view,
      create procedure, create trigger to escala_restaurado;
alter user escala_restaurado quota unlimited on data;

-- 3. Baixa o dump do bucket para o DATA_PUMP_DIR.
begin
    dbms_cloud.get_object(
        credential_name => 'ESCALA_RESTAURO',
        object_uri      =>
            'https://objectstorage.sa-saopaulo-1.oraclecloud.com/n/<namespace>/b/escala-backup/o/<arquivo>.dmp',
        directory_name  => 'DATA_PUMP_DIR');
end;
/

-- 4. Importa trocando o schema. PROCOBJ fica de fora: o job de backup não renasce no schema restaurado (e a credencial,
--    que não sai no dump, não faria falta a ele).
declare
    l_handle  number;
    l_estado  varchar2(30);
begin
    l_handle := dbms_datapump.open(operation => 'IMPORT', job_mode => 'SCHEMA');
    dbms_datapump.add_file(l_handle, '<arquivo>.dmp', 'DATA_PUMP_DIR');
    dbms_datapump.add_file(l_handle, '<arquivo>-import.log', 'DATA_PUMP_DIR',
                           filetype => dbms_datapump.ku$_file_type_log_file);
    dbms_datapump.metadata_remap(l_handle, 'REMAP_SCHEMA', 'ESCALA_APP', 'ESCALA_RESTAURADO');
    dbms_datapump.metadata_filter(l_handle, 'EXCLUDE_PATH_EXPR', 'IN (''PROCOBJ'')');
    dbms_datapump.start_job(l_handle);
    dbms_datapump.wait_for_job(l_handle, l_estado);
    dbms_output.put_line('Import: ' || l_estado);
end;
/

-- 5. Confere: mesmas tabelas e mesmas linhas no ESCALA_APP e no restaurado. Rode logo depois do backup testado; uso do
--    app entre o dump e a conferência (ex.: uma auditoria nova) aparece como diferença.
declare
    l_origem   number;
    l_destino  number;
    l_erros    pls_integer := 0;
begin
    for t in (select table_name from all_tables where owner = 'ESCALA_APP' order by table_name) loop
        execute immediate 'select count(*) from "ESCALA_APP"."' || t.table_name || '"' into l_origem;
        begin
            execute immediate 'select count(*) from "ESCALA_RESTAURADO"."' || t.table_name || '"' into l_destino;
        exception
            when others then
                l_destino := -1;
        end;
        dbms_output.put_line(rpad(t.table_name, 32) || lpad(l_origem, 10) || lpad(l_destino, 10)
                             || case when l_origem <> l_destino then '   <-- diferente' end);
        if l_origem <> l_destino then
            l_erros := l_erros + 1;
        end if;
    end loop;
    dbms_output.put_line(case when l_erros = 0 then 'Restore conferido: tudo igual.'
                              else l_erros || ' tabela(s) diferente(s).' end);
end;
/

-- 6. Última migração aplicada nos dois schemas (deve ser a mesma). A versão é texto: ordena pelo installed_rank.
select 'ESCALA_APP' as esquema,
       (select "version" from escala_app."flyway_schema_history"
         order by "installed_rank" desc fetch first 1 rows only) as versao
  from dual
union all
select 'ESCALA_RESTAURADO',
       (select "version" from escala_restaurado."flyway_schema_history"
         order by "installed_rank" desc fetch first 1 rows only)
  from dual;

-- 7. Limpa o diretório. Depois de conferir, apague o schema: drop user escala_restaurado cascade;
begin
    dbms_cloud.delete_file('DATA_PUMP_DIR', '<arquivo>.dmp');
    dbms_cloud.delete_file('DATA_PUMP_DIR', '<arquivo>-import.log');
end;
/
