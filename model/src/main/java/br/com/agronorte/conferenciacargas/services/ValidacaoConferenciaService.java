package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.ResultSet;

/** Decisao administrativa transacional. Habilitar em producao somente apos controle de acesso por CODUSU. */
public class ValidacaoConferenciaService {
    public JsonObject executar(BigDecimal nuconf, BigDecimal usuario, String acao, String justificativa) throws Exception {
        if (nuconf == null || nuconf.signum() <= 0 || nuconf.stripTrailingZeros().scale() > 0 || usuario == null)
            throw new Exception("Conferencia ou usuario invalido.");
        if (!"VALIDAR".equals(acao) && !"VALIDAR_DIVERGENTE".equals(acao) && !"RECONFERIR".equals(acao))
            throw new Exception("Acao administrativa invalida.");
        String motivo = justificativa == null ? "" : justificativa.trim();
        if (motivo.length() > 500) throw new Exception("Justificativa excede 500 caracteres.");
        if (("VALIDAR_DIVERGENTE".equals(acao) || "RECONFERIR".equals(acao)) && motivo.length() < 5)
            throw new Exception("Informe uma justificativa com pelo menos 5 caracteres.");
        JapeSession.SessionHandle h = null;
        JdbcWrapper jdbc = null;
        try {
            h = JapeSession.open();
            EntityFacade facade = JapeFactory.getEntityFacade();
            jdbc = facade.getJdbcWrapper();
            jdbc.openSession();
            String anterior;
            NativeSql sql = new NativeSql(jdbc);
            ResultSet rs = null;
            try {
                sql.appendSql("SELECT STATUS FROM AD_CONF_CARGA WHERE NUCONF=:N FOR UPDATE");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                if (!rs.next()) throw new Exception("Conferencia nao localizada.");
                anterior = rs.getString(1);
            } finally { close(rs, sql); }
            if (!"O".equals(anterior) && !"D".equals(anterior))
                throw new Exception("Somente conferencias finalizadas OK ou divergentes podem ser analisadas.");
            if ("VALIDAR".equals(acao) && !"O".equals(anterior))
                throw new Exception("Conferencia divergente exige aprovacao com justificativa.");
            if ("VALIDAR_DIVERGENTE".equals(acao) && !"D".equals(anterior))
                throw new Exception("Esta conferencia nao esta divergente.");
            // Arquiva somente leituras de produtos divergentes; as demais continuam validas.
            // O agrupamento por produto inclui divergencias de lote e unidade.
            if ("RECONFERIR".equals(acao)) {
                if (!"D".equals(anterior))
                    throw new Exception("Reconferencia direcionada exige conferencia divergente.");
                sql = new NativeSql(jdbc);
                try {
                    sql.appendSql("UPDATE AD_CONF_CARGA_LEIT SET STATUS='R' " +
                        "WHERE NUCONF=:N AND NVL(STATUS,'A')='A' AND CODPROD IN (" +
                        "WITH P AS (SELECT CODPROD,CONTROLE,CODVOL,SUM(QTD_PREV) Q " +
                        "FROM AD_CONF_CARGA_PREV WHERE NUCONF=:N GROUP BY CODPROD,CONTROLE,CODVOL), " +
                        "L AS (SELECT CODPROD,CONTROLE,CODVOL,SUM(QTD) Q " +
                        "FROM AD_CONF_CARGA_LEIT WHERE NUCONF=:N AND NVL(STATUS,'A')='A' " +
                        "GROUP BY CODPROD,CONTROLE,CODVOL) " +
                        "SELECT DISTINCT COALESCE(P.CODPROD,L.CODPROD) " +
                        "FROM P FULL OUTER JOIN L ON P.CODPROD=L.CODPROD " +
                        "AND P.CONTROLE=L.CONTROLE AND P.CODVOL=L.CODVOL " +
                        "WHERE NVL(P.Q,0)<>NVL(L.Q,0))");
                    sql.setNamedParameter("N", nuconf);
                    sql.executeUpdate();
                } finally { NativeSql.releaseResources(sql); }
            }
            // Reconferencia retorna a A, sem excluir fisicamente leituras anteriores.
            String novo = "RECONFERIR".equals(acao) ? "A" : ("VALIDAR".equals(acao) ? "V" : "X");
            sql = new NativeSql(jdbc);
            try {
                sql.appendSql("UPDATE AD_CONF_CARGA SET STATUS=:S WHERE NUCONF=:N AND STATUS=:ANT");
                sql.setNamedParameter("S", novo);
                sql.setNamedParameter("N", nuconf);
                sql.setNamedParameter("ANT", anterior);
                if (!sql.executeUpdate()) throw new Exception("Falha ao atualizar status.");
            } finally { NativeSql.releaseResources(sql); }
            // A tabela e criada pelo autoDDL. Para evitar depender de uma sequence Oracle
            // manual, serializamos a numeracao do historico sob lock exclusivo.
            // As decisoes administrativas sao raras; o lock dura apenas a transacao.
            sql = new NativeSql(jdbc);
            try {
                sql.appendSql("LOCK TABLE AD_CONF_CARGA_ACAO IN EXCLUSIVE MODE");
                sql.executeUpdate();
            } finally { NativeSql.releaseResources(sql); }
            BigDecimal proximaAcao;
            sql = new NativeSql(jdbc);
            rs = null;
            try {
                sql.appendSql("SELECT NVL(MAX(NUACAO),0)+1 FROM AD_CONF_CARGA_ACAO");
                rs = sql.executeQuery();
                if (!rs.next()) throw new Exception("Falha ao gerar identificador da acao.");
                proximaAcao = rs.getBigDecimal(1);
            } finally { close(rs, sql); }
            sql = new NativeSql(jdbc);
            try {
                sql.appendSql("INSERT INTO AD_CONF_CARGA_ACAO (NUACAO,NUCONF,ACAO,STATUS_ANT,STATUS_NOVO,CODUSU,DHACAO,JUSTIFICATIVA) " +
                    "VALUES (:ID,:N,:A,:ANT,:NOVO,:U,SYSDATE,:J)");
                sql.setNamedParameter("ID", proximaAcao);
                sql.setNamedParameter("N", nuconf);
                sql.setNamedParameter("A", acao);
                sql.setNamedParameter("ANT", anterior);
                sql.setNamedParameter("NOVO", novo);
                sql.setNamedParameter("U", usuario);
                sql.setNamedParameter("J", motivo.length() == 0 ? null : motivo);
                if (!sql.executeUpdate()) throw new Exception("Falha ao registrar historico.");
            } finally { NativeSql.releaseResources(sql); }
            JsonObject out = new JsonObject();
            out.addProperty("status", novo);
            out.addProperty("acao", acao);
            out.addProperty("mensagem", "RECONFERIR".equals(acao) ? "Reconferencia direcionada liberada. Leituras anteriores preservadas." : "Conferencia validada.");
            return out;
        } finally {
            if (jdbc != null) JdbcWrapper.closeSession(jdbc);
            if (h != null) JapeSession.close(h);
        }
    }
    private static void close(ResultSet rs, NativeSql sql) throws Exception {
        if (rs != null) rs.close();
        if (sql != null) NativeSql.releaseResources(sql);
    }
}
