# Deploy em produção (Oracle Cloud Always Free)

Guia para colocar a Escala no ar. Tudo aqui cabe na cota Always Free; não aumente OCPU nem memória além do indicado.

Visão geral: a VM ARM64 roda `docker compose` com **Caddy** (HTTPS) e o **app**. O banco é um **Autonomous Database 19c** acessado por TLS com a wallet. O CI publica a imagem `ghcr.io/joao-gabriel-meneses/escala:latest` a cada push na `main`.

> Recursos Always Free (A1 e Autonomous DB) só existem na **região home** da conta. Crie a conta com a home em **Brazil East (São Paulo)**.

## 1. Autonomous Database

1. **Oracle Database → Autonomous Database → Create**:
   - Workload: **Transaction Processing**
   - Marque **Always Free**
   - Versão: **19c**. Em São Paulo, o Always Free só oferece 19c; o 26ai Always Free existe apenas em algumas regiões (PHX, IAD, LHR, CDG, SYD, BOM, SIN, NRT). Os testes rodam no Oracle Free 23ai com o Hibernate fixado em 19 e um verificador de sintaxe, mas a garantia final é a validação do passo 5.
   - Nome: `escala`
   - Senha do `ADMIN`: guarde num gerenciador de senhas.
   - Acesso: *Secure access from allowed IPs and VCNs only*, liberando o IP público da VM (passo 2), com **mTLS obrigatório**.
2. **Database connection → Download wallet** (Instance wallet). A senha pedida é só do zip.
3. Em **Database Actions → SQL**, como `ADMIN`, crie o usuário da aplicação e o de validação. Nunca use o `ADMIN` no app.

   ```sql
   -- Senha do ADB: 12 a 30 caracteres, com maiúscula, minúscula e número, sem aspas.
   create user escala_app identified by "<senha-forte>";
   grant create session, create table, create sequence, create view,
         create procedure, create trigger to escala_app;
   alter user escala_app quota unlimited on data;

   -- Schema descartável para validar migrações e backup antes do deploy (passo 5).
   create user escala_validacao identified by "<outra-senha-forte>";
   grant create session, create table, create sequence, create view,
         create procedure, create trigger to escala_validacao;
   alter user escala_validacao quota unlimited on data;
   ```

## 2. VM

1. **Compute → Instances → Create**:
   - Shape: `VM.Standard.A1.Flex` com **1 OCPU e 6 GB**
   - Imagem: **Ubuntu 24.04 (aarch64)**
   - Chave SSH: a sua
   - Rede: VCN padrão com IP público. Em *Networking → Reserved public IPs*, reserve o IP, para o DNS não quebrar se a VM for recriada.
2. Na **Security List** da subnet, adicione regras de ingress `0.0.0.0/0` para TCP 80, TCP 443 e UDP 443 (HTTP/3).
3. As imagens Ubuntu da Oracle também bloqueiam no iptables. Na VM:

   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p udp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

