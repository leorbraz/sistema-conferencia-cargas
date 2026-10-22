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

/** Busca somente metadados da empresa e da conferencia; nao expõe previsao. */
public class BuscaConferenciaService {
    public JsonObject consultar(BigDecimal empresa, BigDecimal ordem, String filtro, boolean pesquisarEmpresas, String statusFila, String docaFila) throws Exception {
        JapeSession.SessionHandle handle = null;
        JdbcWrapper jdbc = null;
        NativeSql sql = null;
        ResultSet rs = null;
        try {
            handle = JapeSession.open();
            EntityFacade facade = JapeFactory.getEntityFacade();
            jdbc = facade.getJdbcWrapper();
            jdbc.openSession();
            JsonObject out = new JsonObject();
            if (pesquisarEmpresas) {
                JsonArray empresas = new JsonArray();
                sql = new NativeSql(jdbc);
                sql.appendSql("SELECT CODEMP, NOMEFANTASIA FROM TSIEMP WHERE UPPER(NOMEFANTASIA) LIKE :F OR TO_CHAR(CODEMP) LIKE :F ORDER BY CODEMP");
                sql.setNamedParameter("F", "%" + filtro.trim().toUpperCase() + "%");
                rs = sql.executeQuery();
                int count = 0;
                while (rs.next() && count++ < 50) {
                    JsonObject e = new JsonObject();
                    e.addProperty("codigo", rs.getBigDecimal(1));
                    e.addProperty("nome", rs.getString(2));
                    empresas.add(e);
                }
                out.add("empresas", empresas);
                return out;
            }
            if ("FILA".equalsIgnoreCase(filtro)) {
                JsonArray lista = new JsonArray();
                sql = new NativeSql(jdbc);
                // Inclui OCs históricas já conferidas e filtra no Oracle ANTES do limite.
                // Status é calculado pela última conferência de cada OC.
                String sf = statusFila == null ? "TODAS" : statusFila.trim().toUpperCase();
                String condStatus = "";
                if ("N".equals(sf)) condStatus = " AND C.NUCONF IS NULL ";
                else if ("FINALIZADAS".equals(sf)) condStatus = " AND C.STATUS IN ('O','D') ";
                else if ("VALIDADAS".equals(sf)) condStatus = " AND C.STATUS IN ('V','X') ";
                else if ("A".equals(sf) || "P".equals(sf) || "O".equals(sf) || "D".equals(sf) || "V".equals(sf) || "X".equals(sf)) condStatus = " AND C.STATUS='" + sf + "' ";
                // Doca é um campo adicional da OC; filtragem ocorre antes do limite de 500 registros.
                BigDecimal docaFiltro = null;
                if (docaFila != null && !docaFila.trim().isEmpty()) {
                    try { docaFiltro = new BigDecimal(docaFila.trim()); }
                    catch (NumberFormatException ex) { throw new Exception("Numero de doca invalido."); }
                }
                sql.appendSql("SELECT O.ORDEMCARGA, O.CODEMP, E.NOMEFANTASIA, C.NUCONF, C.STATUS, " +
                    "OC.AD_DOCA, P.NOMEPARC " +
                    "FROM (SELECT CODEMP, ORDEMCARGA FROM TGFORD WHERE ORDEMCARGA IS NOT NULL " +
                    "UNION SELECT CODEMP, ORDEMCARGA FROM AD_CONF_CARGA WHERE ORDEMCARGA IS NOT NULL) O " +
                    "JOIN TSIEMP E ON E.CODEMP=O.CODEMP " +
                    "LEFT JOIN TGFORD OC ON OC.CODEMP=O.CODEMP AND OC.ORDEMCARGA=O.ORDEMCARGA " +
                    "LEFT JOIN TGFPAR P ON P.CODPARC=OC.CODPARCTRANSP " +
                    "LEFT JOIN AD_CONF_CARGA C ON C.NUCONF=(SELECT MAX(C2.NUCONF) FROM AD_CONF_CARGA C2 " +
                    "WHERE C2.CODEMP=O.CODEMP AND C2.ORDEMCARGA=O.ORDEMCARGA) " +
                    "WHERE (:E=0 OR O.CODEMP=:E) AND (:OC=0 OR O.ORDEMCARGA=:OC) " +
                    (docaFiltro == null ? "" : " AND OC.AD_DOCA=:DOCA ") + condStatus +
                    "ORDER BY O.ORDEMCARGA DESC, O.CODEMP");
                sql.setNamedParameter("E", empresa == null ? BigDecimal.ZERO : empresa);
                sql.setNamedParameter("OC", ordem == null ? BigDecimal.ZERO : ordem);
                if (docaFiltro != null) sql.setNamedParameter("DOCA", docaFiltro);
                rs = sql.executeQuery();
                int count=0;
                while(rs.next() && count<500) {
                    count++;
                    JsonObject linha=new JsonObject();
                    linha.addProperty("ordem",rs.getBigDecimal(1));
                    linha.addProperty("empresa",rs.getBigDecimal(2));
                    linha.addProperty("nomeEmpresa",rs.getString(3));
                    if(rs.getBigDecimal(4)!=null) linha.addProperty("numeroConferencia",rs.getBigDecimal(4));
                    linha.addProperty("status",rs.getString(5)==null?"N":rs.getString(5));
                    if (rs.getObject(6) != null) linha.addProperty("doca", rs.getString(6));
                    if (rs.getString(7) != null) linha.addProperty("transportadora", rs.getString(7));
                    lista.add(linha);
                }
                out.add("ordens",lista);
                out.addProperty("limite",500);
                out.addProperty("limiteAtingido", count == 500);
                return out;
            }
            if (empresa == null || empresa.signum() <= 0) throw new Exception("Informe uma empresa valida.");
            sql = new NativeSql(jdbc);
            sql.appendSql("SELECT NOMEFANTASIA FROM TSIEMP WHERE CODEMP=:E");
            sql.setNamedParameter("E", empresa);
            rs = sql.executeQuery();
            if (!rs.next()) throw new Exception("Empresa nao encontrada.");
            out.addProperty("nomeEmpresa", rs.getString(1));
            rs.close(); rs = null;
            NativeSql.releaseResources(sql); sql = null;
            if (ordem == null) return out;
            if (ordem.signum() <= 0) throw new Exception("Informe uma ordem de carga valida.");
            sql = new NativeSql(jdbc);
            sql.appendSql("SELECT C.NUCONF, C.STATUS, O.AD_DOCA FROM AD_CONF_CARGA C " +
                "LEFT JOIN TGFORD O ON O.CODEMP=C.CODEMP AND O.ORDEMCARGA=C.ORDEMCARGA " +
                "WHERE C.CODEMP=:E AND C.ORDEMCARGA=:O ORDER BY C.NUCONF DESC");
            sql.setNamedParameter("E", empresa);
            sql.setNamedParameter("O", ordem);
            rs = sql.executeQuery();
            if (rs.next()) {
                out.addProperty("numeroConferencia", rs.getBigDecimal(1));
                out.addProperty("status", rs.getString(2));
                if (rs.getObject(3) != null) out.addProperty("doca", rs.getString(3));
            } else {
                out.addProperty("encontrada", false);
                rs.close(); rs = null;
                NativeSql.releaseResources(sql); sql = null;
                sql = new NativeSql(jdbc);
                sql.appendSql("SELECT AD_DOCA FROM TGFORD WHERE CODEMP=:E AND ORDEMCARGA=:O");
                sql.setNamedParameter("E", empresa);
                sql.setNamedParameter("O", ordem);
                rs = sql.executeQuery();
                if (rs.next() && rs.getObject(1) != null) out.addProperty("doca", rs.getString(1));
            }
            return out;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception ignored) { }
            if (sql != null) try { NativeSql.releaseResources(sql); } catch (Exception ignored) { }
            if (jdbc != null) JdbcWrapper.closeSession(jdbc);
            if (handle != null) JapeSession.close(handle);
        }
    }
}
