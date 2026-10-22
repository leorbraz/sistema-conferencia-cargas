# Correção da geração do Add-on

Erro: `processDashboards FAILED` devido a `dashboards/dashboardAtendimento.xml` não declarado no menu.

Correção: remoção do dashboard de exemplo, que não é utilizado pela Conferência Cega de Cargas.

Execute: `./gradlew clean gerarAddon` (PowerShell: `.\gradlew clean gerarAddon`).

Não foram modificados appKey, metadados de tabelas, serviços, menus ou UI.

A compilação e publicação devem ser testadas em homologação.
