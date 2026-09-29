# Deploy em produção (Oracle Cloud Always Free)

Guia para colocar a Escala no ar. Tudo aqui cabe na cota Always Free; não aumente OCPU nem memória além do indicado.

Visão geral: a VM ARM64 roda `docker compose` com **Caddy** (HTTPS) e o **app**. O banco é um **Autonomous Database 23ai** acessado por TLS com a wallet. O CI publica a imagem `ghcr.io/joao-gabriel-meneses/escala:latest` a cada push na `main`.

> Recursos Always Free (A1 e Autonomous DB) só existem na **região home** da conta. Crie a conta com a home em **Brazil East (São Paulo)**.

## 1. Autonomous Database

1. **Oracle Database → Autonomous Database → Create**:
   - Workload: **Transaction Processing**
   - Marque **Always Free**
   - Versão: **23ai** (ou a mais nova oferecida; os testes usam o Oracle Free 23ai)
   - Nome: `escala`
   - Senha do `ADMIN`: guarde num gerenciador de senhas.
   - Acesso: *Secure access from allowed IPs and VCNs only*, liberando o IP público da VM (passo 2), com **mTLS obrigatório**.
2. **Database connection → Download wallet** (Instance wallet). A senha pedida é só do zip.
3. Em **Database Actions → SQL**, como `ADMIN`, crie o usuário da aplicação. Nunca use o `ADMIN` no app.

   ```sql
   -- Senha do ADB: 12 a 30 caracteres, com maiúscula, minúscula e número, sem aspas.
   create user escala_app identified by "<senha-forte>";
   grant create session, create table, create sequence, create view,
         create procedure, create trigger to escala_app;
   alter user escala_app quota unlimited on data;
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

## 4. Primeira subida

```bash
sudo mkdir -p /opt/escala && sudo chown ubuntu:ubuntu /opt/escala && cd /opt/escala
```

Da sua máquina, copie os arquivos de produção:

```bash
scp compose.yaml Caddyfile .env.example ubuntu@<ip-da-vm>:/opt/escala/
scp Wallet_escala.zip ubuntu@<ip-da-vm>:/opt/escala/
```

Na VM:

```bash
cd /opt/escala
unzip Wallet_escala.zip -d wallet && rm Wallet_escala.zip
chmod 700 wallet && chmod 600 wallet/*        # o container roda como uid 1000 (ubuntu)
cp .env.example .env && chmod 600 .env
nano .env                                     # preencha tudo (veja abaixo)
```

No `.env`:

- **`DB_URL`:** use o alias `escala_tp` do `wallet/tnsnames.ora`, por exemplo `jdbc:oracle:thin:@escala_tp?TNS_ADMIN=/wallet`.
- **`DB_USUARIO`/`DB_SENHA`:** o usuário `escala_app` do passo 1.
- **`ESCALA_CHAVE_LEMBRAR_ME`:** gere com `openssl rand -base64 32`.
- **`ESCALA_ADMIN_*`:** o admin criado na primeira subida. Depois de entrar, você pode apagar a senha do `.env`.

O repositório é privado, então a imagem no GHCR também é. Crie um *personal access token (classic)* só com `read:packages` e faça login uma vez:

```bash
echo <token> | docker login ghcr.io -u <seu-usuario-github> --password-stdin
```

Suba:

```bash
docker compose pull
docker compose up -d
docker compose ps              # app e caddy devem ficar "healthy"
docker compose logs -f app     # procure "Started EscalaApplication" e "Admin inicial ... criado"
```

## 5. Atualizar

Depois que o CI publicar uma nova imagem (push na `main`):

```bash
cd /opt/escala && docker compose pull && docker compose up -d && docker image prune -f
```

As migrações do Flyway rodam sozinhas na subida.

## 6. Manter os recursos gratuitos ativos

- **VM ociosa:** a Oracle recupera VMs A1 com CPU, rede e memória abaixo de 20% por 7 dias. O `compose.yaml` usa `-Xms2g -XX:+AlwaysPreTouch`, que mantém cerca de 2 GB de 6 GB (33%) sempre ocupados.
- **Autonomous DB parado:** ele para após 7 dias sem conexão. O pool do Hikari mantém conexões abertas e o healthcheck consulta `/actuator/health`, que consulta o banco, a cada 30 s.

## 7. Backup diário (Data Pump → Object Storage)

O Autonomous DB Always Free **não tem backup manual nem restore**, então o backup é um export diário do schema `ESCALA_APP`.

1. **Object Storage → Buckets → Create** `escala-backup` (Standard, privado). Em **Lifecycle Policy**, apague objetos com mais de 30 dias, para ficar dentro dos 20 GB gratuitos.
2. **Identity → seu usuário → Auth tokens → Generate token**.
3. Anote o *namespace* do Object Storage (Tenancy details).
4. Como `ADMIN`, rode [`infra/backup/export-diario.sql`](../infra/backup/export-diario.sql), trocando os `<...>`. O script cria a credencial, a procedure e um job diário às 3h (horário de Brasília).
5. Teste na hora com `exec escala_backup_diario;` e confira o `.dmp` no bucket.

Para acompanhar as execuções:

```sql
select log_date, status, error#, additional_info
  from user_scheduler_job_run_details
 where job_name = 'ESCALA_BACKUP_DIARIO'
 order by log_date desc fetch first 10 rows only;
```

Para **restaurar**, baixe o dump para o `DATA_PUMP_DIR` com `DBMS_CLOUD.GET_OBJECT` e importe com `DBMS_DATAPUMP` (operação `IMPORT`), ou use o `impdp` de um cliente com a wallet. Faça isso em um schema novo e só depois troque o `DB_USUARIO`.
