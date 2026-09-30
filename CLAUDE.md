# CLAUDE.md — Sistema de Escala Ministerial

Guia para o Claude trabalhar neste repositório. Leia antes de qualquer tarefa.

## O que é o projeto

Sistema web para **gerar automaticamente as escalas** dos ministérios de uma igreja. Começa pelo ministério de **mídia** e precisa aceitar outros ministérios (ex.: louvor) **sem alterar código**, apenas por configuração.

Hoje o líder pergunta a cada membro, pelo WhatsApp, quais domingos e quintas ele pode servir e monta a escala à mão. O sistema substitui isso:

1. Cada membro marca sua disponibilidade.
2. O sistema gera a escala respeitando as regras do ministério.
3. O líder revisa, ajusta e publica.

**Contexto da mídia (MVP):** cerca de 20 membros, 2 funções (Projeção e Transmissão), 1 pessoa por função por evento, cultos aos domingos e quintas, mais eventos avulsos.

Objetivos do autor: servir o ministério, compor portfólio e aprender Java/Spring.

## Stack (decidida)

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1 (Web, Security, Data JPA, Validation, Mail) |
| Solver | Timefold Solver Community (`timefold-solver-spring-boot-starter`, só suporta Spring Boot 4.x) |
| Front-end | Thymeleaf + htmx + Tailwind CSS, renderizado no servidor, mobile-first |
| Banco | Oracle Autonomous Database Always Free **19c** (produção). Em São Paulo o Always Free só oferece 19c |
| Migrações | Flyway |
| Testes | JUnit 5, AssertJ, Mockito, `ConstraintVerifier` (Timefold), Testcontainers com `gvenzl/oracle-free:23-slim-faststart` (23ai, com o Hibernate fixado em 19), JaCoCo |
| Qualidade | pre-commit (gitleaks, arquivos grandes, segredos), Spotless com palantir-java-format |
| E-mail | Spring Mail via SMTP do Gmail (`smtp.gmail.com:587`, senha de app) |
| Build | Maven (`./mvnw`) |
| Deploy | Docker Compose (Caddy + app) em VM Oracle Cloud Always Free Ampere A1 (**ARM64**), região São Paulo |
| CI | GitHub Actions, imagens multiarquitetura com `docker buildx` |

Não troque nenhuma dessas escolhas sem perguntar.

## Arquitetura

Monólito modular. Cada pacote de primeiro nível é um módulo de domínio com suas próprias camadas (`web`, `service`, `repository`, `domain`):

```
br.igreja.escala       # pacote provisório (ver "Em aberto")
├── identidade        # usuários, login, perfis, convites, recuperação de senha
├── ministerio        # ministérios, funções, níveis, membresias, habilitações
├── evento            # modelos de evento recorrente, eventos, períodos
├── disponibilidade   # marcação do membro, trava do período
├── escala            # vagas/alocações, domínio Timefold, catálogo de regras, publicação, desistências
├── notificacao       # e-mails (publicação, lembrete 24 h, alertas ao líder)
└── compartilhado     # auditoria, config, utilitários
```

- Módulos se comunicam por serviços públicos ou eventos de aplicação do Spring, nunca acessando repositórios de outro módulo.
- Controllers devolvem views Thymeleaf; fragmentos htmx ficam em `templates/<modulo>/fragments/`. Componentes visuais ficam em `templates/componentes/` e layouts em `templates/layouts/` (ver "Visual").
- Toda rota verifica autorização **por perfil e por ministério**.

## Perfis

- **Membro:** marca disponibilidade, vê escalas, desiste de uma escala.
- **Gerente (líder):** por ministério. Configura funções, níveis, regras e eventos; gera, ajusta, força exceções, trava e publica.
- **Admin:** tudo, em qualquer ministério; cria ministérios e nomeia gerentes.

Gerente e admin também servem e aparecem na escala. Um usuário pode estar em vários ministérios e em várias funções em cada um.

## Modelo de dados (resumo)

