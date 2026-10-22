package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.ResultSet;

/** Consulta exclusivamente de leitura. Nao devolve itens, lotes ou quantidades. */
public class OrdemCargaService {
    public JsonObject consultar(BigDecimal empresa, BigDecimal ordem) throws Exception {
        if (empresa == null || ordem == null || empresa.signum() <= 0 || ordem.signum() <= 0
                || empresa.stripTrailingZeros().scale() > 0 || ordem.stripTrailingZeros().scale() > 0) {
            throw new Exception("Informe empresa e ordem de carga com numeros inteiros positivos.");
        }

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
            sql.appendSql("SELECT COUNT(*) AS TOTAL FROM TGFORD WHERE CODEMP = :CODEMP AND ORDEMCARGA = :ORDEMCARGA");
            sql.setNamedParameter("CODEMP", empresa);
            sql.setNamedParameter("ORDEMCARGA", ordem);
            rs = sql.executeQuery();
            boolean encontrada = rs.next() && rs.getInt("TOTAL") > 0;
            rs.close(); rs = null;
            NativeSql.releaseResources(sql); sql = null;

            JsonObject out = new JsonObject();
            out.addProperty("empresa", empresa);
            out.addProperty("ordemCarga", ordem);
            out.addProperty("encontrada", encontrada);
            if (!encontrada) {
                out.addProperty("quantidadePedidos", 0);
                out.addProperty("situacao", "NAO_ENCONTRADA");
                out.addProperty("mensagem", "Ordem de carga nao encontrada para esta empresa.");
                return out;
            }

            sql = new NativeSql(jdbc);
            sql.appendSql("SELECT COUNT(DISTINCT CAB.NUNOTA) AS TOTAL " +
                    "FROM TGFCAB CAB WHERE CAB.CODEMP = :CODEMP " +
                    "AND CAB.ORDEMCARGA = :ORDEMCARGA AND CAB.TIPMOV = 'P'");
            sql.setNamedParameter("CODEMP", empresa);
            sql.setNamedParameter("ORDEMCARGA", ordem);
            rs = sql.executeQuery();
            int pedidos = rs.next() ? rs.getInt("TOTAL") : 0;
            out.addProperty("quantidadePedidos", pedidos);
            out.addProperty("situacao", pedidos > 0 ? "LOCALIZADA" : "SEM_PEDIDOS");
            out.addProperty("mensagem", pedidos > 0
                    ? "Ordem de carga localizada. Consulta somente leitura."
                    : "Ordem de carga localizada, mas sem pedidos TIPMOV=P vinculados.");
            return out;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception ignored) { }
            if (sql != null) try { NativeSql.releaseResources(sql); } catch (Exception ignored) { }
            if (jdbc != null) try { jdbc.closeSession(); } catch (Exception ignored) { }
            if (handle != null) JapeSession.close(handle);
        }
    }
}
