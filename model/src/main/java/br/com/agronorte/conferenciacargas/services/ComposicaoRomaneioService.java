package br.com.agronorte.conferenciacargas.services;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.ResultSet;

/**
 * Resumo de leitura da alocacao FIFO do romaneio.
 * NUNCA retorna produto, lote, quantidade esperada ou lista de itens ao navegador.
 * A fonte e o estoque corrente; esta versao NAO congela a previsao impressa.
 */
public class ComposicaoRomaneioService {
    static final String SQL =
        "WITH DEMANDA_BASE AS ( " +
        " SELECT CAB.NUNOTA, ITE.SEQUENCIA, CAB.CODEMP, CAB.ORDEMCARGA, ITE.CODPROD, ITE.CODVOL, " +
        " NVL(REGEXP_SUBSTR(UPPER(TRIM(CAB.NUMPEDIDO2)), '^[A-Z]+'), '@NAC') PREFIXO, " +
        " (ITE.QTDNEG - NVL(ITE.QTDCONFERIDA,0) - NVL(ITE.QTDENTREGUE,0)) QTD_NECESSARIA " +
        " FROM TGFITE ITE JOIN TGFCAB CAB ON CAB.NUNOTA=ITE.NUNOTA " +
        " JOIN TGFORD ORD ON ORD.ORDEMCARGA=CAB.ORDEMCARGA AND ORD.CODEMP=CAB.CODEMP " +
        " WHERE CAB.CODEMP=:CODEMP AND CAB.ORDEMCARGA=:ORDEMCARGA " +
        " AND (ITE.QTDNEG - NVL(ITE.QTDCONFERIDA,0) - NVL(ITE.QTDENTREGUE,0)) > 0 " +
        " ), DEMANDA AS ( " +
        " SELECT D.*, " +
        " NVL(SUM(D.QTD_NECESSARIA) OVER (PARTITION BY D.CODEMP,D.ORDEMCARGA,D.CODPROD,D.PREFIXO " +
        " ORDER BY D.NUNOTA,D.SEQUENCIA ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING),0) DEMANDA_ANT, " +
        " SUM(D.QTD_NECESSARIA) OVER (PARTITION BY D.CODEMP,D.ORDEMCARGA,D.CODPROD,D.PREFIXO " +
        " ORDER BY D.NUNOTA,D.SEQUENCIA ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) DEMANDA_ACUM " +
        " FROM DEMANDA_BASE D " +
        " ), LOTES_DISPONIVEIS AS ( " +
        " SELECT EST.CODEMP,EST.CODPROD,EST.CODLOCAL,EST.CONTROLE LOTE,EST.DTFABRICACAO, " +
        " NVL(REGEXP_SUBSTR(UPPER(TRIM(EST.CONTROLE)), '^[A-Z]+'), '@NAC') PREFIXO, " +
        " GREATEST(EST.ESTOQUE-NVL(EST.RESERVADO,0),0) SALDO " +
        " FROM TGFEST EST WHERE TRIM(EST.CONTROLE) IS NOT NULL " +
        " AND GREATEST(EST.ESTOQUE-NVL(EST.RESERVADO,0),0)>0 " +
        " AND EST.CODEMP=:CODEMP AND EST.CODLOCAL=71400000 " +
        " ), ESTOQUE AS ( " +
        " SELECT L.*, " +
        " NVL(SUM(L.SALDO) OVER (PARTITION BY L.CODEMP,L.CODPROD,L.PREFIXO " +
        " ORDER BY L.DTFABRICACAO NULLS LAST,L.LOTE,L.CODLOCAL " +
        " ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING),0) ESTOQUE_ANT, " +
        " SUM(L.SALDO) OVER (PARTITION BY L.CODEMP,L.CODPROD,L.PREFIXO " +
        " ORDER BY L.DTFABRICACAO NULLS LAST,L.LOTE,L.CODLOCAL " +
        " ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) ESTOQUE_ACUM " +
        " FROM LOTES_DISPONIVEIS L " +
        " ), ALOCACAO AS ( " +
        " SELECT D.CODEMP,D.CODPROD,D.PREFIXO,D.CODVOL,E.LOTE, " +
        " GREATEST(0,LEAST(D.DEMANDA_ACUM,E.ESTOQUE_ACUM)-GREATEST(D.DEMANDA_ANT,E.ESTOQUE_ANT)) QTD " +
        " FROM DEMANDA D JOIN ESTOQUE E ON E.CODEMP=D.CODEMP AND E.CODPROD=D.CODPROD " +
        " AND E.PREFIXO=D.PREFIXO AND E.ESTOQUE_ACUM>D.DEMANDA_ANT " +
        " AND E.ESTOQUE_ANT<D.DEMANDA_ACUM " +
        " ), DEMANDA_TOTAL AS ( " +
        " SELECT CODEMP,CODPROD,PREFIXO,SUM(QTD_NECESSARIA) NECESSARIO " +
        " FROM DEMANDA_BASE GROUP BY CODEMP,CODPROD,PREFIXO " +
        " ), RESUMO_ALOCADO AS ( " +
        " SELECT CODEMP,CODPROD,PREFIXO,SUM(QTD) ALOCADO FROM ALOCACAO " +
        " WHERE QTD>0 GROUP BY CODEMP,CODPROD,PREFIXO " +
        " ), FALTAS AS ( " +
        " SELECT DT.CODEMP,DT.CODPROD,DT.PREFIXO,DT.NECESSARIO-NVL(RA.ALOCADO,0) FALTA " +
        " FROM DEMANDA_TOTAL DT LEFT JOIN RESUMO_ALOCADO RA " +
        " ON RA.CODEMP=DT.CODEMP AND RA.CODPROD=DT.CODPROD AND RA.PREFIXO=DT.PREFIXO " +
        " WHERE DT.NECESSARIO>NVL(RA.ALOCADO,0) " +
        " ) " +
        " SELECT (SELECT COUNT(*) FROM DEMANDA_BASE) ITENS_PENDENTES, " +
        " (SELECT COUNT(*) FROM (SELECT CODPROD,PREFIXO FROM DEMANDA_BASE GROUP BY CODPROD,PREFIXO)) GRUPOS_DEMANDA, " +
        " (SELECT COUNT(*) FROM (SELECT CODPROD,PREFIXO,LOTE,CODVOL FROM ALOCACAO WHERE QTD>0 GROUP BY CODPROD,PREFIXO,LOTE,CODVOL)) GRUPOS_ALOCADOS, " +
        " (SELECT COUNT(*) FROM FALTAS) GRUPOS_SEM_SALDO, " +
        " (SELECT COUNT(*) FROM (SELECT CODPROD,PREFIXO FROM DEMANDA_BASE " +
        " GROUP BY CODPROD,PREFIXO HAVING COUNT(DISTINCT NVL(CODVOL,'@NULO'))>1)) GRUPOS_UNIDADE_MISTA " +
        " FROM DUAL";

