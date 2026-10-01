-- Duração dos modelos e dos eventos, para saber quando cada evento termina: a escala (Fase 3) não põe a mesma
-- pessoa em dois eventos com horários sobrepostos. O que já existe fica com 2 horas, a duração de um culto.
alter table modelo_evento add (duracao_minutos number(4) default 120 not null);
alter table modelo_evento add constraint ck_modelo_evento_duracao check (duracao_minutos between 1 and 1440);
alter table evento add (duracao_minutos number(4) default 120 not null);
alter table evento add constraint ck_evento_duracao check (duracao_minutos between 1 and 1440);
