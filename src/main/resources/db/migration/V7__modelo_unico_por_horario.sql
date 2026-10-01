-- Um modelo por ministério, dia da semana e horário: dois "Culto de domingo, 18h00" gerariam o mesmo culto duas
-- vezes. Outro horário no mesmo dia (culto da manhã e da noite) e outro ministério no mesmo horário continuam valendo.
alter table modelo_evento add constraint uk_modelo_evento_horario unique (ministerio_id, dia_semana, horario_minutos);