    public JsonObject consultar(BigDecimal empresa, BigDecimal ordem) throws Exception {
        if (empresa == null || ordem == null || empresa.signum() <= 0 || ordem.signum() <= 0 ||
            empresa.stripTrailingZeros().scale() > 0 || ordem.stripTrailingZeros().scale() > 0) {
            throw new Exception("Informe empresa e ordem de carga validas.");
        }
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
            sql.appendSql(SQL);
            sql.setNamedParameter("CODEMP", empresa);
            sql.setNamedParameter("ORDEMCARGA", ordem);
            rs = sql.executeQuery();
            if (!rs.next()) throw new Exception("A consulta FIFO nao retornou resumo.");
            int itens = rs.getInt("ITENS_PENDENTES");
            int faltas = rs.getInt("GRUPOS_SEM_SALDO");
            int mistas = rs.getInt("GRUPOS_UNIDADE_MISTA");
            JsonObject out = new JsonObject();
            out.addProperty("situacao", itens == 0 ? "SEM_DEMANDA" : faltas > 0 || mistas > 0 ? "REQUER_ANALISE" : "ALOCACAO_COMPLETA");
            out.addProperty("demandaPendente", itens > 0);
            out.addProperty("alocacaoCompleta", itens > 0 && faltas == 0 && mistas == 0);
            out.addProperty("possuiFaltaDeLote", faltas > 0);
            out.addProperty("possuiUnidadeMista", mistas > 0);
            out.addProperty("mensagem", itens == 0 ? "Nenhuma demanda pendente identificada." :
                faltas > 0 ? "Existem quantidades sem lote disponivel. Conferencia nao liberada." :
                mistas > 0 ? "Foram identificadas unidades distintas para o mesmo produto/prefixo. Validacao necessaria." :
                "Alocacao FIFO atual completa. Previsao ainda nao congelada.");
            return out;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception ignored) { }
            if (sql != null) try { NativeSql.releaseResources(sql); } catch (Exception ignored) { }
            if (jdbc != null) try { jdbc.closeSession(); } catch (Exception ignored) { }
            if (handle != null) JapeSession.close(handle);
        }
    }
}
