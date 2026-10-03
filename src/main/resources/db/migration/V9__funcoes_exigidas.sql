-- Funções que cada evento e cada modelo precisam (ex.: a quinta sem Transmissão, o casamento só com Projeção).
-- Sem linhas, o evento precisa de todas as funções do ministério: é o padrão, e uma função criada depois entra sozinha
-- nos eventos que não foram personalizados. O evento gerado de um modelo copia as linhas do modelo.
-- Excluir a função apaga as linhas dela (on delete cascade); o evento que só tinha essa função volta para "todas".
create table evento_funcao (
    evento_id number not null,
    funcao_id number not null,
    constraint pk_evento_funcao primary key (evento_id, funcao_id),
    constraint fk_evento_funcao_evento foreign key (evento_id) references evento (id),
    constraint fk_evento_funcao_funcao foreign key (funcao_id) references funcao (id) on delete cascade
);
create index ix_evento_funcao_funcao on evento_funcao (funcao_id);

create table modelo_evento_funcao (
    modelo_id number not null,
    funcao_id number not null,
    constraint pk_modelo_evento_funcao primary key (modelo_id, funcao_id),
    constraint fk_modelo_evento_funcao_modelo foreign key (modelo_id) references modelo_evento (id),
    constraint fk_modelo_evento_funcao_funcao foreign key (funcao_id) references funcao (id) on delete cascade
);
create index ix_modelo_evento_funcao_funcao on modelo_evento_funcao (funcao_id);
