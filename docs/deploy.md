# Deploy em produção (Oracle Cloud Always Free + Cloudflare Tunnel)

Roteiro para colocar a Escala no ar, **na ordem**, feito à mão. Nada aqui é automático: o CI só publica a imagem.

```
Usuário ──https──▶ Cloudflare (DNS, WAF, TLS) ──túnel de saída──▶ cloudflared ──http──▶ app:8080 ──TLS + wallet──▶ Autonomous DB 19c
                   escala.ibrp.com.br                             (VM A1 ARM64, docker compose, nenhuma porta aberta)
```

- **Sem porta aberta:** o `cloudflared` abre uma conexão de saída para o Cloudflare. Nada entra na VM pela internet, então as portas 80 e 443 ficam fechadas. A única porta aberta é a 22, e só para o seu IP.
- **Sem Caddy nem certificado na VM:** o TLS termina no Cloudflare. O app recebe `X-Forwarded-Proto: https` e, com `server.forward-headers-strategy=native`, gera redirects e cookies `Secure` como https.
- **Imagem:** `ghcr.io/joao-gabriel-meneses/escala:<sha>`, publicada pelo CI a cada push na `main` (amd64 + arm64). O deploy usa sempre a tag com o SHA completo do commit, nunca `latest`.
- **Cota Always Free:** A1 com 2 OCPUs e 12 GB é a cota inteira. Não crie outra VM A1 nem aumente nada.

> Recursos Always Free (A1 e Autonomous DB) só existem na **região home** da conta. A conta precisa ter a home em **Brazil East (São Paulo)**.

## 1. VM

1. **Compute → Instances → Create**:
   - Shape: `VM.Standard.A1.Flex` com **2 OCPUs e 12 GB**
   - Imagem: **Ubuntu LTS (aarch64)**, a mais nova que a Oracle oferecer (24.04 ou 26.04)
   - Chave SSH: a sua chave pública (`~/.ssh/id_ed25519.pub`). Não crie senha.
   - Rede: VCN padrão, subnet pública.
2. **IP público reservado:** em *Networking → IP management → Reserved public IPs*, reserve um IP. Na VNIC da instância, troque o IP efêmero pelo reservado. Esse IP vai para a lista de acesso do banco (passo 2), então não pode mudar.
3. **Security List** da subnet (*Networking → VCN → Security Lists → Default*), regras de ingress:
   - **Apague** a regra `0.0.0.0/0` TCP 22, que vem por padrão.
   - **Adicione** TCP 22 a partir de `<seu-ip>/32`.
   - **Não** crie regra para 80 nem 443: com o túnel, nada entra por elas.
   - Se o seu IP mudar, você perde o SSH. Entre pelo Console (Cloud Shell) e atualize a regra.
4. **SSH só por chave.** Na VM:

   ```bash
   sudo tee /etc/ssh/sshd_config.d/00-escala.conf >/dev/null <<'EOF'
   PasswordAuthentication no
   KbdInteractiveAuthentication no
   PermitRootLogin no
   EOF
   sudo systemctl reload ssh
   sudo sshd -T | grep -E '^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin) '
   # esperado: passwordauthentication no / kbdinteractiveauthentication no / permitrootlogin no
   ```

   Antes de fechar a sessão, abra outra para conferir que ainda entra.
5. **Firewall da VM:** as imagens Ubuntu da Oracle já vêm com o iptables liberando só a 22. Confira que 80 e 443 **não** aparecem:

   ```bash
   sudo iptables -S INPUT
   # esperado: ACCEPT para --dport 22 e, no fim, um REJECT geral. Nenhuma linha com 80 ou 443.
   ```

   Se houver regra para 80 ou 443 (de um deploy antigo), apague com `sudo iptables -D INPUT <regra>` e salve com `sudo netfilter-persistent save`.
