-- MAX_POR_NIVEL_NO_EVENTO saiu do catálogo e MIN_POR_NIVEL_NO_EVENTO entrou no lugar: todo evento com alguém escalado
-- tem pelo menos N pessoas de um nível (na Mídia, um Experiente). A regra nova nasce desligada e sem nível em todo
-- ministério, como a antiga nasceu; o gerente liga na página de regras.
delete from regra where tipo = 'MAX_POR_NIVEL_NO_EVENTO';
insert into regra (ministerio_id, tipo, parametros, rigidez, peso, ativa)
select m.id, 'MIN_POR_NIVEL_NO_EVENTO', '{"nivelId":null,"minimo":1}', 'HARD', 1, 0 from ministerio m;
