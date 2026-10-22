package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import java.math.BigDecimal;

/** Chamado dentro da mesma transacao EJB da leitura/exclusao. */
public final class HistoricoConferenciaService {
    private HistoricoConferenciaService() {}
    public static void registrar(JdbcWrapper jdbc, BigDecimal nuconf, BigDecimal nuleit,
            String acao, BigDecimal produto, String lote, String unidade,
            BigDecimal qtd, BigDecimal usuario, String motivo) throws Exception {
        NativeSql sql = new NativeSql(jdbc);
        try {
            sql.appendSql("INSERT INTO AD_CONF_CARGA_HIST (NUHIST,NUCONF,NULEIT,ACAO,CODPROD,CONTROLE,CODVOL,QTD,CODUSU,DHEVENTO,MOTIVO) " +
                "VALUES (TESTE.AD_CONF_CARGA_HIST_SEQ.NEXTVAL,:N,:L,:A,:P,:C,:V,:Q,:U,SYSDATE,:M)");
            sql.setNamedParameter("N", nuconf);
            sql.setNamedParameter("L", nuleit); sql.setNamedParameter("A", acao);
            sql.setNamedParameter("P", produto); sql.setNamedParameter("C", lote);
            sql.setNamedParameter("V", unidade); sql.setNamedParameter("Q", qtd);
            sql.setNamedParameter("U", usuario); sql.setNamedParameter("M", motivo);
            if (!sql.executeUpdate()) throw new Exception("Falha ao registrar historico.");
        } finally { NativeSql.releaseResources(sql); }
    }
}