6. **Docker:**

   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker ubuntu   # saia e entre de novo no SSH
   ```

   Porta publicada pelo Docker (`ports:`) passa **por cima** do iptables. Por isso o `compose.prod.yaml` não publica nenhuma, e nenhuma deve ser publicada.

## 2. Autonomous Database 19c

1. **Oracle Database → Autonomous Database → Create**:
   - Workload: **Transaction Processing**
   - Marque **Always Free**
   - Versão: **19c**. Em São Paulo, o Always Free só oferece 19c. Os testes rodam no Oracle Free 23ai com o Hibernate fixado em 19 e um verificador de sintaxe, mas a garantia final é o passo 5.
   - Nome: `escala`
   - Senha do `ADMIN`: guarde num gerenciador de senhas.
   - Acesso: *Secure access from allowed IPs and VCNs only*, com o **IP reservado da VM** (passo 1.2) e **mTLS obrigatório**.
2. **Database connection → Download wallet** (Instance wallet). A senha pedida é só do zip.
3. Em **Database Actions → SQL**, como `ADMIN`, crie o usuário da aplicação e o de validação. Nunca use o `ADMIN` no app.

   O `escala_app` também é dono do backup (passo 7). Os privilégios do backup são **concedidos direto**, porque privilégio recebido por role não vale dentro de procedure com os direitos do dono nem no job do Scheduler.

   ```sql
   -- Senha do ADB: 12 a 30 caracteres, com maiúscula, minúscula e número, sem aspas.
   create user escala_app identified by "<senha-forte>";
   grant create session, create table, create sequence, create view,
         create procedure, create trigger to escala_app;
   alter user escala_app quota unlimited on data;

   -- Backup (passo 7), tudo direto, sem role.
   grant create job to escala_app;
   grant read, write on directory data_pump_dir to escala_app;
   grant execute on dbms_cloud to escala_app;

   -- Schema descartável para validar as migrações antes do deploy (passo 5).
   create user escala_validacao identified by "<outra-senha-forte>";
   grant create session, create table, create sequence, create view,
         create procedure, create trigger to escala_validacao;
   alter user escala_validacao quota unlimited on data;
   ```

   Confira que os privilégios do backup estão diretos no usuário, e não por role:

   ```sql
   select privilege from dba_sys_privs where grantee = 'ESCALA_APP' order by privilege;
   select owner, table_name, privilege from dba_tab_privs where grantee = 'ESCALA_APP' order by table_name;
   -- esperado: CREATE JOB entre os de sistema; READ e WRITE em DATA_PUMP_DIR e EXECUTE em DBMS_CLOUD.
   ```

> **O Autonomous DB grátis para sozinho com 7 dias sem conexão e é apagado com 90 dias parado.** O pool do Hikari e o health check (que consulta o banco) mantêm a conexão. Se o app ficar fora do ar por dias, ligue o banco pelo Console antes dos 90.

## 3. Cloudflare

### 3.1 Zona e nameservers

1. No Cloudflare, **Add a domain** → `ibrp.com.br`, plano **Free**.
2. O Cloudflare importa os registros DNS atuais. **Antes de trocar os nameservers**, compare com o DNS de hoje (Registro.br ou o provedor atual) e confira que vieram todos, principalmente:
   - **MX** e os TXT de e-mail (SPF, DKIM, DMARC), senão o e-mail da igreja para;
   - o site atual (`ibrp.com.br`, `www`).

   Registros de e-mail ficam **DNS only** (nuvem cinza).
3. **DNSSEC:** se estiver ativo no Registro.br, **desative antes** de trocar os nameservers, senão o domínio para de resolver. Depois que a zona estiver ativa, reative pelo Cloudflare (*DNS → Settings → DNSSEC*) e cadastre o DS no Registro.br.
4. No **Registro.br** (*Domínios → ibrp.com.br → DNS → Alterar servidores DNS*), coloque os dois nameservers que o Cloudflare mostrar.
5. Espere a zona ficar **Active** no Cloudflare (minutos a algumas horas).

### 3.2 Túnel

1. **Zero Trust → Networks → Tunnels → Create a tunnel → Cloudflared**, nome `escala-vm`. O Zero Trust pede para escolher um plano: o **Free** basta.
2. Em *Install and run connector*, escolha **Docker**. O comando mostrado termina em `--token <TOKEN>`. **Guarde só o token**, num gerenciador de senhas: ele vai no `.env` como `CLOUDFLARE_TUNNEL_TOKEN`. Não rode esse comando: quem roda o conector é o compose.
3. **Public Hostname → Add**:
   - Subdomain `escala`, Domain `ibrp.com.br`
   - Service: **HTTP**, URL **`app:8080`** (o nome do serviço no compose)

   O Cloudflare cria sozinho o CNAME `escala` → `<id>.cfargotunnel.com`, com proxy.

### 3.3 HTTPS e cache

- **Só https:** em *Rules → Redirect Rules*, use o modelo *Redirect from HTTP to HTTPS*, filtrado por hostname igual a `escala.ibrp.com.br`. Filtrar pelo hostname evita mexer no site da igreja; o "Always Use HTTPS" vale para a zona inteira.
- **TLS mínimo 1.2:** *SSL/TLS → Edge Certificates → Minimum TLS Version*. Vale para a zona inteira, e navegadores atuais já usam 1.2 ou mais.
- **Cache:** não crie regra "Cache Everything". O Cloudflare já guarda CSS e JS (que saem com hash no nome) e não guarda HTML. HTML em cache mistura sessões e serve página velha.
- **Bot Fight Mode:** deixe **desligado**. No plano Free ele não aceita exceção e bloqueia o monitoramento (passo 8). A WAF gerenciada do plano Free continua ativa.

## 4. Preparar a VM

```bash
sudo mkdir -p /opt/escala && sudo chown ubuntu:ubuntu /opt/escala
```

Da sua máquina, copie o compose, o exemplo do `.env` e a wallet:

```bash
scp compose.prod.yaml .env.example ubuntu@<ip-da-vm>:/opt/escala/
scp Wallet_escala.zip ubuntu@<ip-da-vm>:/opt/escala/
```

Na VM:

```bash
cd /opt/escala
unzip Wallet_escala.zip -d wallet && rm Wallet_escala.zip
ls wallet/tnsnames.ora                         # tem que estar direto em wallet/, sem subpasta
chmod 700 wallet && chmod 600 wallet/*        # o container roda como uid 1000 (ubuntu)
```

O repositório é privado, então a imagem no GHCR também é. Crie um *personal access token (classic)* só com `read:packages` e faça login uma vez:

```bash
echo <token> | docker login ghcr.io -u <seu-usuario-github> --password-stdin
```

Escolha a imagem: o SHA completo do último commit da `main` com o CI verde (`git rev-parse origin/main`). O job **Imagem Docker** do CI publica `ghcr.io/joao-gabriel-meneses/escala:<sha>` e confere que a variante arm64 roda.

## 5. Validar as migrações no Autonomous DB 19c (antes de apontar para o schema real)

Os testes do CI rodam no Oracle Free 23ai, que aceita sintaxe que o 19c recusa. Três coisas pegam o que conseguem:

- o dialeto fixado em 19;
- o verificador de sintaxe (`SqlCompativelComOracle19Test`);
- a checagem do schema (`SchemaCompativelComOracle19IT`).

Mas só o banco real prova que funciona: o parser do 19c, os privilégios, a wallet e o TLS. O CI não enxerga o ADB (o acesso é restrito ao IP da VM), então a validação roda na VM, no schema descartável `ESCALA_VALIDACAO`.

**Checklist do 19c**, para toda migração nova (detalhes no CLAUDE.md, "Oracle 19c em produção"):

- [ ] Sem `BOOLEAN`: `NUMBER(1)` com `CHECK (col IN (0, 1))`.
- [ ] Sem tipo `JSON`: `CLOB` com `CHECK (col IS JSON)`.
- [ ] Sem `CREATE ... IF NOT EXISTS` nem `DROP ... IF EXISTS`.
- [ ] Sem `SELECT` sem `FROM dual`.
- [ ] Sem `INSERT` com várias linhas em `VALUES (...), (...)`: um `INSERT` por linha ou `INSERT ALL`.

Rode a imagem no **modo validação**, sem servidor web. Ela aplica todas as migrações no `ESCALA_VALIDACAO`, roda o `ddl-auto=validate` do Hibernate, cria o admin (um `INSERT` real) e sai.

```bash
cd /opt/escala
ESCALA_TAG=<sha>
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
  "ghcr.io/joao-gabriel-meneses/escala:$ESCALA_TAG" \
  --spring.main.web-application-type=none
echo "código de saída: $?"
unset DB_SENHA
```

O esperado é código de saída **0**, e no log (em JSON):

- `Successfully applied N migrations to schema "ESCALA_VALIDACAO"`;
- `Started EscalaApplication`;
- `Admin inicial validacao@escala.local criado`.

Qualquer erro de SQL (`ORA-...`) ou de validação do Hibernate encerra com código 1.

Confira como `ADMIN`:

```sql
select banner_full from v$version;   -- deve mostrar 19c

select "version", "description", "success"
  from escala_validacao."flyway_schema_history"
 order by "installed_rank";
```

Depois, apague e recrie o `escala_validacao` (passo 2.3) para a próxima validação:

```sql
drop user escala_validacao cascade;
```

**Repita este passo antes de todo deploy que trouxer migração nova.**

## 6. Primeira subida

```bash
cd /opt/escala
cp .env.example .env && chmod 600 .env
nano .env                                     # preencha tudo (veja abaixo)
```

No `.env`, que fica só na VM e nunca no repositório:

- **`ESCALA_TAG`:** o SHA completo validado no passo 5.
- **`CLOUDFLARE_TUNNEL_TOKEN`:** o token do passo 3.2.
- **`DOMINIO`:** `escala.ibrp.com.br`. O app monta com ele os links que saem dele (`https://DOMINIO/...`). Sem ele, o app não sobe.
- **`DB_URL`:** o alias `escala_tp` do `wallet/tnsnames.ora`, por exemplo `jdbc:oracle:thin:@escala_tp?TNS_ADMIN=/wallet`.
- **`DB_USUARIO`/`DB_SENHA`:** o `escala_app` do passo 2.3.
- **`ESCALA_CHAVE_LEMBRAR_ME`:** gere com `openssl rand -base64 32`.
- **`ESCALA_ADMIN_*`:** o admin criado na primeira subida. Sem eles, e com o banco vazio, a aplicação não sobe. Depois de entrar, apague a senha do `.env`.
- **`COMPOSE_FILE=compose.prod.yaml`:** já vem preenchido. Faz o `docker compose` usar o arquivo de produção sem `-f`.

Suba:

```bash
docker compose pull
docker compose up -d
docker compose ps                    # app "healthy"; o cloudflared só sobe depois disso
docker compose logs -f app           # "Started EscalaApplication" e "Admin inicial ... criado"
docker compose logs cloudflared      # "Registered tunnel connection" (4 conexões)
```

Os logs do app são JSON (um por linha). Para ler:

```bash
docker compose logs --no-log-prefix app | jq -r '[."@timestamp", (."log.level" // .log.level), .message] | join(" ")'
```

De fora da VM:

```bash
curl -s https://escala.ibrp.com.br/actuator/health      # {"status":"UP"}
curl -sI http://escala.ibrp.com.br/login | head -3      # 301 para https
```

Entre com o admin e confira, no navegador, que o cookie `JSESSIONID` está com `Secure`, `HttpOnly` e `SameSite=Lax`.

## 7. Backup

O Autonomous DB Always Free **não tem backup manual nem restore**. O backup é um export diário do schema `ESCALA_APP` com Data Pump, enviado ao Object Storage.

> **O backup só conta depois de um restore testado.** Um dump que nunca foi importado não prova nada. Faça o 7.4 agora e repita de tempos em tempos (por exemplo, a cada trimestre e depois de migrações grandes).

### 7.1 Bucket e usuário OCI do backup

1. **Object Storage → Buckets → Create** `escala-backup` (Standard, privado).
2. **Lifecycle Policy** do bucket: apagar objetos com mais de 30 dias, para ficar dentro dos 20 GB gratuitos. A lifecycle precisa desta policy no tenancy (*Identity → Policies*):

   ```text
   Allow service objectstorage-sa-saopaulo-1 to manage object-family in tenancy
   ```

3. Um **usuário OCI só para o backup**, porque o token dele fica no banco:
   - *Identity → Domains → Default → Users*: crie `escala-backup`, sem acesso ao Console;
   - crie o grupo `EscalaBackup` e coloque o usuário nele;
   - crie a policy abaixo;
   - em *Auth tokens*, gere um token e guarde. O *namespace* do Object Storage está em *Tenancy details*.

   ```text
   Allow group 'Default'/'EscalaBackup' to manage objects in tenancy where target.bucket.name = 'escala-backup'
   ```

### 7.2 Criar o backup no ESCALA_APP

Tudo pertence ao `ESCALA_APP`: a credencial, a procedure (`AUTHID DEFINER`) e o job. Os privilégios são os diretos do passo 2.3.

1. Como `ADMIN`, libere o Database Actions para o `escala_app`, só durante a configuração:

   ```sql
   begin
       ords_admin.enable_schema(
           p_enabled             => true,
           p_schema              => 'ESCALA_APP',
           p_url_mapping_type    => 'BASE_PATH',
           p_url_mapping_pattern => 'escala_app',
           p_auto_rest_auth      => true);
   end;
   /
   ```

2. Entre no Database Actions **como `ESCALA_APP`** e rode [`infra/backup/export-diario.sql`](../infra/backup/export-diario.sql) em modo script (F5), trocando os `<...>`. Ele cria a credencial `ESCALA_BACKUP`, a procedure `escala_backup_diario` e o job `ESCALA_BACKUP_DIARIO_JOB` (3h, horário de Brasília).

### 7.3 Testar o job de verdade

Ainda como `ESCALA_APP`, rode o job pelo Scheduler, numa sessão de background, como ele vai rodar às 3h:

```sql
begin
    dbms_scheduler.run_job('ESCALA_BACKUP_DIARIO_JOB', use_current_session => false);
end;
/

-- Espere um ou dois minutos.
select log_date, status, error#, additional_info
  from user_scheduler_job_run_details
 where job_name = 'ESCALA_BACKUP_DIARIO_JOB'
 order by log_date desc fetch first 5 rows only;
-- esperado: SUCCEEDED, error# 0
```

Confira o dump e o log no bucket:

```sql
select object_name, bytes, last_modified
  from dbms_cloud.list_objects(
           'ESCALA_BACKUP',
           'https://objectstorage.sa-saopaulo-1.oraclecloud.com/n/<namespace>/b/escala-backup/o/')
 order by last_modified desc;
-- esperado: escala_app_AAAAMMDD_HHMMSS.dmp e .log de agora, com tamanho maior que zero
```

Baixe o `.log` pelo Console do Object Storage e confira que termina em `successfully completed`.

Por fim, desligue o Database Actions do `escala_app` (como `ADMIN`):

```sql
begin
    ords_admin.enable_schema(p_enabled => false, p_schema => 'ESCALA_APP');
end;
/
```

### 7.4 Testar o restore

Como `ADMIN`, rode [`infra/backup/restaurar.sql`](../infra/backup/restaurar.sql) com o dump do 7.3 (`<arquivo>` é o nome sem `.dmp`). O script:

1. cria uma credencial do `ADMIN` para ler o bucket;
2. cria o schema **vazio** `ESCALA_RESTAURADO`;
3. baixa o dump;
4. importa com `REMAP_SCHEMA`, sem o job;
5. compara as linhas tabela a tabela com o `ESCALA_APP` e mostra a última migração dos dois.

O esperado:

- `Import: COMPLETED`;
- `Restore conferido: tudo igual.`;
- a mesma versão do Flyway nos dois schemas.

Depois de conferir:

```sql
drop user escala_restaurado cascade;
```

### 7.5 Acompanhar e restaurar de verdade

Para ver as execuções, entre como `ESCALA_APP` (ou como `ADMIN`, em `dba_scheduler_job_run_details` com `owner = 'ESCALA_APP'`):

```sql
select log_date, status, error#, additional_info
  from dba_scheduler_job_run_details
 where owner = 'ESCALA_APP' and job_name = 'ESCALA_BACKUP_DIARIO_JOB'
 order by log_date desc fetch first 10 rows only;
```

Para **restaurar** de verdade:

1. rode o `restaurar.sql` com o dump escolhido, num schema novo;
2. confira os dados;
3. aponte o `DB_USUARIO` (e a senha) para o schema novo e rode `docker compose up -d`;
4. recrie nele o backup (passos 2.3 e 7.2). O import deixou o job de fora de propósito.

Nunca importe por cima do schema em uso.

## 8. Monitoramento externo

Use um monitor gratuito de uptime, por exemplo o UptimeRobot (o plano Free verifica a cada 5 minutos):

- Tipo **Keyword**, URL `https://escala.ibrp.com.br/actuator/health`, palavra `"UP"`, intervalo **5 minutos**.
- Alerta por e-mail para você.

O health consulta o banco: o monitor avisa quando o app cai, o túnel cai ou o Autonomous DB para. Ele também mantém o banco com conexão.

## 9. Atualizar

Depois que o CI publicar a imagem do novo commit da `main` (e depois do passo 5, se houver migração nova):

```bash
cd /opt/escala
nano .env                       # ESCALA_TAG=<sha novo>; anote o anterior para o rollback
docker compose pull && docker compose up -d && docker image prune -f
docker compose ps               # app volta a "healthy"
```

As migrações do Flyway rodam sozinhas na subida.

## 10. Rollback

Volte o `ESCALA_TAG` para o SHA anterior e suba de novo:

```bash
cd /opt/escala
nano .env                       # ESCALA_TAG=<sha anterior>
docker compose up -d
```

A imagem anterior ainda está na VM ou no GHCR, então não precisa de build.

**Cuidado com migrações:** o Flyway não desfaz migração.

- Se a versão nova só **acrescentou** coisas (tabela, coluna anulável), a anterior sobe normalmente. O Flyway ignora a migração "do futuro".
- Se ela **removeu ou renomeou** coluna ou tabela, a anterior falha no `ddl-auto=validate`. Aí as saídas são uma migração nova que devolva a compatibilidade, ou o restore do backup de antes do deploy (passo 7.5).

## 11. Manter os recursos gratuitos ativos

- **VM ociosa:** a Oracle recupera VMs A1 em que CPU, rede **e** memória ficam abaixo de 20% por 7 dias.
  - O `compose.prod.yaml` usa `-Xms2g -Xmx3g -XX:+AlwaysPreTouch`, que deixa uns 2 GB sempre ocupados.
  - Com 12 GB, isso fica **perto do limite**: JVM, SO e cloudflared somam uns 22 a 25%.
  - Confira em *Instance → Metrics → Memory Utilization* (precisa do plugin *Compute Instance Monitoring* do Oracle Cloud Agent, ligado por padrão).
  - Se a média ficar perto de 20%, suba o `-Xms` (e o `-Xmx`) no compose.
- **Autonomous DB parado:** para com 7 dias sem conexão e é **apagado com 90 dias parado**. O pool do Hikari, o health check do compose (a cada 30 s) e o monitoramento (a cada 5 min) mantêm a conexão.

## 12. Problemas comuns

**Redirect para `http://` (ou loop de redirect) depois do login**
- O app não sabe que a requisição original era https.
- Confira que o perfil é o `prod` (`server.forward-headers-strategy=native`) e que o Public Hostname do túnel aponta para `http://app:8080`, e não para um IP.
- O Tomcat só confia em `X-Forwarded-*` vindos de redes privadas (10/8, 172.16/12, 192.168/16), onde o Docker cria a rede. Uma rede com `subnet` customizada fora dessas faixas quebra isso.

**Cookie de sessão perdido (login volta para a tela de login)**
- O cookie é `Secure`, então só vai em https. Acesso por `http://` direto (sem o redirect do passo 3.3) ou pelo IP da VM não guarda sessão.
- Confira que não existe regra de cache para HTML ("Cache Everything"): o Cloudflare guardaria a resposta com `Set-Cookie` de outra pessoa.
- `SameSite=Lax` não manda o cookie em POST vindo de outro site, o que é o esperado.

**CSS ou JS velho depois de um deploy**
- O HTML precisa apontar para `/css/app-<hash>.css`, e não para `/css/app.css`. Confira com `curl -s https://escala.ibrp.com.br/login | grep app-`.
- Se estiver sem hash, o perfil ou a imagem estão errados.
- Se o próprio HTML veio velho, há uma regra de cache para HTML; apague a regra e faça *Caching → Purge Everything*.

**Wallet não encontrada (`ORA-17957`, `IO Error`, `TNS_ADMIN`, `cwallet.sso`)**
- A wallet tem que estar descompactada em `/opt/escala/wallet`, com `tnsnames.ora` direto nela, sem subpasta. O `DB_URL` termina em `?TNS_ADMIN=/wallet`.
- Permissão: o container roda como uid 1000. `ls -ln wallet` deve mostrar o dono 1000 e os arquivos legíveis por ele.
- O alias no `DB_URL` (`escala_tp`) tem que existir no `tnsnames.ora`.
- Se a conexão for recusada (`ORA-12506`), o IP reservado da VM não está na lista de acesso do banco (passo 2.1).

**cloudflared não sobe**
- Ele espera o app ficar `healthy`. Veja `docker compose logs app`.
- Se o app está saudável, confira o `CLOUDFLARE_TUNNEL_TOKEN` e o status do túnel no painel (Zero Trust → Tunnels).
