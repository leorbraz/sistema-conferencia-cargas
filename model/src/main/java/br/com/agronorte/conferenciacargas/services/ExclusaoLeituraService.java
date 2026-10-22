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

public class ExclusaoLeituraService {
    private static void validar(BigDecimal nuconf) throws Exception {
        if (nuconf == null || nuconf.signum() <= 0 || nuconf.stripTrailingZeros().scale() > 0)
            throw new Exception("Numero da conferencia invalido.");
    }
    public JsonObject listar(BigDecimal nuconf, BigDecimal produto, String lote, String unidade) throws Exception {
        validar(nuconf);
        if (produto == null || produto.signum() <= 0 || lote == null || unidade == null)
            throw new Exception("Informe produto, lote e unidade.");
        JapeSession.SessionHandle h = null; JdbcWrapper jdbc = null;
        try {
            h = JapeSession.open();
            EntityFacade facade = JapeFactory.getEntityFacade();
            jdbc = facade.getJdbcWrapper(); jdbc.openSession();
            NativeSql sql = new NativeSql(jdbc); ResultSet rs = null;
            try {
                sql.appendSql("SELECT L.NULEIT,L.QTD,L.CODUSU,L.DHLEITURA " +
                    "FROM AD_CONF_CARGA_LEIT L JOIN AD_CONF_CARGA C ON C.NUCONF=L.NUCONF " +
                    "WHERE L.NUCONF=:N AND L.CODPROD=:P AND L.CONTROLE=:L AND L.CODVOL=:V " +
                    "AND NVL(L.STATUS,'A')='A' AND C.STATUS='A' ORDER BY L.DHLEITURA,L.NULEIT");
                sql.setNamedParameter("N",nuconf);sql.setNamedParameter("P",produto);
                sql.setNamedParameter("L",lote);sql.setNamedParameter("V",unidade);
                rs=sql.executeQuery(); JsonArray itens=new JsonArray();
                while(rs.next()) {
                    JsonObject item=new JsonObject();
                    item.addProperty("nuleit",rs.getBigDecimal(1));
                    item.addProperty("quantidade",rs.getBigDecimal(2));
                    item.addProperty("usuario",rs.getBigDecimal(3));
                    item.addProperty("data",rs.getTimestamp(4)==null?"":rs.getTimestamp(4).toString());
                    itens.add(item);
                }
                JsonObject out=new JsonObject();out.add("leituras",itens);return out;
            } finally {close(rs,sql);}
        } finally {if(jdbc!=null)JdbcWrapper.closeSession(jdbc);if(h!=null)JapeSession.close(h);}
    }
    public JsonObject excluir(BigDecimal nuconf, BigDecimal nuleit, String motivo, BigDecimal usuario) throws Exception {
        validar(nuconf);
        if(nuleit==null||nuleit.signum()<=0||nuleit.stripTrailingZeros().scale()>0||usuario==null)
            throw new Exception("Identificador de leitura invalido.");
        if(motivo==null||motivo.trim().isEmpty()||motivo.trim().length()>500)
            throw new Exception("Informe o motivo da exclusao (ate 500 caracteres).");
        JapeSession.SessionHandle h=null;JdbcWrapper jdbc=null;
        try {
            h=JapeSession.open();EntityFacade facade=JapeFactory.getEntityFacade();
            jdbc=facade.getJdbcWrapper();jdbc.openSession();
            NativeSql sql=new NativeSql(jdbc);ResultSet rs=null;
            try {
                sql.appendSql("SELECT STATUS FROM AD_CONF_CARGA WHERE NUCONF=:N FOR UPDATE");
                sql.setNamedParameter("N",nuconf);rs=sql.executeQuery();
                if(!rs.next()||!"A".equals(rs.getString(1)))
                    throw new Exception("Conferencia nao esta aberta para exclusao.");
            } finally {close(rs,sql);}
            BigDecimal produto,qtd;String lote,unidade;
            sql=new NativeSql(jdbc);rs=null;
            try {
                sql.appendSql("SELECT CODPROD,CONTROLE,CODVOL,QTD FROM AD_CONF_CARGA_LEIT " +
                    "WHERE NUCONF=:N AND NULEIT=:L AND NVL(STATUS,'A')='A' FOR UPDATE");
                sql.setNamedParameter("N",nuconf);sql.setNamedParameter("L",nuleit);
                rs=sql.executeQuery();
                if(!rs.next())throw new Exception("Leitura inexistente ou ja excluida.");
                produto=rs.getBigDecimal(1);lote=rs.getString(2);
                unidade=rs.getString(3);qtd=rs.getBigDecimal(4);
            } finally {close(rs,sql);}
            sql=new NativeSql(jdbc);
            try {
                sql.appendSql("UPDATE AD_CONF_CARGA_LEIT SET STATUS='E',CODUSUEXC=:U,DHEXC=SYSDATE " +
                    "WHERE NUCONF=:N AND NULEIT=:L AND NVL(STATUS,'A')='A'");
                sql.setNamedParameter("U",usuario);sql.setNamedParameter("N",nuconf);
                sql.setNamedParameter("L",nuleit);
                if(!sql.executeUpdate())throw new Exception("Leitura nao foi excluida.");
            } finally {NativeSql.releaseResources(sql);}
            HistoricoConferenciaService.registrar(jdbc,nuconf,nuleit,"EXCLUSAO",produto,lote,unidade,qtd,usuario,motivo.trim());
            JsonObject out=new JsonObject();out.addProperty("nuleit",nuleit);
            out.addProperty("mensagem","Leitura excluida e historico registrado.");return out;
        } finally {if(jdbc!=null)JdbcWrapper.closeSession(jdbc);if(h!=null)JapeSession.close(h);}
    }
    private static void close(ResultSet rs,NativeSql sql) throws Exception {
        if(rs!=null)rs.close();if(sql!=null)NativeSql.releaseResources(sql);
    }
}
