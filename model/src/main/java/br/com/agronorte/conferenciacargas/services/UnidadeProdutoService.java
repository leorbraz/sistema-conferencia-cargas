package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import com.google.gson.JsonObject;
import java.sql.ResultSet;

/** Unidade padrao TGFPRO.CODVOL para o codigo principal TGFPRO.REFERENCIA. */
public class UnidadeProdutoService {
    public JsonObject consultar(String barra) throws Exception {
        if (barra == null || barra.trim().isEmpty() || barra.length() > 100)
            throw new Exception("Informe um codigo de barras valido.");
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
            sql.appendSql("SELECT CODVOL FROM TGFPRO WHERE REFERENCIA=:B AND USOPROD='V' AND ATIVO='S'");
            sql.setNamedParameter("B", barra.trim());
            rs = sql.executeQuery();
            if (!rs.next()) throw new Exception("Produto ativo nao encontrado para o codigo de barras.");
            String unidade = rs.getString(1);
            if (rs.next()) throw new Exception("Codigo de barras ambiguo: mais de um produto.");
            if (unidade == null || unidade.trim().isEmpty())
                throw new Exception("Produto sem unidade padrao (CODVOL) cadastrada.");
            JsonObject out = new JsonObject();
            out.addProperty("codvol", unidade.trim());
            return out;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception ignored) { }
            if (sql != null) NativeSql.releaseResources(sql);
            if (jdbc != null) try { jdbc.closeSession(); } catch (Exception ignored) { }
            if (handle != null) JapeSession.close(handle);
        }
    }
}
