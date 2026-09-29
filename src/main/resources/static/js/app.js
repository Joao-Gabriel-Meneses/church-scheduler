// Envia o token CSRF do Spring Security em toda requisição do htmx.
document.addEventListener("htmx:configRequest", (evento) => {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const cabecalho = document.querySelector('meta[name="_csrf_header"]')?.content;
  if (token && cabecalho) {
    evento.detail.headers[cabecalho] = token;
  }
});
