package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.ResultSet;

/** Valida o numero da etiqueta com o campo adicional TGFORD.AD_DOCA. */
public class ValidacaoDocaService {
    public JsonObject validar(BigDecimal empresa, BigDecimal ordem, String docaLida) throws Exception {
        if (empresa == null || empresa.signum() <= 0 || ordem == null || ordem.signum() <= 0)
            throw new Exception("Empresa e ordem de carga obrigatorias.");
        if (docaLida == null || !docaLida.trim().matches("[0-9]+"))
            throw new Exception("Etiqueta da doca invalida. Bipe somente o numero.");
        BigDecimal numeroLido = new BigDecimal(docaLida.trim());
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
            sql.appendSql("SELECT AD_DOCA FROM TGFORD WHERE CODEMP=:E AND ORDEMCARGA=:O");
            sql.setNamedParameter("E", empresa);
            sql.setNamedParameter("O", ordem);
            rs = sql.executeQuery();
            if (!rs.next()) throw new Exception("Ordem de carga nao encontrada.");
            BigDecimal docaCadastrada = rs.getBigDecimal(1);
            JsonObject out = new JsonObject();
            boolean correta = docaCadastrada != null && docaCadastrada.compareTo(numeroLido) == 0;
            out.addProperty("validada", correta);
            out.addProperty("mensagem", correta ? "Doca validada." :
                docaCadastrada == null ? "A OC nao possui doca cadastrada." : "Doca divergente. Confira a etiqueta da doca fisica.");
            return out;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception ignored) { }
            if (sql != null) NativeSql.releaseResources(sql);
            if (jdbc != null) try { jdbc.closeSession(); } catch (Exception ignored) { }
            if (handle != null) JapeSession.close(handle);
        }
    }
}
