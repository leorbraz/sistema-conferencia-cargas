# v1.0.32 — Historico administrativo pelo autoDDL

- `datadictionary/acao.xml`: tabela AD_CONF_CARGA_ACAO instalada pelo mecanismo `autoDDL=true` ja usado no projeto.
- `ConferenciaCargaSPBean`: JUSTIFICATIVA lida como parametro HTTP opcional; regra de negocio continua exigindo motivo para divergencias e reconferencia.
- `ValidacaoConferenciaService`: identificador NUACAO gerado sob lock transacional da tabela, sem exigir sequence Oracle criada manualmente. A operacao administrativa serializa brevemente as gravacoes no historico.
- `build.gradle`: prepara timestamp da classe Bean e valida os artefatos XDoclet antes da compilacao Java.

## Testes em homologacao

1. Publicar e configurar o addon para executar o autoDDL; confirmar `AD_CONF_CARGA_ACAO` no Oracle.
2. Executar `gradlew clean build` e verificar `[Conferencia Cargas] XDoclet OK`.
3. Testar Validar OK (sem justificativa), Validar com divergencias (motivo obrigatorio) e Solicitar reconferencia (motivo obrigatorio).
4. Confirmar historico e estados no Oracle. Nao liberar administracao em producao antes de implementar controle de acesso CODUSU.

Nota: a tabela de historico e nova; o autoDDL depende dos privilegios do usuario de implantacao e da configuracao da plataforma Sankhya.
