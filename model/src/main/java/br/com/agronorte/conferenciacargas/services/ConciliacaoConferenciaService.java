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

/** Resultado administrativo, comparando CODPROD + CONTROLE + CODVOL. */
public class ConciliacaoConferenciaService {
    public JsonObject consultar(BigDecimal nuconf) throws Exception {
        if (nuconf == null || nuconf.signum() <= 0 || nuconf.stripTrailingZeros().scale() > 0)
            throw new Exception("Numero da conferencia invalido.");
        JapeSession.SessionHandle handle = null;
        JdbcWrapper jdbc = null;
        NativeSql sql = null;
        ResultSet rs = null;
        try {
            handle = JapeSession.open();
            EntityFacade facade = JapeFactory.getEntityFacade();
            jdbc = facade.getJdbcWrapper();
            jdbc.openSession();
            sql = new NativeSql(jdbc);
            sql.appendSql("SELECT STATUS FROM AD_CONF_CARGA WHERE NUCONF=:N");
            sql.setNamedParameter("N", nuconf);
            rs = sql.executeQuery();
            if (!rs.next()) throw new Exception("Conferencia nao localizada.");
            String status = rs.getString(1);
            rs.close(); rs = null; NativeSql.releaseResources(sql); sql = null;
            if (!"A".equals(status) && !"P".equals(status) && !"O".equals(status) && !"D".equals(status) && !"V".equals(status) && !"X".equals(status))
                throw new Exception("Conferencia nao esta pendente ou em andamento.");
            sql = new NativeSql(jdbc);
            sql.appendSql(
                "WITH PREV AS (SELECT CODPROD,CONTROLE,CODVOL,SUM(QTD_PREV) QTD_PREV " +
                "FROM AD_CONF_CARGA_PREV WHERE NUCONF=:N GROUP BY CODPROD,CONTROLE,CODVOL), " +
                "LEIT AS (SELECT CODPROD,CONTROLE,CODVOL,SUM(QTD) QTD_LIDA " +
                "FROM AD_CONF_CARGA_LEIT WHERE NUCONF=:N AND NVL(STATUS,'A')='A' " +
                "GROUP BY CODPROD,CONTROLE,CODVOL) " +
                "SELECT COALESCE(P.CODPROD,L.CODPROD) CODPROD,T.DESCRPROD,T.REFERENCIA, " +
                "COALESCE(P.CONTROLE,L.CONTROLE) CONTROLE,COALESCE(P.CODVOL,L.CODVOL) CODVOL, " +
                "NVL(P.QTD_PREV,0) QTD_PREV,NVL(L.QTD_LIDA,0) QTD_LIDA " +
                "FROM PREV P FULL OUTER JOIN LEIT L ON P.CODPROD=L.CODPROD " +
                "AND P.CONTROLE=L.CONTROLE AND P.CODVOL=L.CODVOL " +
                "LEFT JOIN TGFPRO T ON T.CODPROD=COALESCE(P.CODPROD,L.CODPROD) " +
                "ORDER BY COALESCE(P.CODPROD,L.CODPROD),COALESCE(P.CONTROLE,L.CONTROLE), " +
                "COALESCE(P.CODVOL,L.CODVOL)");
            sql.setNamedParameter("N", nuconf);
            rs = sql.executeQuery();
            JsonArray itens = new JsonArray();
            int conformes=0, faltas=0, sobras=0, naoConferidos=0, naoPrevistos=0;
            while (rs.next()) {
                BigDecimal previsto=rs.getBigDecimal(6), lido=rs.getBigDecimal(7);
                BigDecimal diferenca=lido.subtract(previsto);
                String situacao;
                if (previsto.signum()==0) { situacao="NAO PREVISTO"; naoPrevistos++; }
                else if (lido.signum()==0) { situacao="NAO CONFERIDO"; naoConferidos++; }
                else if (diferenca.signum()==0) { situacao="CONFORME"; conformes++; }
                else if (diferenca.signum()<0) { situacao="FALTA"; faltas++; }
                else { situacao="SOBRA"; sobras++; }
                JsonObject item=new JsonObject();
                item.addProperty("codprod",rs.getBigDecimal(1));
                item.addProperty("descricao",rs.getString(2));
                item.addProperty("referencia",rs.getString(3));
                item.addProperty("lote",rs.getString(4));
                item.addProperty("unidade",rs.getString(5));
                item.addProperty("previsto",previsto);
                item.addProperty("lido",lido);
                item.addProperty("diferenca",diferenca);
                item.addProperty("situacao",situacao);
                itens.add(item);
            }
            JsonObject out=new JsonObject();
            out.add("itens",itens);
            out.addProperty("conformes",conformes);out.addProperty("faltas",faltas);
            out.addProperty("sobras",sobras);out.addProperty("naoConferidos",naoConferidos);
            out.addProperty("naoPrevistos",naoPrevistos);
            out.addProperty("total",itens.size());
            return out;
        } finally {
            if (rs!=null) try {rs.close();} catch(Exception ignored) {}
            if (sql!=null) NativeSql.releaseResources(sql);
            if (jdbc!=null) JdbcWrapper.closeSession(jdbc);
            if (handle!=null) JapeSession.close(handle);
        }
    }
}
