-- Executar em homologacao ANTES de publicar a v1.0.22.
-- Confirmar se as colunas ainda nao existem na AD_CONF_CARGA.
ALTER TABLE AD_CONF_CARGA ADD (
    CODUSUFIN NUMBER(10),
    DHFINALIZACAO DATE,
    DIVERGENTE VARCHAR2(1)
);
-- Se houver CHECK constraint no STATUS, ampliar os valores permitidos para O e D.
-- Nao executar um novo CHECK sem verificar as restricoes ja existentes.
