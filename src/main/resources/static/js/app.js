// Envia o token CSRF do Spring Security em toda requisição do htmx.
document.addEventListener("htmx:configRequest", (evento) => {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const cabecalho = document.querySelector('meta[name="_csrf_header"]')?.content;
  if (token && cabecalho) {
    evento.detail.headers[cabecalho] = token;
  }
});

// Toast de sucesso (templates/componentes/toast.html): some em 5 s, mas não com o ponteiro ou o foco nele,
// ou ao clicar em fechar. htmx.onLoad roda na carga da página e em todo conteúdo novo, inclusive toasts OOB.
const DURACAO_DO_TOAST_MS = 5000;

function fecharToast(toast) {
  toast.classList.add("rt-toast--saindo");
  const semAnimacao = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  setTimeout(() => toast.remove(), semAnimacao ? 0 : 150);
}

function agendarFechamento(toast) {
  setTimeout(() => {
    if (toast.matches(":hover, :focus-within")) {
      agendarFechamento(toast);
    } else {
      fecharToast(toast);
    }
  }, DURACAO_DO_TOAST_MS);
}

document.addEventListener("click", (evento) => {
  const botao = evento.target.closest("[data-fechar-toast]");
  if (botao) {
    fecharToast(botao.closest("[data-toast]"));
  }
});

// Botão "copiar" (componentes/botao, opção copiar): copia o texto do elemento indicado. Se a página tiver um
// <template id="<id>-copiado"> com um Toast, mostra o toast e fecha o Sheet em volta do botão. Sem acesso à área de
// transferência (navegador antigo ou página sem HTTPS), seleciona o texto para a pessoa copiar à mão.
document.addEventListener("click", async (evento) => {
  const botao = evento.target.closest("[data-copiar]");
  const alvo = botao && document.getElementById(botao.dataset.copiar);
  if (!alvo) {
    return;
  }
  try {
    await navigator.clipboard.writeText(alvo.innerText);
  } catch {
    window.getSelection().selectAllChildren(alvo);
    return;
  }
  const aviso = document.getElementById(`${botao.dataset.copiar}-copiado`);
  if (aviso) {
    const toast = aviso.content.firstElementChild.cloneNode(true);
    document.getElementById("toasts").append(toast);
    agendarFechamento(toast);
  }
  botao.closest("[popover]")?.hidePopover();
});

htmx.onLoad((conteudo) => {
  const toasts = conteudo.matches("[data-toast]") ? [conteudo] : conteudo.querySelectorAll("[data-toast]");
  toasts.forEach(agendarFechamento);
});

// Tooltip (docs/design/components/Tooltip): o Esc esconde sem tirar o foco nem mover o ponteiro (WCAG 1.4.13).
// Volta a aparecer quando o foco ou o ponteiro saem do controle em que o Esc foi apertado.
let tooltipEscondidoEm = null;

document.addEventListener("keydown", (evento) => {
  if (evento.key === "Escape") {
    tooltipEscondidoEm = document.querySelector(".rt-com-tooltip:hover, .rt-com-tooltip:focus-visible");
    document.documentElement.classList.toggle("rt-sem-tooltip", tooltipEscondidoEm !== null);
  }
});
for (const tipo of ["focusin", "mouseover"]) {
  document.addEventListener(tipo, (evento) => {
    if (tooltipEscondidoEm && !tooltipEscondidoEm.contains(evento.target)) {
      tooltipEscondidoEm = null;
      document.documentElement.classList.remove("rt-sem-tooltip");
    }
  });
}

// NavMenu (docs/design/components/NavMenu): é um <details>, que abre e fecha sem JS. Aqui só fecha com clique fora,
// com Esc (devolvendo o foco à aba) e quando outro menu abre.
function fecharMenus(exceto) {
  document.querySelectorAll("details.rt-nav-menu[open]").forEach((menu) => {
    if (menu !== exceto) {
      menu.open = false;
    }
  });
}

document.addEventListener("click", (evento) => {
  fecharMenus(evento.target.closest("details.rt-nav-menu"));
});

document.addEventListener("keydown", (evento) => {
  const menu = evento.key === "Escape" && document.activeElement?.closest("details.rt-nav-menu[open]");
  if (menu) {
    menu.open = false;
    menu.querySelector("summary").focus();
  }
});

// Sheet da vaga (escala/fragments/escala): o htmx põe a vaga tocada na grade dentro do Sheet e o abre. Sem JS, o link
// da vaga abre a página dela. A recusa de uma ação volta para o Sheet já aberto.
document.addEventListener("htmx:afterSwap", (evento) => {
  const sheet = document.getElementById("vaga-sheet");
  if (evento.detail.target.id === "vaga-conteudo" && sheet && !sheet.matches(":popover-open")) {
    sheet.showPopover();
  }
});