- `Usuario`
- `Ministerio`
- `Membresia` (usuario × ministerio, `gerente`)
- `Funcao` (`qtd_min`, `qtd_max`)
- `Nivel` (`ordem`)
- `Habilitacao` (usuario × funcao × nivel)
- `Regra` (`tipo`, `parametros` JSON, `rigidez` HARD/SOFT, `peso`, `ativa`)
- `ModeloEvento`
- `Evento`
- `Periodo` (`disponibilidade_travada`, `status_escala` RASCUNHO/PUBLICADA)
- `Disponibilidade` (PODE / NAO_PODE / PREFERE_NAO)
- `Vaga` (evento × funcao × posicao, `usuario_id` anulável, `fixada`, `forcada`, `justificativa`)
- `SolicitacaoTroca`
- `Auditoria`

Tudo que varia por ministério (funções, níveis, regras, eventos) pertence ao ministério. **Oracle 19c não tem tipos JSON nem BOOLEAN nativos:**
- `parametros` é `CLOB` com `CHECK (parametros IS JSON)`. Na entidade, mapeie como `String` com `@Lob` e converta com Jackson na classe de parâmetros. Não use `@JdbcTypeCode(SqlTypes.JSON)`: com o dialeto 19 o Hibernate espera **BLOB**.
- Flags (`gerente`, `fixada`, `ativa`...) são `NUMBER(1)` com `CHECK (col IN (0, 1))`, mapeadas como `boolean` no Java.

Cada tipo de regra tem uma classe Java de parâmetros (ex.: `MaxPorNivelParams`) que valida esse JSON.

## Regras de negócio — o ponto central

As regras são **dados configurados por ministério, não código por ministério**. Nunca escreva `if (ministerio == "midia")`.

Existe um **catálogo fixo de tipos de regra**. Cada tipo é implementado uma única vez, como uma restrição no `ConstraintProvider`, e lê seus parâmetros da tabela `Regra`. Um ministério novo que precise de algo inédito ganha um **tipo novo** (uma classe nova), disponível para todos, sem mexer nos existentes.

| Tipo | Mídia (MVP) |
| --- | --- |
| `PESSOAS_POR_FUNCAO` | Projeção 1–1, Transmissão 1–1 (hard) |
| `HABILITACAO` | Sempre ativa (hard) |
| `DISPONIBILIDADE` | Só escala quem marcou PODE (hard) |
| `UMA_FUNCAO_POR_EVENTO` | Hard |
| `MAX_POR_NIVEL_NO_EVENTO` | Máx. 1 Iniciante por evento, ou seja, nunca dois iniciantes juntos (hard) |
| `LIMITE_POR_PERIODO` | Máx. 3 escalas por mês por pessoa (hard; o gerente pode forçar mais) |
| `EQUILIBRIO_DE_CARGA` | Distribuir o serviço igualmente (soft) |
| `PRIORIDADE_POR_DATA` | Preencher primeiro os eventos mais próximos (medium) |
| `PREFERENCIA` | Evitar PREFERE_NAO (soft, futuro) |
| `VINCULO_ENTRE_PESSOAS`, `INTERVALO_MINIMO` | Não usados na mídia |

Outras regras:

- **Níveis:** a mídia usa só **Iniciante** e **Experiente**. O nível é atribuído pelo gerente **por função**.
- **Disponibilidade:** pode ser alterada a qualquer momento até o gerente **travar** o período.
- **Sem solução válida:** a vaga fica **vazia** e o gerente é alertado com o motivo. O solver nunca viola uma regra hard sem avisar.
- **Forçar uma alocação** que viola regra exige justificativa e gera registro de auditoria.
- **Desistência:** esvazia a vaga **na hora, sem aprovação**. A escala do mês **não é regerada nem reorganizada**; o buraco fica e o gerente recebe um alerta. O membro pode indicar opcionalmente um substituto habilitado, que não pode violar regras hard.
- **Eventos:** modelos recorrentes (domingo e quinta) têm horário padrão editável no modelo ou em um evento específico. Editar um evento não altera o modelo. Eventos avulsos podem ser criados a qualquer momento.
- **Lembrete:** e-mail **24 h** antes do evento.

