package br.igreja.escala.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Procura, por heurística (regex), SQL que roda no Oracle 23ai dos testes mas não no Oracle 19c de produção.
 *
 * <p>Não é um parser: comentários e literais de texto são ignorados, instruções são separadas por {@code ;} e blocos
 * PL/SQL por uma linha com {@code /}. Falsos positivos podem ser liberados com o comentário {@value #MARCADOR} na
 * mesma linha. As limitações estão descritas no CLAUDE.md.
 */
final class VerificadorSqlOracle19 {

    static final String MARCADOR = "oracle19:permitido";

    record Violacao(String arquivo, int linha, String regra, String trecho) {

        @Override
        public String toString() {
            return arquivo + ":" + linha + " [" + regra + "] " + trecho;
        }
    }

    /**
     * Regra por regex. {@code soEmSql}: não vale dentro de PL/SQL, onde a sintaxe existe no 19c. {@code permitido}:
     * trechos apagados antes de aplicar a regra (usos válidos no 19c da mesma palavra).
     */
    private record Regra(String nome, Pattern padrao, boolean soEmSql, Pattern permitido) {

        static Regra de(String nome, String regex) {
            return new Regra(nome, compilar(regex), false, null);
        }

        static Regra soEmSql(String nome, String regex) {
            return new Regra(nome, compilar(regex), true, null);
        }

        static Regra exceto(String nome, String regex, String permitido) {
            return new Regra(nome, compilar(regex), false, compilar(permitido));
        }
    }

    private record Instrucao(int inicio, int fim, boolean plsql) {}

    private static final List<Regra> REGRAS = List.of(
            Regra.soEmSql("tipo BOOLEAN em SQL (use NUMBER(1) com CHECK (col IN (0,1)))", "\\bBOOLEAN\\b"),
            Regra.soEmSql("literal TRUE/FALSE em SQL (use 1/0)", "\\b(TRUE|FALSE)\\b"),
            Regra.exceto(
                    "tipo/construtor JSON nativo (use CLOB com CHECK (col IS JSON))",
                    "\\bJSON\\b",
                    "\\bIS\\s+(NOT\\s+)?JSON\\b|\\b(FORMAT|FOR)\\s+JSON\\b"),
            Regra.de("IF [NOT] EXISTS em DDL", "\\bIF\\s+(NOT\\s+)?EXISTS\\b"),
            Regra.de("VALUES como tabela: FROM (VALUES ...)", "\\(\\s*VALUES\\b"),
            Regra.de("SQL domain", "\\bDOMAIN\\b"),
            Regra.de("annotations", "\\bANNOTATIONS\\s*\\("),
            Regra.de("tipo/função VECTOR", "\\b(TO_)?VECTOR(_\\w+)?\\b"),
            Regra.de("DEFAULT ON NULL FOR INSERT ...", "\\bDEFAULT\\s+ON\\s+NULL\\s+FOR\\s+INSERT\\b"),
            Regra.de("alias de tabela com AS", "\\b(FROM|JOIN|UPDATE)\\s+[\\w.$#\"@]+\\s+AS\\s+(?!OF\\b)\\w+"),
            Regra.de("RETURNING OLD/NEW", "\\bRETURNING\\s+(OLD|NEW)\\b"),
            Regra.de("privilégio de schema (ON SCHEMA)", "\\bON\\s+SCHEMA\\b"),
            Regra.de("DB_DEVELOPER_ROLE (23ai)", "\\bDB_DEVELOPER_ROLE\\b"),
            Regra.de(
                    "recurso do 23ai (property graph, reservable, precheck, MLE, TIME_BUCKET, SYS_ROW_ETAG)",
                    "\\b(GRAPH_TABLE|PROPERTY\\s+GRAPH|RESERVABLE|PRECHECK|MLE|TIME_BUCKET|SYS_ROW_ETAG)\\b"),
            Regra.de(
                    "função do 21c+",
                    "\\b(ANY_VALUE|CHECKSUM|BIT_AND_AGG|BIT_OR_AGG|BIT_XOR_AGG|KURTOSIS_POP|KURTOSIS_SAMP"
                            + "|SKEWNESS_POP|SKEWNESS_SAMP|JSON_TRANSFORM|JSON_SCALAR|JSON_ID)\\s*\\("));

    private static final Pattern INICIO_PLSQL = compilar("(DECLARE|BEGIN)\\b|CREATE\\s+(OR\\s+REPLACE\\s+)?"
            + "((NON)?EDITIONABLE\\s+)?(PROCEDURE|FUNCTION|PACKAGE|TRIGGER|TYPE)\\b");
    private static final Pattern FIM_PLSQL = Pattern.compile("^\\s*/\\s*$", Pattern.MULTILINE);
    /** {@code EXEC proc(...)} do SQL*Plus: um bloco PL/SQL de uma linha só, sem {@code ;} obrigatório. */
    private static final Pattern INICIO_EXEC = compilar("EXEC(UTE)?\\b");

    private static final Pattern SELECT = compilar("\\bSELECT\\b");
    private static final Pattern GROUP_BY = compilar("\\bGROUP\\s+BY\\b");
    private static final Pattern ALIAS = compilar("([\\w$#\"]+(?:\\.[\\w$#\"]+)*|\\))\\s+AS\\s+(\\w+)\\b");
    private static final Pattern VALUES = compilar("\\bVALUES\\s*\\(");
    private static final Pattern FROM = compilar("\\bFROM\\b");
    private static final Pattern INICIO_GRANT = compilar("(GRANT|REVOKE|AUDIT)\\b");
    private static final Pattern INICIO_UPDATE = compilar("UPDATE\\b");
    private static final Pattern INICIO_DELETE = compilar("DELETE\\b");
    private static final Pattern NUMERO = Pattern.compile("\\d+");
    private static final Pattern IDENTIFICADOR = Pattern.compile("\\w+");
    private static final Set<String> FIM_DO_SELECT = Set.of("UNION", "INTERSECT", "MINUS", "EXCEPT");
    private static final Set<String> FIM_DO_GROUP_BY =
            Set.of("HAVING", "ORDER", "UNION", "INTERSECT", "MINUS", "EXCEPT", "FETCH", "OFFSET", "FOR", "MODEL");
    private static final Set<String> NAO_E_ALIAS = Set.of("SELECT", "WITH", "OBJECT", "IDENTITY", "TABLE", "OF");

    private VerificadorSqlOracle19() {}

    static List<Violacao> verificar(String arquivo, String sql) {
        return new Analise(arquivo, sql).executar();
    }

    /** Extrai os blocos ```sql de um Markdown, trocando o resto por linhas vazias para manter a numeração. */
    static String blocosSqlDoMarkdown(String markdown) {
        var saida = new StringBuilder();
        boolean dentro = false;
        for (String linha : markdown.split("\n", -1)) {
            String aparada = linha.strip();
            if (!dentro && aparada.toLowerCase(Locale.ROOT).startsWith("```sql")) {
                dentro = true;
                saida.append('\n');
            } else if (dentro && aparada.startsWith("```")) {
                dentro = false;
                saida.append(";\n");
            } else {
                saida.append(dentro ? linha : "").append('\n');
            }
        }
        return saida.toString();
    }

    private static Pattern compilar(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static final class Analise {

        private final String arquivo;
        private final String[] linhas;
        private final Set<Integer> linhasLiberadas = new HashSet<>();
        private final String sql;
        private final int[] inicioDasLinhas;
        private final List<Violacao> violacoes = new ArrayList<>();

        Analise(String arquivo, String original) {
            this.arquivo = arquivo;
            this.linhas = original.split("\n", -1);
            for (int i = 0; i < linhas.length; i++) {
                if (linhas[i].contains(MARCADOR)) {
                    linhasLiberadas.add(i + 1);
                }
            }
            this.sql = semComentariosNemTextos(original);
            this.inicioDasLinhas = inicioDasLinhas(sql);
        }

        List<Violacao> executar() {
            for (Instrucao instrucao : instrucoes()) {
                String texto = sql.substring(instrucao.inicio(), instrucao.fim());
                aplicarRegras(instrucao, texto);
                verificarSelectSemFrom(instrucao, texto);
                verificarValuesComVariasLinhas(instrucao, texto);
                verificarGroupBy(instrucao, texto);
                verificarJoinEmUpdateOuDelete(instrucao, texto);
            }
            return violacoes;
        }

        private void aplicarRegras(Instrucao instrucao, String texto) {
            for (Regra regra : REGRAS) {
                if (regra.soEmSql() && instrucao.plsql()) {
                    continue;
                }
                String alvo = regra.permitido() == null ? texto : substituirPorEspacos(texto, regra.permitido());
                Matcher m = regra.padrao().matcher(alvo);
                while (m.find()) {
                    registrar(instrucao.inicio() + m.start(), regra.nome());
                }
            }
        }

        /** Cada SELECT precisa de um FROM no mesmo nível de parênteses (no 19c não existe SELECT sem FROM). */
        private void verificarSelectSemFrom(Instrucao instrucao, String texto) {
            if (INICIO_GRANT.matcher(texto.stripLeading()).lookingAt()) {
                return;
            }
            Matcher m = SELECT.matcher(texto);
            while (m.find()) {
                if (!temFromNoMesmoNivel(texto, m.end())) {
                    registrar(instrucao.inicio() + m.start(), "SELECT sem FROM (use FROM dual)");
                }
            }
        }

        private static boolean temFromNoMesmoNivel(String texto, int desde) {
            int nivel = 0;
            for (int i = desde; i < texto.length(); i++) {
                char c = texto.charAt(i);
                if (c == '(') {
                    nivel++;
                } else if (c == ')' && --nivel < 0) {
                    return false;
                } else if (nivel == 0 && c == ';') {
                    return false;
                } else if (nivel == 0 && inicioDePalavra(texto, i)) {
                    String palavra = palavraEm(texto, i);
                    if (palavra.equals("FROM")) {
                        return true;
                    }
                    if (FIM_DO_SELECT.contains(palavra)) {
                        return false;
                    }
                }
            }
            return false;
        }

        /** INSERT ... VALUES (...), (...) só existe a partir do 23ai. */
        private void verificarValuesComVariasLinhas(Instrucao instrucao, String texto) {
            Matcher m = VALUES.matcher(texto);
            while (m.find()) {
                int depois = fimDosParenteses(texto, m.end() - 1);
                int proximo = proximoNaoEspaco(texto, depois);
                if (proximo < texto.length() && texto.charAt(proximo) == ',') {
                    registrar(instrucao.inicio() + m.start(), "VALUES com várias linhas (use um INSERT por linha)");
                }
            }
        }

        /** GROUP BY 1 no 19c agrupa pela constante 1 (sem erro); GROUP BY alias não compila. */
        private void verificarGroupBy(Instrucao instrucao, String texto) {
            Set<String> aliases = aliasesDerivados(texto);
            Matcher m = GROUP_BY.matcher(texto);
            while (m.find()) {
                for (String item : itensDoGroupBy(texto, m.end())) {
                    if (NUMERO.matcher(item).matches()) {
                        registrar(instrucao.inicio() + m.start(), "GROUP BY por posição");
                    } else if (aliases.contains(item.toUpperCase(Locale.ROOT))) {
                        registrar(instrucao.inicio() + m.start(), "GROUP BY por alias (repita a expressão)");
                    }
                }
            }
        }

        /** UPDATE ... SET ... FROM e DELETE ... FROM outra_tabela (join direto) são do 23ai. */
        private void verificarJoinEmUpdateOuDelete(Instrucao instrucao, String texto) {
            String aparado = texto.stripLeading();
            int deslocamento = texto.length() - aparado.length();
            boolean update = INICIO_UPDATE.matcher(aparado).lookingAt();
            boolean delete = INICIO_DELETE.matcher(aparado).lookingAt();
            if (!update && !delete) {
                return;
            }
            Matcher m = FROM.matcher(aparado);
            while (m.find()) {
                boolean fromDoDelete =
                        delete && aparado.substring(0, m.start()).strip().equalsIgnoreCase("DELETE");
                if (nivelEm(aparado, m.start()) == 0 && !fromDoDelete) {
                    registrar(instrucao.inicio() + deslocamento + m.start(), "UPDATE/DELETE com join direto (FROM)");
                }
            }
        }

        private List<Instrucao> instrucoes() {
            List<Instrucao> instrucoes = new ArrayList<>();
            int pos = 0;
            while ((pos = proximoNaoEspaco(sql, pos)) < sql.length()) {
                Matcher inicio = INICIO_PLSQL.matcher(sql).region(pos, sql.length());
                if (INICIO_EXEC.matcher(sql).region(pos, sql.length()).lookingAt()) {
                    int fimDaLinha = sql.indexOf('\n', pos);
                    int ate = fimDaLinha < 0 ? sql.length() : fimDaLinha;
                    instrucoes.add(new Instrucao(pos, ate, true));
                    pos = ate;
                } else if (inicio.lookingAt()) {
                    Matcher fim = FIM_PLSQL.matcher(sql);
                    boolean achou = fim.find(pos);
                    instrucoes.add(new Instrucao(pos, achou ? fim.start() : sql.length(), true));
                    pos = achou ? fim.end() : sql.length();
                } else {
                    int pontoEVirgula = sql.indexOf(';', pos);
                    int ate = pontoEVirgula < 0 ? sql.length() : pontoEVirgula;
                    instrucoes.add(new Instrucao(pos, ate, false));
                    pos = ate + 1;
                }
            }
            return instrucoes;
        }

        private void registrar(int posicao, String regra) {
            int linha = linhaDe(posicao);
            if (!linhasLiberadas.contains(linha)) {
                violacoes.add(new Violacao(arquivo, linha, regra, linhas[linha - 1].strip()));
            }
        }

        private int linhaDe(int posicao) {
            int i = Arrays.binarySearch(inicioDasLinhas, posicao);
            return (i >= 0 ? i : -i - 2) + 1;
        }

        private static Set<String> aliasesDerivados(String texto) {
            Set<String> aliases = new HashSet<>();
            Matcher m = ALIAS.matcher(texto);
            while (m.find()) {
                String expressao = m.group(1);
                String alias = m.group(2).toUpperCase(Locale.ROOT);
                String ultimaParte = expressao.substring(expressao.lastIndexOf('.') + 1);
                if (!NAO_E_ALIAS.contains(alias) && !ultimaParte.equalsIgnoreCase(alias)) {
                    aliases.add(alias);
                }
            }
            return aliases;
        }

        private static List<String> itensDoGroupBy(String texto, int desde) {
            List<String> itens = new ArrayList<>();
            int nivel = 0;
            int inicioDoItem = desde;
            int i = desde;
            for (; i < texto.length(); i++) {
                char c = texto.charAt(i);
                if (c == '(') {
                    nivel++;
                } else if (c == ')' && --nivel < 0) {
                    break;
                } else if (nivel == 0 && c == ';') {
                    break;
                } else if (nivel == 0 && c == ',') {
                    itens.add(texto.substring(inicioDoItem, i).strip());
                    inicioDoItem = i + 1;
                } else if (nivel == 0 && inicioDePalavra(texto, i) && FIM_DO_GROUP_BY.contains(palavraEm(texto, i))) {
                    break;
                }
            }
            itens.add(texto.substring(inicioDoItem, i).strip());
            return itens.stream()
                    .filter(item -> IDENTIFICADOR.matcher(item).matches())
                    .toList();
        }
    }

    // Utilitários de texto; os literais já foram trocados por espaços, então parênteses em strings não atrapalham.

    /** Troca comentários e literais por espaços, preservando quebras de linha (e, com isso, a numeração). */
    static String semComentariosNemTextos(String sql) {
        var saida = new StringBuilder(sql.length());
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            char proximo = i + 1 < n ? sql.charAt(i + 1) : '\0';
            if (c == '-' && proximo == '-') {
                while (i < n && sql.charAt(i) != '\n') {
                    saida.append(' ');
                    i++;
                }
            } else if (c == '/' && proximo == '*') {
                int fim = sql.indexOf("*/", i + 2);
                int ate = fim < 0 ? n : fim + 2;
                apagar(sql, i, ate, saida);
                i = ate;
            } else if ((c == 'q' || c == 'Q') && proximo == '\'' && i + 2 < n && !parteDeIdentificador(sql, i - 1)) {
                char fecha = fechamento(sql.charAt(i + 2));
                int fim = sql.indexOf(fecha + "'", i + 3);
                int ate = fim < 0 ? n : fim + 2;
                apagar(sql, i, ate, saida);
                i = ate;
            } else if (c == '\'') {
                int j = i + 1;
                while (j < n && !(sql.charAt(j) == '\'' && (j + 1 >= n || sql.charAt(j + 1) != '\''))) {
                    j += sql.charAt(j) == '\'' ? 2 : 1;
                }
                int ate = Math.min(j + 1, n);
                apagar(sql, i, ate, saida);
                i = ate;
            } else if (c == '"') {
                int fim = sql.indexOf('"', i + 1);
                int ate = fim < 0 ? n : fim + 1;
                saida.append('"');
                for (int k = i + 1; k < ate - 1; k++) {
                    saida.append(sql.charAt(k) == '\n' ? '\n' : 'x');
                }
                saida.append(ate - i > 1 ? "\"" : "");
                i = ate;
            } else {
                saida.append(c);
                i++;
            }
        }
        return saida.toString();
    }

    private static void apagar(String sql, int de, int ate, StringBuilder saida) {
        for (int k = de; k < ate; k++) {
            saida.append(sql.charAt(k) == '\n' ? '\n' : ' ');
        }
    }

    private static char fechamento(char abertura) {
        return switch (abertura) {
            case '[' -> ']';
            case '{' -> '}';
            case '(' -> ')';
            case '<' -> '>';
            default -> abertura;
        };
    }

    private static boolean parteDeIdentificador(String texto, int i) {
        return i >= 0 && (Character.isLetterOrDigit(texto.charAt(i)) || texto.charAt(i) == '_');
    }

    private static boolean inicioDePalavra(String texto, int i) {
        return Character.isLetter(texto.charAt(i)) && !parteDeIdentificador(texto, i - 1);
    }

    private static String palavraEm(String texto, int i) {
        int fim = i;
        while (fim < texto.length() && parteDeIdentificador(texto, fim)) {
            fim++;
        }
        return texto.substring(i, fim).toUpperCase(Locale.ROOT);
    }

    private static int nivelEm(String texto, int ate) {
        int nivel = 0;
        for (int i = 0; i < ate; i++) {
            nivel += texto.charAt(i) == '(' ? 1 : texto.charAt(i) == ')' ? -1 : 0;
        }
        return nivel;
    }

    private static int fimDosParenteses(String texto, int abre) {
        int nivel = 0;
        for (int i = abre; i < texto.length(); i++) {
            nivel += texto.charAt(i) == '(' ? 1 : texto.charAt(i) == ')' ? -1 : 0;
            if (nivel == 0) {
                return i + 1;
            }
        }
        return texto.length();
    }

    private static int proximoNaoEspaco(String texto, int desde) {
        int i = desde;
        while (i < texto.length() && Character.isWhitespace(texto.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String substituirPorEspacos(String texto, Pattern padrao) {
        return padrao.matcher(texto).replaceAll(r -> " ".repeat(r.group().length()));
    }

    private static int[] inicioDasLinhas(String texto) {
        List<Integer> inicios = new ArrayList<>(List.of(0));
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') {
                inicios.add(i + 1);
            }
        }
        return inicios.stream().mapToInt(Integer::intValue).toArray();
    }
}
