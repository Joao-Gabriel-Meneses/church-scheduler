package br.igreja.escala;

/** Configuração declarada pelos testes. Nada vem de .env, variável de ambiente ou perfil dev. */
public final class CredenciaisDeTeste {

    public static final String ADMIN_NOME = "Admin do Teste";
    public static final String ADMIN_EMAIL = "admin.teste@escala.local";
    public static final String ADMIN_SENHA = "senha-do-admin-de-teste";
    public static final String CHAVE_LEMBRAR_ME = "chave-lembrar-me-de-teste";
    public static final String URL_BASE = "https://escala.teste.local";

    /** Para @WebMvcTest, que só precisa da chave do "continuar conectado". */
    public static final String PROPRIEDADE_CHAVE_LEMBRAR_ME = "escala.seguranca.chave-lembrar-me=" + CHAVE_LEMBRAR_ME;

    private CredenciaisDeTeste() {}
}
