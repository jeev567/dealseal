package com.dealseal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

/**
 * SQLite demo storage. One JVM / one EC2 instance; not a distributed lock manager.
 * File location configured via DEALSEAL_DB_PATH (default ./data/dealseal.db).
 */
@Repository
public class DemoStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public DemoStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
        jdbc.execute("CREATE TABLE IF NOT EXISTS agreements (id TEXT PRIMARY KEY, version INTEGER NOT NULL, terms_json TEXT NOT NULL, client_approved INTEGER NOT NULL, developer_approved INTEGER NOT NULL, updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS locks (agreement_id TEXT PRIMARY KEY, version INTEGER NOT NULL, hash TEXT NOT NULL, terms_json TEXT NOT NULL, client_token TEXT NOT NULL, developer_token TEXT NOT NULL, client_approved_at TEXT, developer_approved_at TEXT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS paypal_orders (order_id TEXT PRIMARY KEY, agreement_id TEXT NOT NULL, version INTEGER NOT NULL, amount TEXT NOT NULL)");
    }
    private String json(List<String> terms) {
        try { return mapper.writeValueAsString(terms); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private List<String> terms(String raw) {
        try { return mapper.readValue(raw, new TypeReference<List<String>>() {}); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public record AgreementRow(UUID id,int version,List<String> terms,boolean clientApproved,boolean developerApproved,Instant updatedAt) {}
    public void saveAgreement(AgreementRow a) {
        jdbc.update("INSERT INTO agreements VALUES (?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET version=excluded.version,terms_json=excluded.terms_json,client_approved=excluded.client_approved,developer_approved=excluded.developer_approved,updated_at=excluded.updated_at",
                a.id().toString(),a.version(),json(a.terms()),a.clientApproved()?1:0,a.developerApproved()?1:0,a.updatedAt().toString());
    }
    public Optional<AgreementRow> agreement(UUID id) {
        var rows=jdbc.query("SELECT * FROM agreements WHERE id=?", (rs,n)->new AgreementRow(
                UUID.fromString(rs.getString("id")),rs.getInt("version"),terms(rs.getString("terms_json")),
                rs.getInt("client_approved")!=0,rs.getInt("developer_approved")!=0,Instant.parse(rs.getString("updated_at"))),id.toString());
        return rows.stream().findFirst();
    }
    public record LockRow(UUID id,int version,String hash,List<String> terms,String clientToken,String developerToken,Instant clientAt,Instant developerAt) {}
    public void saveLock(LockRow l) {
        jdbc.update("INSERT INTO locks VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(agreement_id) DO UPDATE SET version=excluded.version,hash=excluded.hash,terms_json=excluded.terms_json,client_token=excluded.client_token,developer_token=excluded.developer_token,client_approved_at=excluded.client_approved_at,developer_approved_at=excluded.developer_approved_at",
                l.id().toString(),l.version(),l.hash(),json(l.terms()),l.clientToken(),l.developerToken(),
                l.clientAt()==null?null:l.clientAt().toString(),l.developerAt()==null?null:l.developerAt().toString());
    }
    public Optional<LockRow> lock(UUID id) {
        var rows=jdbc.query("SELECT * FROM locks WHERE agreement_id=?", (rs,n)->new LockRow(
                UUID.fromString(rs.getString("agreement_id")),rs.getInt("version"),rs.getString("hash"),
                terms(rs.getString("terms_json")),rs.getString("client_token"),rs.getString("developer_token"),
                rs.getString("client_approved_at")==null?null:Instant.parse(rs.getString("client_approved_at")),
                rs.getString("developer_approved_at")==null?null:Instant.parse(rs.getString("developer_approved_at"))),id.toString());
        return rows.stream().findFirst();
    }
    public record OrderRow(UUID agreementId,int version,String amount) {}
    public void saveOrder(String orderId,OrderRow o) {
        jdbc.update("INSERT INTO paypal_orders VALUES (?,?,?,?) ON CONFLICT(order_id) DO NOTHING",orderId,o.agreementId().toString(),o.version(),o.amount());
    }
    public Optional<OrderRow> order(String id) {
        var rows=jdbc.query("SELECT * FROM paypal_orders WHERE order_id=?", (rs,n)->new OrderRow(
                UUID.fromString(rs.getString("agreement_id")),rs.getInt("version"),rs.getString("amount")),id);
        return rows.stream().findFirst();
    }
}
