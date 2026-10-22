# Conferência Cega de Cargas — limpeza do template

Esta versão remove apenas os metadados e scripts que criariam TPL_ATENDIMENTO, TPL_HIERARQUICA e FN_SOMA, além das classes de exemplo com hooks/serviços e interface HTML5 de exemplo.

O menu e a tela CONF_CARGAS foram mantidos, assim como build.gradle, appKey, parceiroNome e web.xml.

## Atenção
- NÃO aprove os scripts de banco de dados exibidos na instalação anterior.
- Faça backup da pasta local antes de substituir os arquivos.
- A limpeza do ZIP não desfaz scripts já aprovados no banco.
- Execute `.\gradlew clean gerarAddon` e verifique a nova lista de scripts antes de aprovar qualquer mudança.
- O build não foi executado neste ambiente por depender do SDK e das dependências do Addon Studio.
