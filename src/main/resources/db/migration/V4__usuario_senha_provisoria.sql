-- Senha provisória: definida pelo gerente no cadastro do membro ou ao redefinir a senha dele.
-- Enquanto for 1, o membro só consegue abrir a tela de trocar a senha.
alter table usuario add (senha_provisoria number(1) default 0 not null);
alter table usuario add constraint ck_usuario_senha_provisoria check (senha_provisoria in (0, 1));
