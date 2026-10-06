-- Ajuste manual da escala (Fase 3b). A versão é o controle de concorrência otimista da vaga (@Version): duas abas
-- editando a mesma vaga, a segunda é recusada em vez de sobrescrever.
alter table vaga add (versao number(10) default 0 not null);
-- Forçada é sempre fixada: gerar de novo não tira quem o gerente forçou. A 3a nunca gravou forcada = 1.
alter table vaga add constraint ck_vaga_forcada_fixada check (forcada = 0 or fixada = 1);
-- O registro de um ajuste leva o antes, o depois e a justificativa (até 500 caracteres).
alter table auditoria modify (descricao varchar2(1000 char));
