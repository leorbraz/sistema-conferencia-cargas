package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import java.math.BigDecimal;
import java.sql.ResultSet;

/** Grava auditoria na mesma transacao da operacao. */
public final class AuditoriaAcaoService {
    private AuditoriaAcaoService() {}
    public static void registrar(JdbcWrapper jdbc, BigDecimal nuconf, String acao,
            String anterior, String novo, BigDecimal usuario, String justificativa) throws Exception {
        NativeSql sql = new NativeSql(jdbc);
        try {
            sql.appendSql("LOCK TABLE AD_CONF_CARGA_ACAO IN EXCLUSIVE MODE");
            sql.executeUpdate();
        } finally { NativeSql.releaseResources(sql); }
        BigDecimal id;
        sql = new NativeSql(jdbc);
        ResultSet rs = null;
        try {
            sql.appendSql("SELECT NVL(MAX(NUACAO),0)+1 FROM AD_CONF_CARGA_ACAO");
            rs = sql.executeQuery();
            if (!rs.next()) throw new Exception("Falha ao numerar auditoria.");
            id = rs.getBigDecimal(1);
        } finally { if (rs != null) rs.close(); NativeSql.releaseResources(sql); }
        sql = new NativeSql(jdbc);
        try {
            sql.appendSql("INSERT INTO AD_CONF_CARGA_ACAO (NUACAO,NUCONF,ACAO,STATUS_ANT,STATUS_NOVO,CODUSU,DHACAO,JUSTIFICATIVA) " +
                "VALUES (:ID,:N,:A,:ANT,:NOVO,:U,SYSDATE,:J)");
            sql.setNamedParameter("ID", id); sql.setNamedParameter("N", nuconf);
            sql.setNamedParameter("A", acao); sql.setNamedParameter("ANT", anterior);
            sql.setNamedParameter("NOVO", novo); sql.setNamedParameter("U", usuario);
            sql.setNamedParameter("J", justificativa);
            if (!sql.executeUpdate()) throw new Exception("Falha ao gravar auditoria.");
        } finally { NativeSql.releaseResources(sql); }
    }
}
