package com.vault.transaction.service;

import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.entity.AccountType;
import com.vault.account.repository.AccountRepository;
import com.vault.exception.InsufficientBalanceException;
import com.vault.transaction.dto.TransferRequest;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransferTxExecutorTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private TransferTxExecutor transferTxExecutor;

    private Account fromAccount;
    private Account toAccount;
    private TransferRequest request;

    @BeforeEach
    public void setup() {
        fromAccount = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("100028734912")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("10000.0000"))
                .status(AccountStatus.ACTIVE)
                .build();

        toAccount = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("100088882222")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("5000.0000"))
                .status(AccountStatus.ACTIVE)
                .build();

        request = new TransferRequest();
        request.setFromAccountId(fromAccount.getId());
        request.setToAccountNumber(toAccount.getAccountNumber());
        request.setAmount(new BigDecimal("2000.0000"));
        request.setTransactionPin("1234");
        request.setInterBank(false);
        request.setDescription("Rent");
    }

    @Test
    public void testExecuteTransferTx_SuccessSameBank() {
        when(accountRepository.findByAccountNumber(toAccount.getAccountNumber())).thenReturn(Optional.of(toAccount));
        when(accountRepository.findByIdForUpdate(fromAccount.getId())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdForUpdate(toAccount.getId())).thenReturn(Optional.of(toAccount));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        Transaction transaction = transferTxExecutor.executeTransferTx(fromAccount, request, "idempotency_key");

        assertNotNull(transaction);
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(new BigDecimal("2000.0000"), transaction.getAmount());
        assertEquals(new BigDecimal("8000.0000"), fromAccount.getBalance());
        assertEquals(new BigDecimal("7000.0000"), toAccount.getBalance());

        verify(accountRepository, times(1)).save(fromAccount);
        verify(accountRepository, times(1)).save(toAccount);
    }

    @Test
    public void testExecuteTransferTx_SuccessInterBank() {
        request.setInterBank(true);
        request.setBankCode("SBI");

        when(accountRepository.findByIdForUpdate(fromAccount.getId())).thenReturn(Optional.of(fromAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        Transaction transaction = transferTxExecutor.executeTransferTx(fromAccount, request, "idempotency_key");

        assertNotNull(transaction);
        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
        assertEquals(new BigDecimal("2000.0000"), transaction.getAmount());
        assertEquals(new BigDecimal("8000.0000"), fromAccount.getBalance());

        verify(accountRepository, times(1)).save(fromAccount);
        verify(accountRepository, never()).save(toAccount);
    }

    @Test
    public void testExecuteTransferTx_InsufficientBalance() {
        request.setAmount(new BigDecimal("15000.0000"));

        when(accountRepository.findByAccountNumber(toAccount.getAccountNumber())).thenReturn(Optional.of(toAccount));
        when(accountRepository.findByIdForUpdate(fromAccount.getId())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdForUpdate(toAccount.getId())).thenReturn(Optional.of(toAccount));

        assertThrows(InsufficientBalanceException.class, () -> {
            transferTxExecutor.executeTransferTx(fromAccount, request, "idempotency_key");
        });

        assertEquals(new BigDecimal("10000.0000"), fromAccount.getBalance());
        verify(accountRepository, never()).save(any());
    }
}
