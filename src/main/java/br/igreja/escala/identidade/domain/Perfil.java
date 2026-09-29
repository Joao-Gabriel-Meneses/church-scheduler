package br.igreja.escala.identidade.domain;

/**
 * Perfis globais. O perfil de gerente é por ministério e vem da membresia, não daqui.
 */
public enum Perfil {
    MEMBRO,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
