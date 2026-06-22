package com.vault.transaction.service;

import org.junit.jupiter.api.Test;
import java.sql.*;
import com.vault.security.EncryptionUtil;

public class DbInspectTest {
    @Test
    public void inspect() throws Exception {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        
        if (url == null) {
            url = "jdbc:postgresql://localhost:5432/postgres";
        }
        
        System.out.println("Connecting to database: " + url);
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected!");
            
            // Query all transactions involving Ritik or Aman
            System.out.println("--- TRANSACTIONS FOR RITIK OR AMAN ---");
            String sql = "SELECT id, reference_number, from_account_id, to_account_id, amount, type, status, description, initiated_at, completed_at " +
                         "FROM transactions " +
                         "WHERE from_account_id IN ('d24d92fd-5252-4b64-a058-da6a1f1edd5d') " +
                         "   OR to_account_id IN ('6b6c5a5a-ffb0-4f17-a089-f6d0072a313a', 'a50efb61-be5a-48eb-a77b-0240f667b95f') " +
                         "ORDER BY initiated_at DESC";
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("[%d] Tx: Ref=%s, From=%s, To=%s, Amount=%s, Type=%s, Status=%s, Desc=%s, Init=%s, Done=%s\n",
                        count, rs.getString("reference_number"), rs.getString("from_account_id"), rs.getString("to_account_id"),
                        rs.getString("amount"), rs.getString("type"), rs.getString("status"),
                        rs.getString("description"), rs.getString("initiated_at"), rs.getString("completed_at"));
                }
                System.out.println("Total transactions found: " + count);
            }
            
            // Let's also query all audit logs / notification logs from MongoDB if possible?
            // Wait, we don't have to check MongoDB unless necessary. Let's first inspect all DB transactions.
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
