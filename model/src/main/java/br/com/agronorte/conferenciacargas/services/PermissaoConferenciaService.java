package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import java.math.BigDecimal;
import java.sql.ResultSet;

/** Perfil operacional, independente da autenticacao nativa do Sankhya. */
public final class PermissaoConferenciaService {
    private PermissaoConferenciaService() { }

    public static String perfilAtual() throws Exception {
        AuthenticationInfo autenticacao = AuthenticationInfo.getCurrent();
        if (autenticacao == null || autenticacao.getUserID() == null)
            throw new Exception("Sessao de usuario nao identificada.");
        BigDecimal usuario = autenticacao.getUserID();
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
            sql.appendSql("SELECT PERFIL FROM AD_CONF_CARGA_PERM WHERE CODUSU=:U AND ATIVO='S'");
            sql.setNamedParameter("U", usuario);
            rs = sql.executeQuery();
            if (!rs.next()) throw new Exception("Usuario sem perfil ativo na Conferencia de Cargas.");
            String perfil = rs.getString(1);
            if (!"C".equals(perfil) && !"S".equals(perfil))
                throw new Exception("Perfil de conferencia invalido.");
            if (rs.next()) throw new Exception("Mais de um perfil encontrado para o usuario.");
            return perfil;
        } finally {
            if (rs != null) rs.close();
            if (sql != null) NativeSql.releaseResources(sql);
            if (jdbc != null) JdbcWrapper.closeSession(jdbc);
            if (handle != null) JapeSession.close(handle);
        }
    }

    public static void exigirOperador() throws Exception { perfilAtual(); }
    public static void exigirSupervisor() throws Exception {
        if (!"S".equals(perfilAtual()))
            throw new Exception("Operacao permitida somente ao perfil Supervisor.");
    }
}