4. Instale o Docker:

   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker ubuntu   # saia e entre de novo no SSH
   ```

## 3. DNS

Aponte um registro **A** do domínio para o IP reservado da VM **antes** de subir o Caddy, porque ele precisa do DNS certo para emitir o certificado. Qual domínio usar ainda está em aberto (ver CLAUDE.md).

## 4. Preparar a VM

```bash
sudo mkdir -p /opt/escala && sudo chown ubuntu:ubuntu /opt/escala
```

Da sua máquina, copie os arquivos de produção e a wallet:

```bash
scp compose.yaml Caddyfile .env.example ubuntu@<ip-da-vm>:/opt/escala/
scp Wallet_escala.zip ubuntu@<ip-da-vm>:/opt/escala/
```

Na VM:

```bash
cd /opt/escala
unzip Wallet_escala.zip -d wallet && rm Wallet_escala.zip
chmod 700 wallet && chmod 600 wallet/*        # o container roda como uid 1000 (ubuntu)
```

O repositório é privado, então a imagem no GHCR também é. Crie um *personal access token (classic)* só com `read:packages` e faça login uma vez:

```bash
echo <token> | docker login ghcr.io -u <seu-usuario-github> --password-stdin
```

## 5. Validar no Autonomous DB 19c (antes do primeiro deploy)

Os testes do CI rodam no Oracle Free 23ai. O dialeto fixado em 19, o verificador de sintaxe (`SqlCompativelComOracle19Test`) e a checagem do schema (`SchemaCompativelComOracle19IT`) pegam o que conseguem, mas só o banco real prova que funciona. Este passo cobre o que o CI não alcança:

- o parser do 19c;
- os privilégios;
- a wallet e o TLS;
- o `DBMS_CLOUD`;
- o Data Pump no Autonomous DB.

O CI não enxerga o ADB (o acesso é restrito ao IP da VM), então a validação roda na VM.

### 5.1 Migrações e mapeamento JPA

Rode a imagem no **modo validação**, sem servidor web. Ela aplica todas as migrações no `ESCALA_VALIDACAO`, roda o `ddl-auto=validate` do Hibernate, cria o admin (um `INSERT` real) e sai.

```bash
cd /opt/escala
read -rsp 'Senha do ESCALA_VALIDACAO: ' DB_SENHA; echo; export DB_SENHA
docker run --rm \
  -v "$PWD/wallet:/wallet:ro" \
  -e DB_URL='jdbc:oracle:thin:@escala_tp?TNS_ADMIN=/wallet' \
  -e DB_USUARIO=ESCALA_VALIDACAO \
  -e DB_SENHA \
  -e ESCALA_ADMIN_EMAIL=validacao@escala.local \
  -e ESCALA_ADMIN_SENHA=validacao-descartavel \
  -e ESCALA_CHAVE_LEMBRAR_ME=validacao \
  -e DOMINIO=validacao.local \
  -e JAVA_TOOL_OPTIONS=-Xmx512m \
  ghcr.io/joao-gabriel-meneses/escala:latest \
  --spring.main.web-application-type=none
echo "código de saída: $?"
unset DB_SENHA
```

O esperado é código de saída **0** e, no log, `Successfully applied N migrations to schema "ESCALA_VALIDACAO"`, `Started EscalaApplication` e `Admin inicial validacao@escala.local criado`. Qualquer erro de SQL (`ORA-...`) ou de validação do Hibernate encerra com código 1.

Confira como `ADMIN`:

```sql
select banner_full from v$version;   -- deve mostrar 19c

select "version", "description", "success"
  from escala_validacao."flyway_schema_history"
 order by "installed_rank";
```

### 5.2 Backup e restauração

1. **Object Storage → Buckets → Create** `escala-backup` (Standard, privado). Em **Lifecycle Policy**, apague objetos com mais de 30 dias, para ficar dentro dos 20 GB gratuitos.
2. **Identity → seu usuário → Auth tokens → Generate token**, e anote o *namespace* do Object Storage (Tenancy details).
3. Como `ADMIN`, rode [`infra/backup/export-diario.sql`](../infra/backup/export-diario.sql), trocando os `<...>`. Ele cria a credencial `ESCALA_BACKUP`, a procedure `escala_backup_diario` e o job `ESCALA_BACKUP_DIARIO_JOB` (3h, horário de Brasília). Ative o *DBMS_OUTPUT* no Database Actions.
4. Exporte o schema de validação, que já tem tabelas e o admin do passo 5.1:

   ```sql
   exec escala_backup_diario('ESCALA_VALIDACAO')
   -- saída: Backup enviado: escala_validacao_AAAAMMDD_HHMMSS.dmp

   select object_name, bytes
     from dbms_cloud.list_objects(
              'ESCALA_BACKUP',
              'https://objectstorage.sa-saopaulo-1.oraclecloud.com/n/<namespace>/b/escala-backup/o/');
   ```

5. Restaure esse dump em outro schema com [`infra/backup/restaurar.sql`](../infra/backup/restaurar.sql):
   - `<arquivo>`: o nome impresso, sem `.dmp`
   - `<schema-origem>`: `ESCALA_VALIDACAO`
   - `<schema-destino>`: `ESCALA_RESTAURADO`

   O esperado é `Import: COMPLETED` e **1** usuário restaurado.

6. Teste o job como ele vai rodar de verdade, em sessão de background, e confira o resultado:

   ```sql
   exec dbms_scheduler.run_job('ESCALA_BACKUP_DIARIO_JOB', use_current_session => false)

   select log_date, status, error#, additional_info
     from user_scheduler_job_run_details
    where job_name = 'ESCALA_BACKUP_DIARIO_JOB'
    order by log_date desc fetch first 5 rows only;
   ```

   Nesse momento o `ESCALA_APP` ainda está vazio, então o dump sai pequeno. Mesmo assim, o status deve ser `SUCCEEDED`.

### 5.3 Limpeza

```sql
drop user escala_restaurado cascade;
drop user escala_validacao cascade;
```

Recrie o `escala_validacao` (passo 1) sempre que precisar validar de novo. **Repita o 5.1 antes de todo deploy que trouxer migrações novas.** Se a migração transforma dados, é ainda melhor restaurar o backup mais recente de `ESCALA_APP` em `ESCALA_VALIDACAO` (5.2, passo 5) e rodar o 5.1 em cima dele, para testar contra dados reais.

## 6. Primeira subida

```bash
cd /opt/escala
cp .env.example .env && chmod 600 .env
nano .env                                     # preencha tudo (veja abaixo)
```

No `.env`:

- **`DB_URL`:** use o alias `escala_tp` do `wallet/tnsnames.ora`, por exemplo `jdbc:oracle:thin:@escala_tp?TNS_ADMIN=/wallet`.
- **`DB_USUARIO`/`DB_SENHA`:** o usuário `escala_app` do passo 1.
- **`DOMINIO`:** o domínio público. O Caddy emite o certificado com ele, e o app monta os links que saem dele (`https://DOMINIO/...`, no lembrete de disponibilidade). Sem ele, o app não sobe.
- **`ESCALA_CHAVE_LEMBRAR_ME`:** gere com `openssl rand -base64 32`.
- **`ESCALA_ADMIN_*`:** o admin criado na primeira subida. Sem eles, e com o banco vazio, a aplicação não sobe. Depois de entrar, você pode apagar a senha do `.env`.

Suba:

```bash
docker compose pull
docker compose up -d
docker compose ps              # app e caddy devem ficar "healthy"
docker compose logs -f app     # procure "Started EscalaApplication" e "Admin inicial ... criado"
```

## 7. Atualizar

Depois que o CI publicar uma nova imagem (push na `main`), e depois do passo 5.1 se houver migrações novas:

```bash
cd /opt/escala && docker compose pull && docker compose up -d && docker image prune -f
```

As migrações do Flyway rodam sozinhas na subida.

## 8. Manter os recursos gratuitos ativos

- **VM ociosa:** a Oracle recupera VMs A1 com CPU, rede e memória abaixo de 20% por 7 dias. O `compose.yaml` usa `-Xms2g -XX:+AlwaysPreTouch`, que mantém cerca de 2 GB de 6 GB (33%) sempre ocupados.
- **Autonomous DB parado:** ele para após 7 dias sem conexão. O pool do Hikari mantém conexões abertas e o healthcheck consulta `/actuator/health`, que consulta o banco, a cada 30 s.

## 9. Backup diário

O Autonomous DB Always Free **não tem backup manual nem restore**, então o backup é o export diário do schema `ESCALA_APP`, configurado no passo 5.2. Para acompanhar:

```sql
select log_date, status, error#, additional_info
  from user_scheduler_job_run_details
 where job_name = 'ESCALA_BACKUP_DIARIO_JOB'
 order by log_date desc fetch first 10 rows only;
```

Para **restaurar**, use o [`infra/backup/restaurar.sql`](../infra/backup/restaurar.sql) com `<schema-origem>` `ESCALA_APP` e um schema de destino novo. Confira os dados e só depois aponte o `DB_USUARIO` para ele. Nunca importe por cima do schema em uso.
