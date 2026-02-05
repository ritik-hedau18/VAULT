package com.vault.transaction.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequest {

    @NotNull(message = "Source account ID is required")
    private UUID fromAccountId;

    @NotBlank(message = "Destination account number is required")
    private String toAccountNumber;

    private boolean interBank;

    private String bankCode; // E.g., IFSC code for inter-bank

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Transaction PIN is required")
    @Pattern(regexp = "^\\d{4}$", message = "Transaction PIN must be a 4-digit number")
    private String transactionPin;

    private String totpCode; // Required if 2FA enabled

    private String description;
}
