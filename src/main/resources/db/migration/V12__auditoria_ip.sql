-- IP de quem fez a ação: o do usuário (CF-Connecting-IP, atrás do Cloudflare) ou o da requisição.
-- Nulo nos registros antigos e nas ações sem requisição (ex.: a gravação da geração, que roda numa fila).
alter table auditoria add (ip varchar2(45 char));
