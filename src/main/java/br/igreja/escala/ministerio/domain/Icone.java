package br.igreja.escala.ministerio.domain;

/**
 * Ícones que se escolhem para ministérios (SideRail) e funções (tile da ListRow). Cada um precisa estar em
 * src/main/frontend/icones.json; o IconeTest confere.
 */
public enum Icone {
    MONITOR("monitor", "Monitor"),
    VIDEO("video", "Câmera de vídeo"),
    CAMERA("camera", "Câmera fotográfica"),
    MUSIC("music", "Música"),
    MIC("mic", "Microfone"),
    USERS("users", "Pessoas"),
    HAND_HEART("hand-heart", "Mão com coração"),
    BABY("baby", "Criança"),
    BOOK_OPEN("book-open", "Livro"),
    CLIPBOARD_LIST("clipboard-list", "Prancheta"),
    COFFEE("coffee", "Café"),
    DOOR_OPEN("door-open", "Porta");

    private final String lucide;
    private final String rotulo;

    Icone(String lucide, String rotulo) {
        this.lucide = lucide;
        this.rotulo = rotulo;
    }

    /** Nome do ícone no sprite Lucide, usado em {@code componentes/icone}. */
    public String lucide() {
        return lucide;
    }

    public String rotulo() {
        return rotulo;
    }
}
