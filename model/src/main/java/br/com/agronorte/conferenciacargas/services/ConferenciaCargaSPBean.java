package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.modelcore.util.BaseSPBean;
import br.com.sankhya.ws.ServiceContext;
import javax.ejb.SessionBean;
import java.math.BigDecimal;

/**
 * @ejb.bean name="ConferenciaCargaSP"
 *           jndi-name="SANKHYA/ConferenciaCargaSP"
 *           type="Stateless"
 *           transaction-type="Container"
 *           view-type="remote"
 *
 * @ejb.transaction type="Supports"
 * @ejb.util generate="false"
 */
public class ConferenciaCargaSPBean
    extends BaseSPBean implements SessionBean {

    /**
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void buscarConferencia(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirOperador();
        try {
            BigDecimal empresa = ctx.getParameterAsBigDecimal("CODEMP");
            BigDecimal ordem = ctx.getParameterAsBigDecimal("ORDEMCARGA");
            String filtro = ctx.getHttpRequest().getParameter("FILTRO");
            String modo = ctx.getHttpRequest().getParameter("MODO");
            ctx.setJsonResponse(new BuscaConferenciaService().consultar(empresa, ordem, "FILA".equals(modo) ? "FILA" : filtro, "PESQUISA_EMPRESA".equals(modo), ctx.getHttpRequest().getParameter("STATUSFILA"), ctx.getHttpRequest().getParameter("DOCA")));
            ctx.setStatus(ServiceContext.STATUS_OK);
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage());
        }
    }

    /**
     * Consulta somente a unidade padrao do produto a partir do codigo de barras.
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void consultarUnidadeProduto(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirOperador();
        try {
            String barra = ctx.getParameterAsString("CODBARRA");
            ctx.setJsonResponse(new UnidadeProdutoService().consultar(barra));
            ctx.setStatus(ServiceContext.STATUS_OK);
            ctx.setStatusMessage("Unidade consultada.");
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage());
        }
    }

    /**
     * Compara etiqueta fisica com AD_DOCA da OC; sem persistencia adicional.
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void validarDoca(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirOperador();
        try {
            BigDecimal empresa = ctx.getParameterAsBigDecimal("CODEMP");
            BigDecimal ordem = ctx.getParameterAsBigDecimal("ORDEMCARGA");
            String lida = ctx.getParameterAsString("DOCA_LIDA");
            ctx.setJsonResponse(new ValidacaoDocaService().validar(empresa, ordem, lida));
            ctx.setStatus(ServiceContext.STATUS_OK);
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage());
        }
    }

    /**
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void testarConexao(ServiceContext ctx) throws Exception {

        ctx.setStatus(ServiceContext.STATUS_OK);

        ctx.setStatusMessage(
            "Servico Java da Conferencia de Cargas funcionando!"
        );
    }

    /**
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void consultarOC(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirSupervisor();
        BigDecimal empresa = ctx.getParameterAsBigDecimal("CODEMP");
        BigDecimal ordem = ctx.getParameterAsBigDecimal("ORDEMCARGA");
        try {
            ctx.setJsonResponse(new OrdemCargaService().consultar(empresa, ordem));
            ctx.setStatus(ServiceContext.STATUS_OK);
            ctx.setStatusMessage("Consulta concluida.");
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage() == null ? "Falha na consulta." : e.getMessage());
        }
    }

    /**
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void consultarComposicao(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirSupervisor();
        BigDecimal empresa = ctx.getParameterAsBigDecimal("CODEMP");
        BigDecimal ordem = ctx.getParameterAsBigDecimal("ORDEMCARGA");
        try {
            ctx.setJsonResponse(new ComposicaoRomaneioService().consultar(empresa, ordem));
            ctx.setStatus(ServiceContext.STATUS_OK);
            ctx.setStatusMessage("Validacao FIFO concluida.");
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage() == null ? "Falha na validacao FIFO." : e.getMessage());
        }
    }


    /**
     * Congelamento transacional. Em caso de erro, propaga excecao para rollback.
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Required"
     */
    public void iniciarConferencia(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirSupervisor();
        try {
            BigDecimal empresa = ctx.getParameterAsBigDecimal("CODEMP");
            BigDecimal ordem = ctx.getParameterAsBigDecimal("ORDEMCARGA");
            BigDecimal usuario = br.com.sankhya.modelcore.auth.AuthenticationInfo.getCurrent().getUserID();
            ctx.setJsonResponse(new CongelamentoConferenciaService().iniciar(empresa, ordem, usuario));
            ctx.setStatus(ServiceContext.STATUS_OK);
            ctx.setStatusMessage("Previsao congelada e conferencia liberada.");
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage());
            throw new IllegalStateException("Falha ao congelar previsao: " + e.getMessage(), e);
        }
    }


    /**
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Required"
     */
    public void registrarLeitura(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirOperador();
        BigDecimal nuconf = ctx.getParameterAsBigDecimal("NUCONF");
        String barra = ctx.getParameterAsString("CODBARRA");
        String lote = ctx.getParameterAsString("CONTROLE");
        String unidade = ctx.getParameterAsString("CODVOL");
        String evento = ctx.getParameterAsString("IDEVENTO");
        BigDecimal qtd = ctx.getParameterAsBigDecimal("QTD");
        BigDecimal usuario = br.com.sankhya.modelcore.auth.AuthenticationInfo.getCurrent().getUserID();
        try {
            ctx.setJsonResponse(new LeituraConferenciaService().registrar(nuconf,barra,lote,qtd,unidade,evento,usuario,"S".equals(ctx.getParameterAsString("CONFIRMAR_LOTE"))));
            ctx.setStatus(ServiceContext.STATUS_OK);
            ctx.setStatusMessage("Leitura processada.");
        } catch (Exception e) {
            // Propagar para garantir rollback transacional.
            throw new IllegalStateException("Falha ao registrar leitura: " + e.getMessage(), e);
        }
    }

    /**
     * Lista apenas produtos previstos e leituras fisicas realizadas.
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void consultarPainelConferente(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirOperador();
        try {
            BigDecimal nuconf = ctx.getParameterAsBigDecimal("NUCONF");
            ctx.setJsonResponse(new ConsultaConferenteService().consultar(nuconf));
            ctx.setStatus(ServiceContext.STATUS_OK);
            ctx.setStatusMessage("Painel do conferente atualizado.");
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage() == null ? "Falha ao consultar painel." : e.getMessage());
        }
    }


    /**
     * Resultado administrativo: inclui previsao, sem expor dados na API do conferente.
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void consultarConciliacao(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirSupervisor();
        try {
            BigDecimal nuconf = ctx.getParameterAsBigDecimal("NUCONF");
            ctx.setJsonResponse(new ConciliacaoConferenciaService().consultar(nuconf));
            ctx.setStatus(ServiceContext.STATUS_OK);
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage());
        }
    }

    /**
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void listarLeiturasIndividuais(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirOperador();
        try {
            BigDecimal nuconf = ctx.getParameterAsBigDecimal("NUCONF");
            BigDecimal produto = ctx.getParameterAsBigDecimal("CODPROD");
            String lote = ctx.getParameterAsString("CONTROLE");
            String unidade = ctx.getParameterAsString("CODVOL");
            ctx.setJsonResponse(new ExclusaoLeituraService().listar(nuconf, produto, lote, unidade));
            ctx.setStatus(ServiceContext.STATUS_OK);
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage());
        }
    }

    /**
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Required"
     */
    public void excluirLeitura(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirOperador();
        try {
            BigDecimal nuconf = ctx.getParameterAsBigDecimal("NUCONF");
            BigDecimal nuleit = ctx.getParameterAsBigDecimal("NULEIT");
            String motivo = ctx.getParameterAsString("MOTIVO");
            BigDecimal usuario = br.com.sankhya.modelcore.auth.AuthenticationInfo.getCurrent().getUserID();
            ctx.setJsonResponse(new ExclusaoLeituraService().excluir(nuconf, nuleit, motivo, usuario));
            ctx.setStatus(ServiceContext.STATUS_OK);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao excluir leitura: " + e.getMessage(), e);
        }
    }

    /**
     * Historico de operacoes, somente leitura.
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void consultarHistorico(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirSupervisor();
        try {
            BigDecimal nuconf = ctx.getParameterAsBigDecimal("NUCONF");
            ctx.setJsonResponse(new ConsultaHistoricoConferenciaService().consultar(nuconf));
            ctx.setStatus(ServiceContext.STATUS_OK);
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage());
        }
    }

    /**
     * Consulta cega do resultado ou confirmacao transacional do encerramento fisico.
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Required"
     */
    public void finalizarConferencia(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirOperador();
        try {
            BigDecimal nuconf = ctx.getParameterAsBigDecimal("NUCONF");
            boolean confirmar = "S".equals(ctx.getParameterAsString("CONFIRMAR"));
            boolean divergente = "S".equals(ctx.getParameterAsString("DIVERGENTE"));
            BigDecimal usuario = br.com.sankhya.modelcore.auth.AuthenticationInfo.getCurrent().getUserID();
            ctx.setJsonResponse(new FinalizacaoConferenciaService().executar(nuconf, usuario, confirmar, divergente));
            ctx.setStatus(ServiceContext.STATUS_OK);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao finalizar conferencia: " + e.getMessage(), e);
        }
    }

    /**
     * Acoes administrativas restritas ao perfil Supervisor.
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Required"
     */
    public void decidirConferencia(ServiceContext ctx) throws Exception {
        PermissaoConferenciaService.exigirSupervisor();
        try {
            BigDecimal nuconf = ctx.getParameterAsBigDecimal("NUCONF");
            String acao = ctx.getParameterAsString("ACAO");
            String justificativa = corrigirUtf8InterpretadoComoWindows1252(ctx.getHttpRequest().getParameter("JUSTIFICATIVA"));
            BigDecimal usuario = br.com.sankhya.modelcore.auth.AuthenticationInfo.getCurrent().getUserID();
            ctx.setJsonResponse(new ValidacaoConferenciaService().executar(nuconf, usuario, acao, justificativa));
            ctx.setStatus(ServiceContext.STATUS_OK);
        } catch (Exception e) {
            throw new IllegalStateException("Falha na decisao administrativa: " + e.getMessage(), e);
        }
    }


    /**
     * Perfil da sessao autenticada; nunca recebe CODUSU do cliente.
     * @ejb.interface-method view-type="remote"
     * @ejb.transaction type="Supports"
     */
    public void consultarPerfilConferencia(ServiceContext ctx) throws Exception {
        try {
            String perfil = PermissaoConferenciaService.perfilAtual();
            com.google.gson.JsonObject out = new com.google.gson.JsonObject();
            out.addProperty("perfil", perfil);
            ctx.setJsonResponse(out);
            ctx.setStatus(ServiceContext.STATUS_OK);
        } catch (Exception e) {
            ctx.setStatus(ServiceContext.STATUS_ERROR);
            ctx.setStatusMessage(e.getMessage());
        }
    }

    /**
     * Corrige apenas a dupla interpretacao UTF-8 -> Windows-1252 que pode ocorrer
     * quando o HttpServiceBroker decodifica parametros da URL em charset legado.
     * Nao altera textos normais; evita uma conversao global dos dados do banco.
     */
    private static String corrigirUtf8InterpretadoComoWindows1252(String texto) {
        if (texto == null || texto.length() == 0) return texto;
        // U+00C3 e U+00C2 sao prefixos tipicos de UTF-8 lido como Windows-1252.
        if (texto.indexOf('\u00c3') < 0 && texto.indexOf('\u00c2') < 0) return texto;
        try {
            byte[] bytes = texto.getBytes(java.nio.charset.Charset.forName("windows-1252"));
            java.nio.charset.CharsetDecoder decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            String corrigido = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            // So substitui quando a sequencia efetivamente contem um prefixo
            // tipico de mojibake e a decodificacao UTF-8 foi valida.
            if (!corrigido.equals(texto) && corrigido.length() <= texto.length()) return corrigido;
        } catch (Exception ignorado) {
            // Texto ja correto ou nao recuperavel: preservar exatamente como chegou.
        }
        return texto;
    }

}
