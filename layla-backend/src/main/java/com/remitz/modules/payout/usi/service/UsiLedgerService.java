package com.remitz.modules.payout.usi.service;

import com.remitz.modules.payout.usi.dto.UsiLedgerSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reporting service for USI Money P&L. Reads PAID USI-routed transactions (a transaction is
 * USI-routed when it has a row in {@code usi_transactions}). Profit is simply the fee Layla
 * charges ({@code transactions.fee_amount}) — there is no FX-margin profit because the customer
 * receives USI's source rate with no markup. USI's commission is surfaced for reference only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsiLedgerService {

    private final JdbcTemplate jdbc;

    private static final String FROM_JOIN = """
            FROM transactions t
            JOIN usi_transactions u ON u.transaction_id = t.reference_number
            WHERE t.status = 'PAID'
              AND t.created_at >= ? AND t.created_at < ?
            """;

    @Transactional(readOnly = true)
    public UsiLedgerSummary summary(LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(30);
        if (to == null)   to   = LocalDate.now();
        Date fromDate = Date.valueOf(from);
        Date toDate   = Date.valueOf(to.plusDays(1)); // half-open upper bound

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COUNT(*)                          AS cnt,
                       COALESCE(SUM(t.send_amount), 0)   AS volume,
                       COALESCE(SUM(t.fee_amount), 0)    AS fee,
                       COALESCE(SUM(u.commission), 0)    AS usi_comm
                """ + FROM_JOIN, fromDate, toDate);

        return UsiLedgerSummary.builder()
                .from(from)
                .to(to)
                .transactionCount(asLong(row.get("cnt")))
                .totalVolume(asBigDecimal(row.get("volume")))
                .totalFee(asBigDecimal(row.get("fee")))
                .totalProfit(asBigDecimal(row.get("fee")))
                .totalUsiCommission(asBigDecimal(row.get("usi_comm")))
                .byCorridor(byCorridor(fromDate, toDate))
                .byDay(byDay(fromDate, toDate))
                .transactions(transactions(fromDate, toDate))
                .build();
    }

    private List<UsiLedgerSummary.TxRow> transactions(Date from, Date to) {
        String sql = """
                SELECT t.reference_number, t.status, t.sender_email,
                       t.send_amount, t.send_currency, t.receive_amount, t.receive_currency,
                       t.fee_amount, u.commission, u.reference_number AS usi_ref, t.created_at
                """ + FROM_JOIN + " ORDER BY t.created_at DESC LIMIT 300";
        return jdbc.query(sql, (rs, n) -> UsiLedgerSummary.TxRow.builder()
                .referenceNumber(rs.getString("reference_number"))
                .status(rs.getString("status"))
                .senderEmail(rs.getString("sender_email"))
                .sendAmount(rs.getBigDecimal("send_amount"))
                .sendCurrency(rs.getString("send_currency"))
                .receiveAmount(rs.getBigDecimal("receive_amount"))
                .receiveCurrency(rs.getString("receive_currency"))
                .fee(rs.getBigDecimal("fee_amount"))
                .usiCommission(rs.getBigDecimal("commission"))
                .usiReference(rs.getString("usi_ref"))
                .createdAt(String.valueOf(rs.getTimestamp("created_at")))
                .build(), from, to);
    }

    private List<UsiLedgerSummary.Bucket> byCorridor(Date from, Date to) {
        String sql = """
                SELECT CONCAT(t.send_currency, '->', t.receive_currency) AS k,
                       COUNT(*) AS cnt,
                       COALESCE(SUM(t.send_amount), 0) AS volume,
                       COALESCE(SUM(t.fee_amount), 0)  AS fee,
                       COALESCE(SUM(u.commission), 0)  AS usi_comm
                """ + FROM_JOIN + " GROUP BY t.send_currency, t.receive_currency ORDER BY fee DESC";
        return jdbc.query(sql, (rs, n) -> UsiLedgerSummary.Bucket.builder()
                .key(rs.getString("k"))
                .transactionCount(rs.getLong("cnt"))
                .volume(rs.getBigDecimal("volume"))
                .fee(rs.getBigDecimal("fee"))
                .usiCommission(rs.getBigDecimal("usi_comm"))
                .build(), from, to);
    }

    private List<UsiLedgerSummary.Bucket> byDay(Date from, Date to) {
        String sql = """
                SELECT DATE(t.created_at) AS k,
                       COUNT(*) AS cnt,
                       COALESCE(SUM(t.send_amount), 0) AS volume,
                       COALESCE(SUM(t.fee_amount), 0)  AS fee,
                       COALESCE(SUM(u.commission), 0)  AS usi_comm
                """ + FROM_JOIN + " GROUP BY DATE(t.created_at) ORDER BY k ASC";
        return jdbc.query(sql, (rs, n) -> UsiLedgerSummary.Bucket.builder()
                .key(String.valueOf(rs.getDate("k")))
                .transactionCount(rs.getLong("cnt"))
                .volume(rs.getBigDecimal("volume"))
                .fee(rs.getBigDecimal("fee"))
                .usiCommission(rs.getBigDecimal("usi_comm"))
                .build(), from, to);
    }

    private static long asLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static BigDecimal asBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(o.toString());
    }
}
