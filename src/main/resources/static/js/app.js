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
