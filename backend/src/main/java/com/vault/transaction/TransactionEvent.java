package com.vault.transaction;

import com.vault.transaction.entity.Transaction;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class TransactionEvent extends ApplicationEvent {

    private final UUID transactionId;
    private final String referenceNumber;
    private final UUID userId;
    private final String type;
    private final BigDecimal amount;
    private final String fromAccountNumber;
    private final String toAccountNumber;
    private final String status;
    private final String description;

    public TransactionEvent(Object source, Transaction transaction, UUID userId) {
        super(source);
        this.transactionId = transaction.getId();
        this.referenceNumber = transaction.getReferenceNumber();
        this.userId = userId;
        this.type = transaction.getType().name();
        this.amount = transaction.getAmount();
        this.fromAccountNumber = transaction.getFromAccount() != null ? transaction.getFromAccount().getAccountNumber() : "CASH/ATM";
        this.toAccountNumber = transaction.getToAccount() != null ? transaction.getToAccount().getAccountNumber() : "CASH/ATM";
        this.status = transaction.getStatus().name();
        this.description = transaction.getDescription();
    }
}