## Solver (Timefold)

- **Entidade planejada:** `Vaga`, com variável `usuario` anulável (`allowsUnassigned = true`) para permitir vagas vazias.
- Alocações publicadas, fixadas ou forçadas usam `@PlanningPin`.
- **Pontuação:** `HardMediumSoftScore`.
  - Hard: regras rígidas.
  - Medium: vaga vazia, com peso maior quanto mais próximo o evento.
  - Soft: equilíbrio de carga (penalizar o quadrado das escalas por pessoa) e preferências.
- A geração é assíncrona via `SolverManager`, com limite de 10–30 s. O resultado vira rascunho.
- Explicar vagas vazias ao gerente com `SolutionManager.explain`.
- **Toda restrição nova precisa de teste com `ConstraintVerifier`**, cobrindo o caso que penaliza e o que não penaliza.

## Infraestrutura e restrições do ambiente

- **VM:** `VM.Standard.A1.Flex` com 1 OCPU e 6 GB. A cota Always Free total é de 2 OCPUs e 12 GB; nunca proponha ultrapassar.
- **ARM64:** toda imagem Docker deve ter variante `linux/arm64`. Use a base Eclipse Temurin 21.
- **VM ociosa:** a Oracle pode recuperar a VM se CPU, rede e memória ficarem abaixo de 20% por 7 dias. A JVM roda com `-Xms2g -Xmx3g -XX:+AlwaysPreTouch` para manter a memória acima do limite (sem o pre-touch o heap reservado não conta como uso).
- **Autonomous DB Always Free:**
  - 20 GB de armazenamento e **30 sessões**. Mantenha o HikariCP em `maximum-pool-size` ≤ 10.
  - Para após 7 dias sem conexão; o pool ativo e o health check que consulta o banco evitam isso.
  - **Não tem backup manual nem restore.** O backup é um export diário com Data Pump para o Object Storage.
  - Conexão por TLS com a wallet, montada como volume e fora da imagem.
- **Gmail:** exige verificação em duas etapas e senha de app. O limite é de 500 destinatários por dia. Falhas de envio devem ser registradas e notificadas ao admin.
- **Segredos** (senha do banco, wallet, senha de app do Gmail) só via variáveis de ambiente ou arquivos montados. **Nunca commitar.**

## Oracle 19c em produção, 23ai nos testes

