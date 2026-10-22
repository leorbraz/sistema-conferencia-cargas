package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.ResultSet;

/**
 * Congela a alocacao calculada pelo MESMO SQL FIFO da v1.0.10.
 * Nao devolve produtos, lotes ou quantidades ao navegador.
 * STATUS=A somente apos congelamento integral da previsao FIFO.
 * Erros impedem a liberacao e provocam rollback da transacao EJB.
 */
public class CongelamentoConferenciaService {
    private static final String DETALHE_SQL;
    static {
        String resumo = ComposicaoRomaneioService.SQL;
        String marcador = " SELECT (SELECT COUNT(*) FROM DEMANDA_BASE)";
        int pos = resumo.lastIndexOf(marcador);
        if (pos < 0) throw new IllegalStateException("SQL FIFO nao possui a estrutura esperada.");
        DETALHE_SQL = resumo.substring(0, pos) +
            " SELECT CODPROD, LOTE, CODVOL, SUM(QTD) AS QTD_PREV " +
            " FROM ALOCACAO WHERE QTD > 0 " +
            " GROUP BY CODPROD, LOTE, CODVOL ORDER BY CODPROD, LOTE, CODVOL";
    }

    public JsonObject iniciar(BigDecimal empresa, BigDecimal ordem, BigDecimal usuario) throws Exception {
        if (empresa == null || ordem == null || usuario == null ||
            empresa.signum() <= 0 || ordem.signum() <= 0 || usuario.signum() < 0 ||
            empresa.stripTrailingZeros().scale() > 0 || ordem.stripTrailingZeros().scale() > 0)
            throw new Exception("Empresa, OC ou usuario invalido.");
        JapeSession.SessionHandle h = null;
        JdbcWrapper jdbc = null;
        NativeSql sql = null;
        ResultSet rs = null;
        try {
            h = JapeSession.open();
            EntityFacade facade = JapeFactory.getEntityFacade();
            jdbc = facade.getJdbcWrapper();
            jdbc.openSession();

            // Bloqueia o cabecalho para serializar a numeracao MAX+1.
            // O metodo EJB deve permanecer com transacao Required.
            // Isto substitui a sequencia Oracle do script manual.
            NativeSql bloqueio = new NativeSql(jdbc);
            try {
                bloqueio.appendSql("LOCK TABLE AD_CONF_CARGA IN EXCLUSIVE MODE");
                bloqueio.executeUpdate();
            } finally { NativeSql.releaseResources(bloqueio); }

            // Serializa criacoes da mesma OC; requer transacao EJB Required.
            sql = new NativeSql(jdbc);
            sql.appendSql("SELECT ORDEMCARGA, SITUACAO FROM TGFORD WHERE CODEMP=:E AND ORDEMCARGA=:O FOR UPDATE");
            sql.setNamedParameter("E", empresa); sql.setNamedParameter("O", ordem);
            rs = sql.executeQuery();
            if (!rs.next()) throw new Exception("Ordem de carga nao localizada.");
            String situacaoOc = rs.getString("SITUACAO");
            if (!"A".equals(situacaoOc == null ? null : situacaoOc.trim()))
                throw new Exception("Não é possível iniciar a conferência. A Ordem de Carga deve estar com situação = Aberta.");
            close(rs, sql); rs = null; sql = null;

            sql = new NativeSql(jdbc);
            sql.appendSql("SELECT NUCONF, STATUS FROM AD_CONF_CARGA WHERE CODEMP=:E AND ORDEMCARGA=:O AND STATUS IN ('P','A')");
            sql.setNamedParameter("E", empresa); sql.setNamedParameter("O", ordem);
            rs = sql.executeQuery();
            if (rs.next()) throw new Exception("Ja existe conferencia pendente ou ativa para esta OC (numero " + rs.getBigDecimal(1) + ").");
            close(rs, sql); rs = null; sql = null;

            // Nao aceita OC sem pedidos. O resumo pode ter demanda de outros TIPMOV;
            // a equivalencia exata ao romaneio impresso requer revisao administrativa.
            JsonObject oc = new OrdemCargaService().consultar(empresa, ordem);
            if (!oc.get("encontrada").getAsBoolean() || oc.get("quantidadePedidos").getAsInt() <= 0)
                throw new Exception("OC sem pedidos validos para iniciar conferencia.");
            JsonObject resumo = new ComposicaoRomaneioService().consultar(empresa, ordem);
            if (!resumo.get("alocacaoCompleta").getAsBoolean())
                throw new Exception("Alocacao FIFO incompleta ou unidade mista. Revise o romaneio.");

            sql = new NativeSql(jdbc);
            sql.appendSql("SELECT NVL(MAX(NUCONF),0)+1 AS NUCONF FROM AD_CONF_CARGA");
            rs = sql.executeQuery();
            if (!rs.next()) throw new Exception("Nao foi possivel gerar numero da conferencia.");
            BigDecimal nuconf = rs.getBigDecimal(1);
            close(rs, sql); rs = null; sql = null;

            sql = new NativeSql(jdbc);
            sql.appendSql("INSERT INTO AD_CONF_CARGA (NUCONF,CODEMP,ORDEMCARGA,STATUS,CODUSUABERT,DHABERT) " +
                "VALUES (:N,:E,:O,'P',:U,SYSDATE)");
            sql.setNamedParameter("N",nuconf); sql.setNamedParameter("E",empresa);
            sql.setNamedParameter("O",ordem); sql.setNamedParameter("U",usuario);
            if (!sql.executeUpdate()) throw new Exception("Falha ao criar cabecalho da conferencia.");
            NativeSql.releaseResources(sql); sql = null;

            BigDecimal proximoPrev;
            NativeSql seqPrev = new NativeSql(jdbc);
            ResultSet rsPrev = null;
            try {
                seqPrev.appendSql("SELECT NVL(MAX(NUPREV),0)+1 FROM AD_CONF_CARGA_PREV");
                rsPrev = seqPrev.executeQuery();
                if (!rsPrev.next()) throw new Exception("Falha ao numerar a previsao.");
                proximoPrev = rsPrev.getBigDecimal(1);
            } finally {
                if (rsPrev != null) rsPrev.close();
                NativeSql.releaseResources(seqPrev);
            }

            sql = new NativeSql(jdbc);
            sql.appendSql(DETALHE_SQL);
            sql.setNamedParameter("CODEMP",empresa);
            sql.setNamedParameter("ORDEMCARGA",ordem);
            rs = sql.executeQuery();
            int grupos = 0;
            while (rs.next()) {
                BigDecimal produto = rs.getBigDecimal("CODPROD");
                String lote = rs.getString("LOTE");
                String unidade = rs.getString("CODVOL");
                BigDecimal qtd = rs.getBigDecimal("QTD_PREV");
                if (produto == null || lote == null || lote.trim().isEmpty() ||
                    unidade == null || unidade.trim().isEmpty() || qtd == null || qtd.signum() <= 0)
                    throw new Exception("Previsao FIFO possui produto, lote, unidade ou quantidade invalida.");
                NativeSql insert = null;
                try {
                    insert = new NativeSql(jdbc);
                    insert.appendSql("INSERT INTO AD_CONF_CARGA_PREV (NUPREV,NUCONF,CODPROD,CONTROLE,CODVOL,QTD_PREV) " +
                        "VALUES (:I,:N,:P,:L,:V,:Q)");
                    insert.setNamedParameter("I",proximoPrev);
                    insert.setNamedParameter("N",nuconf); insert.setNamedParameter("P",produto);
                    insert.setNamedParameter("L",lote); insert.setNamedParameter("V",unidade);
                    insert.setNamedParameter("Q",qtd);
                    if (!insert.executeUpdate()) throw new Exception("Falha ao salvar previsao FIFO.");
                } finally { if (insert != null) NativeSql.releaseResources(insert); }
                grupos++;
                proximoPrev = proximoPrev.add(BigDecimal.ONE);
            }
            close(rs,sql); rs = null; sql = null;
            if (grupos == 0) throw new Exception("Nenhum grupo de produto/lote foi alocado.");
            // Libera somente depois que todos os grupos foram gravados com sucesso.
            // Esta atualizacao participa da mesma transacao EJB Required.
            sql = new NativeSql(jdbc);
            sql.appendSql("UPDATE AD_CONF_CARGA SET STATUS='A' WHERE NUCONF=:N AND STATUS='P'");
            sql.setNamedParameter("N", nuconf);
            if (!sql.executeUpdate()) throw new Exception("Falha ao liberar conferencia apos congelamento.");
            NativeSql.releaseResources(sql); sql = null;
            AuditoriaAcaoService.registrar(jdbc, nuconf, "INICIAR", "P", "A", usuario, null);
            JsonObject out = new JsonObject();
            out.addProperty("numeroConferencia", nuconf);
            out.addProperty("situacao", "EM_ANDAMENTO");
            out.addProperty("mensagem", "Previsao congelada. Conferencia liberada para leitura.");
            return out;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception ignored) { }
            if (sql != null) try { NativeSql.releaseResources(sql); } catch (Exception ignored) { }
            if (jdbc != null) try { JdbcWrapper.closeSession(jdbc); } catch (Exception ignored) { }
            if (h != null) JapeSession.close(h);
        }
    }

    private static void close(ResultSet rs, NativeSql sql) throws Exception {
        if (rs != null) rs.close();
        if (sql != null) NativeSql.releaseResources(sql);
    }
}
