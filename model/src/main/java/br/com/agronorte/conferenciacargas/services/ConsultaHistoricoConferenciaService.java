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

/** Consulta cronologica e somente leitura das operacoes. */
public class ConsultaHistoricoConferenciaService {
    public JsonObject consultar(BigDecimal nuconf) throws Exception {
        if (nuconf == null || nuconf.signum() <= 0 || nuconf.stripTrailingZeros().scale() > 0)
            throw new Exception("Numero da conferencia invalido.");
        JapeSession.SessionHandle h = null;
        JdbcWrapper jdbc = null;
        try {
            h = JapeSession.open();
            EntityFacade facade = JapeFactory.getEntityFacade();
            jdbc = facade.getJdbcWrapper(); jdbc.openSession();
            NativeSql sql = new NativeSql(jdbc);
            ResultSet rs = null;
            try {
                sql.appendSql("SELECT DATA_EVENTO,CODUSU,ACAO,DETALHES FROM (" +
                    "SELECT DHACAO DATA_EVENTO,CODUSU,ACAO, " +
                    "SUBSTR('Status: '||(" +
                    "CASE STATUS_ANT WHEN 'P' THEN 'Preparação' WHEN 'A' THEN 'Em Conferência' WHEN 'O' THEN 'Finalizada OK' WHEN 'D' THEN 'Finalizada Divergente' WHEN 'V' THEN 'Validada' WHEN 'X' THEN 'Validada Divergente' ELSE NVL(STATUS_ANT,'Não informado') END" +
                    ")||' -> '||(" +
                    "CASE STATUS_NOVO WHEN 'P' THEN 'Preparação' WHEN 'A' THEN 'Em Conferência' WHEN 'O' THEN 'Finalizada OK' WHEN 'D' THEN 'Finalizada Divergente' WHEN 'V' THEN 'Validada' WHEN 'X' THEN 'Validada Divergente' ELSE NVL(STATUS_NOVO,'Não informado') END" +
                    ")||" +
                    "CASE WHEN JUSTIFICATIVA IS NOT NULL THEN ' | '||JUSTIFICATIVA ELSE '' END,1,1000) DETALHES " +
                    "FROM AD_CONF_CARGA_ACAO WHERE NUCONF=:N " +
                    "UNION ALL SELECT DHEVENTO DATA_EVENTO,CODUSU,ACAO, " +
                    "SUBSTR('Leitura '||NULEIT||' | Produto '||CODPROD||' | Lote '||CONTROLE||" +
                    "' | Unidade '||CODVOL||' | Qtd '||QTD||" +
                    "CASE WHEN MOTIVO IS NOT NULL THEN ' | '||MOTIVO ELSE '' END,1,1000) DETALHES " +
                    "FROM AD_CONF_CARGA_HIST WHERE NUCONF=:N) ORDER BY DATA_EVENTO DESC");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                JsonArray eventos = new JsonArray();
                while (rs.next()) {
                    JsonObject item = new JsonObject();
                    java.sql.Timestamp data = rs.getTimestamp(1);
                    item.addProperty("data", data == null ? "" : new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(data));
                    item.addProperty("usuario", rs.getBigDecimal(2));
                    item.addProperty("acao", rs.getString(3));
                    item.addProperty("detalhes", rs.getString(4));
                    eventos.add(item);
                }
                JsonObject out = new JsonObject(); out.add("eventos", eventos); return out;
            } finally { if (rs != null) rs.close(); NativeSql.releaseResources(sql); }
        } finally { if (jdbc != null) JdbcWrapper.closeSession(jdbc); if (h != null) JapeSession.close(h); }
    }
}
