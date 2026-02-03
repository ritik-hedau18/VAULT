package com.vault.statement.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Document(collection = "monthly_statements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyStatement {

    @Id
    private String statementId;

    private UUID accountId;
    private UUID userId;
    private String month; // YYYY-MM
    private String openingBalance; // BigDecimal string representation
    private String closingBalance; // BigDecimal string representation
    private String totalCredits;   // BigDecimal string representation
    private String totalDebits;    // BigDecimal string representation
    private List<TransactionItem> transactions;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionItem {
        private String transactionId;
        private String amount;
        private String type;
        private String date;
        private String description;
    }
}
