package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.ResultSet;

/** Registro fisico: nenhuma quantidade prevista e enviada ao navegador. */
public class LeituraConferenciaService {
    public JsonObject registrar(BigDecimal nuconf, String barra, String lote, BigDecimal qtd,
                                String unidade, String evento, BigDecimal usuario) throws Exception {
        if (nuconf == null || nuconf.signum() <= 0 || nuconf.stripTrailingZeros().scale() > 0 ||
            barra == null || barra.trim().isEmpty() || barra.length() > 100 ||
            lote == null || lote.trim().isEmpty() || lote.length() > 100 ||
            unidade == null || unidade.trim().isEmpty() || unidade.length() > 20 ||
            evento == null || evento.trim().isEmpty() || evento.length() > 80 ||
            qtd == null || qtd.signum() <= 0 || qtd.scale() > 8 || usuario == null)
            throw new Exception("Dados de leitura invalidos.");
        barra = barra.trim(); lote = lote.trim(); unidade = unidade.trim(); evento = evento.trim();
        JapeSession.SessionHandle h = null;
        JdbcWrapper jdbc = null;
        try {
            h = JapeSession.open();
            EntityFacade facade = JapeFactory.getEntityFacade();
            jdbc = facade.getJdbcWrapper();
            jdbc.openSession();
            // A linha da conferencia e bloqueada para serializar leituras e numeracao.
            NativeSql sql = new NativeSql(jdbc);
            ResultSet rs = null;
            try {
                sql.appendSql("SELECT STATUS FROM AD_CONF_CARGA WHERE NUCONF=:N FOR UPDATE");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                if (!rs.next()) throw new Exception("Conferencia nao encontrada.");
                if (!"A".equals(rs.getString(1)))
                    throw new Exception("Conferencia nao liberada. Aguarde a validacao administrativa (STATUS=A).");
            } finally { close(rs,sql); }
            // Reenvio de mesma requisicao nao pode duplicar contagem.
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("SELECT NULEIT FROM AD_CONF_CARGA_LEIT WHERE IDEVENTO=:I");
                sql.setNamedParameter("I", evento);
                rs = sql.executeQuery();
                if (rs.next()) return resultado(rs.getBigDecimal(1), true);
            } finally { close(rs,sql); }
            // Nesta primeira fase, somente o codigo de barras principal do produto.
            // Outras codificacoes exigirao mapeamento adicional validado no ambiente.
            BigDecimal produto;
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("SELECT CODPROD FROM TGFPRO WHERE REFERENCIA=:B AND USOPROD='V'");
                sql.setNamedParameter("B", barra);
                rs = sql.executeQuery();
                if (!rs.next()) throw new Exception("Codigo de barras nao localizado no cadastro de produtos.");
                produto = rs.getBigDecimal(1);
                if (rs.next()) throw new Exception("Codigo de barras ambiguo: mais de um produto.");
            } finally { close(rs,sql); }
            // Na reconferencia, nao permitir bipagem de produto que permaneceu conforme.
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("SELECT COUNT(*) FROM AD_CONF_CARGA_ACAO " +
                    "WHERE NUCONF=:N AND ACAO='RECONFERIR'");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                boolean reconferencia = rs.next() && rs.getInt(1) > 0;
                if (reconferencia) {
                    close(rs, sql); rs = null; sql = new NativeSql(jdbc);
                    sql.appendSql("SELECT COUNT(*) FROM AD_CONF_CARGA_LEIT " +
                        "WHERE NUCONF=:N AND CODPROD=:P AND STATUS='R'");
                    sql.setNamedParameter("N", nuconf);
                    sql.setNamedParameter("P", produto);
                    rs = sql.executeQuery();
                    boolean anteriorArquivada = rs.next() && rs.getInt(1)>0;
                    if (!anteriorArquivada) {
                        close(rs, sql); rs=null; sql=new NativeSql(jdbc);
                        sql.appendSql("SELECT COUNT(*) FROM AD_CONF_CARGA_PREV P " +
                            "WHERE P.NUCONF=:N AND P.CODPROD=:P AND NOT EXISTS (" +
                            "SELECT 1 FROM AD_CONF_CARGA_LEIT L WHERE L.NUCONF=P.NUCONF " +
                            "AND L.CODPROD=P.CODPROD AND NVL(L.STATUS,'A')='A')");
                        sql.setNamedParameter("N", nuconf);
                        sql.setNamedParameter("P", produto);
                        rs = sql.executeQuery();
                        if (!(rs.next() && rs.getInt(1)>0))
                            throw new Exception("Produto fora da reconferencia direcionada.");
                    }
                }
            } finally { close(rs,sql); }
            BigDecimal id;
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("SELECT NVL(MAX(NULEIT),0)+1 FROM AD_CONF_CARGA_LEIT");
                rs = sql.executeQuery();
                if (!rs.next()) throw new Exception("Nao foi possivel numerar leitura.");
                id = rs.getBigDecimal(1);
            } finally { close(rs,sql); }
            sql = new NativeSql(jdbc);
            try {
                sql.appendSql("INSERT INTO AD_CONF_CARGA_LEIT (NULEIT,NUCONF,CODPROD,CONTROLE,CODVOL,CODBARRA,QTD,CODUSU,DHLEITURA,IDEVENTO,STATUS) " +
                              "VALUES (:I,:N,:P,:L,:V,:B,:Q,:U,SYSDATE,:EV,'A')");
                sql.setNamedParameter("I", id); sql.setNamedParameter("N", nuconf);
                sql.setNamedParameter("P", produto); sql.setNamedParameter("L", lote);
                sql.setNamedParameter("V", unidade); sql.setNamedParameter("B", barra);
                sql.setNamedParameter("Q", qtd); sql.setNamedParameter("U", usuario);
                sql.setNamedParameter("EV", evento);
                if (!sql.executeUpdate()) throw new Exception("Falha ao registrar leitura.");
            } finally { NativeSql.releaseResources(sql); }
            HistoricoConferenciaService.registrar(jdbc,nuconf,id,"INCLUSAO",produto,lote,unidade,qtd,usuario,null);
            return resultado(id, false);
        } finally {
            if (jdbc != null) JdbcWrapper.closeSession(jdbc);
            if (h != null) JapeSession.close(h);
        }
    }
    private JsonObject resultado(BigDecimal id, boolean repetida) {
        JsonObject out = new JsonObject();
        out.addProperty("numeroLeitura", id);
        out.addProperty("repetida", repetida);
        out.addProperty("mensagem", repetida ? "Leitura ja registrada anteriormente." : "Leitura registrada.");
        return out;
    }
    private static void close(ResultSet rs, NativeSql sql) throws Exception {
        if (rs != null) rs.close();
        if (sql != null) NativeSql.releaseResources(sql);
    }
}
