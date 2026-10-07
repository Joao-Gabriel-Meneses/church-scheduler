-- Desistência do membro (Fase 4): quem saiu da vaga e quando. A vaga fica vazia (e fixada) até alguém entrar no lugar;
-- pôr uma pessoa na vaga apaga a desistência, que continua na auditoria. Enquanto houver desistente, o gerente vê o
-- alerta na página de escalas.
alter table vaga add (
    desistente_id number,
    desistiu_em   timestamp with time zone
);
alter table vaga add constraint fk_vaga_desistente foreign key (desistente_id) references usuario (id);
-- Desistente e hora andam juntos, e a vaga com desistente está vazia.
alter table vaga add constraint ck_vaga_desistencia check (
    (desistente_id is null and desistiu_em is null)
    or (desistente_id is not null and desistiu_em is not null and usuario_id is null)
);
create index ix_vaga_desistente on vaga (desistente_id);
