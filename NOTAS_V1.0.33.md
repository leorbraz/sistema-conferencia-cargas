# v1.0.33 - Correcao da justificativa com acentos

- Corrigida pontualmente a leitura HTTP de JUSTIFICATIVA no EJB `ConferenciaCargaSPBean`.
- Recupera UTF-8 interpretado como Windows-1252 somente quando ha sinais de mojibake e a sequencia de bytes e valida.
- Mantem textos recebidos corretamente e todas as regras administrativas.
- Nenhuma alteracao de banco ou layout.
- O registro antigo NUACAO=2 nao e alterado automaticamente.

## Publicacao

1. `./gradlew clean build` (Windows: `.\gradlew clean build`)
2. Verificar a geracao do EJB e do descritor.
3. `.\gradlew publishAddon deployAddon configureAddon processDashboards`
4. Em homologacao, testar justificativas com `nao`, `não`, `ação`, `ç`, `é`, `ã`, e consultar DUMP(JUSTIFICATIVA,1016).

## Limite

O codigo trata a causa observada na leitura HTTP da justificativa; deve ser validado no ambiente Sankhya.
