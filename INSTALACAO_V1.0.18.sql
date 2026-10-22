-- Executar SOMENTE apos publicar os metadados da v1.0.18 no Sankhya.
-- Confirme a existencia das colunas e da tabela antes de rodar.
-- Sequence para auditoria concorrente; execute uma unica vez.
CREATE SEQUENCE AD_CONF_HIST_SEQ START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ATENCAO: atualizar a conciliacao para ignorar leituras excluidas:
-- Em cada consulta sobre AD_CONF_CARGA_LEIT L, adicionar:
-- AND NVL(L.STATUS, 'A') = 'A'
-- Nao usar a condicao em AD_CONF_CARGA_PREV.
