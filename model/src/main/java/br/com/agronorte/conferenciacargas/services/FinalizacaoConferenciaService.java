package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.ResultSet;

/** Encerramento fisico cego: nunca retorna itens, quantidades ou lotes previstos. */
public class FinalizacaoConferenciaService {
    public JsonObject executar(BigDecimal nuconf, BigDecimal usuario, boolean confirmar, boolean divergenciaConfirmada) throws Exception {
        if (nuconf == null || nuconf.signum() <= 0 || nuconf.stripTrailingZeros().scale() > 0 || usuario == null)
            throw new Exception("Conferencia ou usuario invalido.");
        JapeSession.SessionHandle h = null;
        JdbcWrapper jdbc = null;
        try {
            h = JapeSession.open();
            EntityFacade facade = JapeFactory.getEntityFacade();
            jdbc = facade.getJdbcWrapper();
            jdbc.openSession();
            // O mesmo bloqueio utilizado pelas leituras e exclusoes evita finalizacao simultanea.
            NativeSql sql = new NativeSql(jdbc);
            ResultSet rs = null;
            try {
                sql.appendSql("SELECT STATUS FROM AD_CONF_CARGA WHERE NUCONF=:N FOR UPDATE");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                if (!rs.next()) throw new Exception("Conferencia nao localizada.");
                if (!"A".equals(rs.getString(1))) throw new Exception("Somente conferencias em andamento podem ser finalizadas.");
            } finally { close(rs, sql); }
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("SELECT COUNT(*) FROM AD_CONF_CARGA_LEIT WHERE NUCONF=:N AND NVL(STATUS,'A')='A'");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                if (!rs.next() || rs.getLong(1) == 0) throw new Exception("Registre ao menos uma leitura antes de finalizar.");
            } finally { close(rs, sql); }
            // Uma conferencia sem previsao congelada nao pode ser encerrada.
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("SELECT COUNT(*) FROM AD_CONF_CARGA_PREV WHERE NUCONF=:N");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                if (!rs.next() || rs.getLong(1) == 0)
                    throw new Exception("Nao existe previsao congelada para esta conferencia. Verifique o inicio da conferencia.");
            } finally { close(rs, sql); }
            // Leituras com quantidade nula, zero ou negativa nao sao validas para encerramento.
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("SELECT COUNT(*) FROM AD_CONF_CARGA_LEIT WHERE NUCONF=:N AND NVL(STATUS,'A')='A' AND (QTD IS NULL OR QTD<=0)");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                if (!rs.next() || rs.getLong(1) > 0)
                    throw new Exception("Existem leituras com quantidade invalida. Corrija-as antes de finalizar.");
            } finally { close(rs, sql); }
            // FULL OUTER JOIN por produto, lote e unidade, inclusive itens nao previstos.
            boolean divergente;
            sql = new NativeSql(jdbc); rs = null;
            try {
                sql.appendSql("WITH P AS (SELECT CODPROD,CONTROLE,CODVOL,SUM(QTD_PREV) QTD FROM AD_CONF_CARGA_PREV WHERE NUCONF=:N GROUP BY CODPROD,CONTROLE,CODVOL), " +
                    "L AS (SELECT CODPROD,CONTROLE,CODVOL,SUM(QTD) QTD FROM AD_CONF_CARGA_LEIT WHERE NUCONF=:N AND NVL(STATUS,'A')='A' GROUP BY CODPROD,CONTROLE,CODVOL) " +
                    "SELECT COUNT(*) FROM P FULL OUTER JOIN L ON P.CODPROD=L.CODPROD AND DECODE(P.CONTROLE,L.CONTROLE,1,0)=1 AND DECODE(P.CODVOL,L.CODVOL,1,0)=1 " +
                    "WHERE NVL(P.QTD,0)<>NVL(L.QTD,0)");
                sql.setNamedParameter("N", nuconf);
                rs = sql.executeQuery();
                if (!rs.next()) throw new Exception("Falha ao comparar a conferencia.");
                divergente = rs.getLong(1) > 0;
            } finally { close(rs, sql); }
            JsonObject out = new JsonObject();
            out.addProperty("divergente", divergente);
            if (!confirmar) {
                out.addProperty("finalizada", false);
                out.addProperty("mensagem", divergente ? "Conferencia divergente. Deseja finalizar mesmo assim?" : "Conferencia sem divergencias. Deseja finalizar?");
                return out;
            }
            // Se a situacao mudou entre a consulta e a confirmacao, solicitar nova confirmacao.
            if (divergente != divergenciaConfirmada)
                throw new Exception("O resultado da conferencia mudou. Solicite a finalizacao novamente.");
            sql = new NativeSql(jdbc);
            try {
                sql.appendSql("UPDATE AD_CONF_CARGA SET STATUS=:S, CODUSUFIN=:U, DHFINALIZACAO=SYSDATE, DIVERGENTE=:D WHERE NUCONF=:N AND STATUS='A'");
                sql.setNamedParameter("S", divergente ? "D" : "O");
                sql.setNamedParameter("U", usuario);
                sql.setNamedParameter("D", divergente ? "S" : "N");
                sql.setNamedParameter("N", nuconf);
                if (!sql.executeUpdate()) throw new Exception("Nao foi possivel finalizar a conferencia.");
            } finally { NativeSql.releaseResources(sql); }
            AuditoriaAcaoService.registrar(jdbc, nuconf, "FINALIZAR", "A", divergente ? "D" : "O", usuario, null);
            out.addProperty("finalizada", true);
            out.addProperty("status", divergente ? "D" : "O");
            out.addProperty("mensagem", divergente ? "Conferencia finalizada divergente." : "Conferencia finalizada OK.");
            return out;
        } finally {
            if (jdbc != null) JdbcWrapper.closeSession(jdbc);
            if (h != null) JapeSession.close(h);
        }
    }
    private static void close(ResultSet rs, NativeSql sql) throws Exception {
        if (rs != null) rs.close();
        if (sql != null) NativeSql.releaseResources(sql);
    }
}