**Produção é Oracle 19c.** Testes e dev rodam no Oracle Free 23ai (não existe imagem 19c gratuita), que aceita sintaxe que o 19c recusa. Todo SQL escrito à mão (migrações, `@Query(nativeQuery = true)`, `JdbcTemplate`, scripts em `infra/`, blocos ```sql da documentação) tem que ser **19c**.

**Proibido** (só existe a partir do 21c/23ai) e a alternativa no 19c:

| Proibido | Use |
| --- | --- |
| Coluna `BOOLEAN`, literais `TRUE`/`FALSE` em SQL | `NUMBER(1)` com `CHECK (col IN (0, 1))`, e `1`/`0`. Em PL/SQL, `BOOLEAN` e `TRUE` são válidos |
| Tipo `JSON`, `JSON(...)`, `RETURNING JSON`, `JSON_TRANSFORM`, `JSON_SCALAR` | `CLOB` com `CHECK (col IS JSON)`, `JSON_VALUE`/`JSON_QUERY`/`JSON_TABLE` |
| `CREATE ... IF NOT EXISTS`, `DROP ... IF EXISTS` | Migração que roda uma vez só (Flyway), ou bloco PL/SQL que trata o erro |
| `SELECT` sem `FROM` (`select 1`, `select sysdate`) | `FROM dual` |
| `INSERT ... VALUES (...), (...)` e `FROM (VALUES ...)` | Um `INSERT` por linha, ou `INSERT ALL`, ou `SELECT ... FROM dual UNION ALL ...` |
| `GROUP BY` por alias ou posição (`GROUP BY 1` agrupa pela constante 1 no 19c, sem erro) | Repetir a expressão no `GROUP BY` |
| SQL domains, `ANNOTATIONS`, `VECTOR`, property graph, `RESERVABLE`, `PRECHECK`, MLE | Não usar |
| `DEFAULT ON NULL FOR INSERT ONLY / AND UPDATE` | `DEFAULT ON NULL` (12c), trigger se precisar em update |
| Alias de tabela com `AS` (`FROM t AS x`) | `FROM t x` |
| `UPDATE ... SET ... FROM` / `DELETE ... FROM t2` (join direto) e `RETURNING OLD/NEW` | Subconsulta correlacionada ou `MERGE` |
| `GRANT ... ON SCHEMA`, `DB_DEVELOPER_ROLE` | Privilégios de sistema explícitos |
| Funções do 21c+ (`ANY_VALUE`, `CHECKSUM`, `BIT_*_AGG`, `KURTOSIS_*`, `SKEWNESS_*`) | Equivalentes do 19c |

**Proteções automáticas (CI):**
- **Dialeto fixado:** `jakarta.persistence.database-major-version=19` em `application.yaml` (vale para todos os perfis), para que o Hibernate gere tipos e SQL de 19c. `EscalaApplicationIT` garante isso.
- **Verificador de texto:** `SqlCompativelComOracle19Test` roda o `VerificadorSqlOracle19` (regex) sobre as migrações, `infra/**/*.sql` e os blocos ```sql de `docs/`, e aponta arquivo:linha:regra. Para liberar um falso positivo, comente `oracle19:permitido` na linha.
- **Verificador do schema:** `SchemaCompativelComOracle19IT` confere, no schema migrado, que não há colunas `BOOLEAN`/`JSON`/`VECTOR`, domains nem annotations. Isso é necessário porque o `ddl-auto=validate` **não** diferencia `BOOLEAN` de `NUMBER(1)`.

**Limitações do verificador** (é heurística, não parser):
- Não enxerga SQL em strings Java (`@Query` nativa, `JdbcTemplate`) nem SQL dinâmico dentro de literais (`EXECUTE IMMEDIATE '...'`).
- Tem falsos negativos: alias sem `AS` no `GROUP BY`, subconsulta com alias `AS` e construções que ele não conhece.
- Detecta PL/SQL pelo início da instrução (`BEGIN`, `DECLARE`, `CREATE PROCEDURE`...) e pelo `/` final; fora desse formato, `BOOLEAN` e `TRUE` em PL/SQL podem virar falso positivo.
- Não pega diferenças de comportamento, de otimizador ou de privilégios.

Por isso a garantia final é **rodar as migrações no Autonomous DB 19c real** (schema `ESCALA_VALIDACAO`, passo 5 do `docs/deploy.md`) antes do primeiro deploy e de todo deploy com migração nova.

## Requisitos não funcionais que afetam o código

- Mobile-first: marcar a disponibilidade do mês em menos de 1 minuto pelo celular.
- Páginas em até 2 s; geração da escala do mês em até 30 s.
- Senhas com BCrypt, CSRF ativo, HTTPS (Caddy).
- LGPD: coletar só nome, e-mail e telefone; o membro pode excluir a conta.
- Interface em **pt-BR**, datas no fuso **America/Sao_Paulo** (use `ZoneId.of("America/Sao_Paulo")`, nunca o fuso padrão da JVM).

## Convenções

- Domínio, entidades, tabelas e mensagens ao usuário em **português**. Termos técnicos do framework ficam em inglês (`Controller`, `Service`, `Repository`).
- Tabelas e colunas em `snake_case`; classes em `PascalCase`.
- Migrações Flyway: `V<n>__descricao.sql`, **compatíveis com Oracle 19c** (ver a seção acima). Nunca editar uma migração já aplicada.
- Use records para DTOs e parâmetros de regra.
- Toda ação do gerente que altera escala (forçar, publicar, trocar) registra `Auditoria`.
- Commits pequenos, mensagens em português no imperativo.
- Código Java formatado pelo Spotless (palantir-java-format). Rode `./mvnw spotless:apply` antes de commitar; o hook do pre-commit faz isso e o `verify` falha com código fora do padrão.
- **pre-commit obrigatório** (`pre-commit install`). Os hooks bloqueiam segredos (gitleaks, chaves privadas, `.env`, wallet, `*.jks`, `*.pem`, `*.key`) e arquivos acima de 500 KB. Nunca use `--no-verify`; o CI roda os mesmos hooks.

## Visual

O design system "Escala" (feito no Claude Design) está em `docs/design/`. O `docs/design/README.md` é a fonte da verdade para voz, cor, tipografia e layout; leia antes de qualquer tela.

- **Tokens:** `docs/design/tokens.css` é gerado de `tokens.json` por `python3 docs/design/gerar_tokens_css.py`. Nunca edite o `.css` à mão; token novo entra no `tokens.json`.
- **Tailwind só para layout e ajustes.** O `src/main/frontend/app.css` liga o Tailwind aos tokens (`@theme inline reference`) e apaga o tema padrão: só existem utilitários dos tokens (`bg-brand`, `p-4` = `space-4`, `h-control`, `text-title`, `rounded-pill`...). Uma classe fora deles (`bg-slate-50`, `p-5`, `font-bold`) não gera CSS, sem erro nenhum.
- **Componentes:** as classes `.rt-*` vêm de `docs/design/components/bundle.css` e dos componentes criados no app (Field, Toast, Sheet e a Toolbar no celular, com README na pasta de cada um). Não reescreva componentes em utilitários do Tailwind.
- **Telas usam só os fragmentos de `templates/componentes/`** (botao, badge, lista, tabela, alerta, formulario, toast, navegacao, toolbar, icone) e os layouts de `templates/layouts/`: `simples` (sem navegação), `membro` (celular) e `gerente` (desktop). Cada fragmento documenta as opções no topo do arquivo.
- **Nenhuma cor ou tamanho fixo fora dos tokens:** nada de `style=`, `<style>`, valor arbitrário do Tailwind (`w-[37px]`) ou cor hexadecimal. O `TemplatesUsamSoTokensTest` falha nesses casos.
- **Voz:** português, tratando por "você", sentence case, botões com verbo no infinitivo ("Gerar escala", "Salvar"), sem emoji. Títulos no padrão "Ministério — Período". Todo alerta diz o quê, onde e por quê.
- **Listas de cadastro:** ListRow no celular (`rt-list md:hidden`) e DataTable no desktop (`rt-panel hidden md:block`).
- **Um botão primário por tela.** A Toolbar do gerente é contextual: "Gerar escala" só é primário na página de escalas. O `UmPrimarioPorTela` confere toda página renderizada nos testes de controller e de integração.
- **Ícones:** Lucide com traço 1.5, via `componentes/icone`. Um ícone novo entra em `src/main/frontend/icones.json`; o `IconesTest` pega nome fora da lista.
- **Fonte e ícones hospedados no app** (Urbanist OFL-1.1 e Lucide ISC, do npm com versão fixa). O `copiar-assets.mjs` gera tudo em `target/classes/static` junto com as licenças. Nada de CDN.
- **Sucesso:** depois de um redirect, `addFlashAttribute("sucesso", "...")` vira toast; numa resposta htmx, use `componentes/toast :: toastOob`.
- **Conferir o visual:** `/dev/componentes` (só no perfil `dev`) mostra cada fragmento com os textos dos previews de `docs/design/components/*/preview.html`; `/dev/componentes/membro` e `/gerente` mostram os layouts.

## Política de testes

- **Todo código com lógica nasce com teste unitário no mesmo commit:** services, domínio, validação de parâmetros de regra e utilitários. Use JUnit 5, AssertJ e Mockito, sem subir o Spring.
- Controllers: `@TesteDeController(MeuController.class)` (`@WebMvcTest` com a `SecurityConfig` real, no perfil `test`) e MockMvc, cobrindo autorização (perfil e ministério), validação e CSRF. Não use `@WebMvcTest` direto.
- Restrições do Timefold: `ConstraintVerifier`, cobrindo o caso que penaliza e o que não penaliza.
- Integração com banco: classes `*IT` com Testcontainers (Oracle Free), executadas só no `./mvnw verify` (Failsafe). Testes unitários (`*Test`) rodam no `./mvnw test` (Surefire).
- **Isolamento:**
  - Todo teste que sobe o Spring roda no perfil `test`, nunca no `dev`, para que o seed e as credenciais de dev não apareçam nos testes. O `@TesteDeIntegracao` e o `@TesteDeController` já incluem `@ActiveProfiles("test")`. O `PerfilDosTestesTest` falha se algum teste subir o Spring sem esse perfil, e o `EscalaApplicationIT` confere que ele vence o `SPRING_PROFILES_ACTIVE` do ambiente.
  - Todo `*IT` usa `@TesteDeIntegracao`, que declara toda a configuração (credenciais em `CredenciaisDeTeste`). Nada vem de `.env`, de variáveis de ambiente ou do perfil `dev`.
  - Cada teste cria os próprios dados e usa `@Transactional` para desfazê-los. Nunca dependa de dados de outra classe nem do admin criado na subida. O banco é um container novo, sem reuse.
  - As classes rodam em ordem aleatória. Para reproduzir uma falha, use a semente do log (`-Dfailsafe.runOrder.random.seed=...`).
- Não existe perfil padrão. O `./mvnw spring-boot:run` ativa o `dev`; na IDE, rode `TestEscalaApplication` ou ative o perfil `dev`.
- Correção de bug começa por um teste que reproduz o bug.
- O JaCoCo falha o `verify` se a cobertura de linhas dos testes unitários ficar abaixo de 70% (excluídos `*Application`, `config` e DTOs).

## Comandos

```bash
pre-commit install              # uma vez por clone: ativa os hooks
./mvnw spring-boot:run          # rodar local (perfil dev; sobe o Oracle Free via compose.dev.yaml)
./mvnw test                     # testes unitários e de restrições
./mvnw verify                   # + integração (Testcontainers/Oracle Free), Spotless e JaCoCo
./mvnw spotless:apply           # formatar o código Java
pre-commit run --all-files      # rodar todos os hooks
docker buildx build --platform linux/amd64,linux/arm64 -t escala:latest .
docker compose up -d            # produção (Caddy + app)
```

## Roadmap

1. **Fase 0 — Fundação:** repositório, Spring Boot + Flyway + Oracle, CI, login e perfis, VM Oracle (São Paulo), imagens arm64, deploy com HTTPS.
2. **Fase 1 — Cadastros:** ministérios, funções, níveis, membros, habilitações, modelos de evento.
3. **Fase 2 — Disponibilidade:** tela mobile de marcação, trava do período, painel de quem não respondeu.
4. **Fase 3 — Escala automática:** domínio Timefold, catálogo de regras, geração, alertas, ajuste manual, forçar com justificativa, publicação.
5. **Fase 4 — Pós-publicação:** desistência, alertas ao líder, e-mails de publicação e lembrete de 24 h, texto para WhatsApp, PDF. **Aqui o MVP entra em uso.**
6. **Fase 5 — Refinos:** preferências, relatórios, auditoria completa.
7. **Fase 6 — Expansão:** louvor, criando só os tipos de regra que faltarem.

## Fora do escopo por enquanto

- Integração com WhatsApp
- App nativo ou SPA
- Múltiplas igrejas
- Regras próprias do louvor

## Em aberto (pergunte antes de assumir)

- **Domínio do sistema:** um subdomínio gratuito (ex.: DuckDNS) ou um `.com.br`?
- **Conta de e-mail:** o Gmail pessoal do líder ou um Gmail dedicado ao ministério?
- **Pacote base:** hoje é o provisório `br.igreja.escala` (groupId `br.igreja`, artifactId `escala`). Trocar em um único commit de refatoração quando for decidido.

## Referência

Documento completo de requisitos: *Escala Ministerial – Requisitos Técnicos*.
