package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.ResultSet;

/** Consulta parcialmente cega: nao devolve lotes nem quantidades previstos. */
public class ConsultaConferenteService {
    public JsonObject consultar(BigDecimal nuconf) throws Exception {
        if (nuconf == null || nuconf.signum() <= 0 || nuconf.stripTrailingZeros().scale() > 0)
            throw new Exception("Numero da conferencia invalido.");
        JapeSession.SessionHandle handle = null;
        JdbcWrapper jdbc = null;
        try {
            handle = JapeSession.open();
            EntityFacade facade = JapeFactory.getEntityFacade();
            jdbc = facade.getJdbcWrapper();
            jdbc.openSession();
            JsonObject out = new JsonObject();
            NativeSql sql = new NativeSql(jdbc);
            ResultSet rs = null;
            try {
                sql.appendSql("SELECT STATUS FROM AD_CONF_CARGA WHERE NUCONF=:N");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                if (!rs.next()) throw new Exception("Conferencia nao encontrada.");
                String status = rs.getString(1);
                if (!"A".equals(status) && !"O".equals(status) && !"D".equals(status)) throw new Exception("Conferencia nao disponivel para consulta.");
                out.addProperty("numeroConferencia", nuconf);
                out.addProperty("status", status);
            } finally { close(rs, sql); }
            JsonArray produtos = new JsonArray();
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("SELECT P.CODPROD, MAX(T.DESCRPROD), MAX(T.REFERENCIA), " +
                              "CASE WHEN EXISTS (SELECT 1 FROM AD_CONF_CARGA_LEIT L " +
                              "WHERE L.NUCONF=:N AND L.CODPROD=P.CODPROD AND NVL(L.STATUS,'A')='A') THEN 1 ELSE 0 END " +
                              "FROM AD_CONF_CARGA_PREV P JOIN TGFPRO T ON T.CODPROD=P.CODPROD " +
                              "WHERE P.NUCONF=:N GROUP BY P.CODPROD ORDER BY MAX(T.DESCRPROD),P.CODPROD");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                while (rs.next()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("codprod", rs.getBigDecimal(1));
                    item.addProperty("descricao", rs.getString(2));
                    item.addProperty("referencia", rs.getString(3));
                    item.addProperty("comLeitura", rs.getInt(4) == 1);
                    produtos.add(item);
                }
            } finally { close(rs, sql); }
            out.add("produtos", produtos);
            JsonArray leituras = new JsonArray();
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("SELECT L.CODPROD, MAX(T.DESCRPROD), L.CONTROLE, L.CODVOL, SUM(L.QTD), COUNT(*) " +
                              "FROM AD_CONF_CARGA_LEIT L JOIN TGFPRO T ON T.CODPROD=L.CODPROD " +
                              "WHERE L.NUCONF=:N AND NVL(L.STATUS,'A')='A' GROUP BY L.CODPROD,L.CONTROLE,L.CODVOL " +
                              "ORDER BY MAX(T.DESCRPROD),L.CONTROLE,L.CODVOL");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                while (rs.next()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("codprod", rs.getBigDecimal(1));
                    item.addProperty("descricao", rs.getString(2));
                    item.addProperty("lote", rs.getString(3));
                    item.addProperty("unidade", rs.getString(4));
                    item.addProperty("quantidade", rs.getBigDecimal(5));
                    item.addProperty("registros", rs.getInt(6));
                    leituras.add(item);
                }
            } finally { close(rs, sql); }
            out.add("leituras", leituras);
            return out;
        } finally {
            if (jdbc != null) JdbcWrapper.closeSession(jdbc);
            if (handle != null) JapeSession.close(handle);
        }
    }
    private static void close(ResultSet rs, NativeSql sql) throws Exception {
        if (rs != null) rs.close();
        if (sql != null) NativeSql.releaseResources(sql);
    }
}
