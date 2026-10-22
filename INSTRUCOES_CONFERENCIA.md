# Conferência Cega de Cargas — v1.0.0

## Alterações feitas sobre o template original
- Nome do projeto em `settings.gradle`.
- `group` Java em `build.gradle`; `appKey`, parceiro, `autoDDL`, servidor e versão mínima preservados.
- Menu substituído por um único item **Conferência Cega** (`CONF_CARGAS`).
- Interface HTML5/AngularJS adicionada em `vc/src/main/webapp/html5/CONF_CARGAS/`.
- Exemplos originais do template mantidos nos diretórios, mas retirados do menu.

## Escopo desta versão
Teste visual de carregamento da tela e preenchimento dos campos empresa e OC. **Não consulta a OC, não cria tabelas, não registra leituras e não altera documentos**.

## Próximos passos
1. Abrir a pasta do projeto no VS Code.
2. Configurar o ambiente local e conferir as credenciais de homologação.
3. Executar o build/validação do Addon Studio e publicar somente em homologação.
4. Abrir **Conferência de Cargas > Conferência Cega** e testar os campos.
5. Enviar captura da tela e log de build; então implementar consulta Oracle real na v1.0.1.

**Atenção:** a publicação não foi executada aqui, portanto o funcionamento no servidor ainda precisa ser validado.
