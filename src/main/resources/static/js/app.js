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
