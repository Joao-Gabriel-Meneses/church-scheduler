package br.igreja.escala.compartilhado.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class InicioController {

    @GetMapping("/")
    String inicio() {
        return "inicio";
    }
}
