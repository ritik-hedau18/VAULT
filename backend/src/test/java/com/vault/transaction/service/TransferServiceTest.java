package com.vault.transaction.service;

import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.entity.AccountType;
import com.vault.account.repository.AccountRepository;
import com.vault.account.service.AccountService;
import com.vault.auth.entity.User;
import com.vault.exception.InsufficientBalanceException;
import com.vault.exception.InvalidTransactionPinException;
import com.vault.transaction.dto.TransactionResponse;
import com.vault.transaction.dto.TransferRequest;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.entity.TransactionType;
import com.vault.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransferServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountService accountService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TransferTxExecutor transferTxExecutor;

    @InjectMocks
    private TransferService transferService;

    private User senderUser;
    private Account fromAccount;
    private Account toAccount;
    private TransferRequest request;

    @BeforeEach
    public void setup() {
        senderUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("John Doe")
                .email("john@example.com")
                .transactionPinHash("hashed_pin")
                .twoFaEnabled(false)
                .build();

        fromAccount = Account.builder()
                .id(UUID.randomUUID())
                .user(senderUser)
                .accountNumber("100028734912")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("10000.0000"))
                .dailyTransferLimit(new BigDecimal("50000.0000"))
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
    public void testTransfer_SuccessSameBank() {
        // Mock PIN validation
        when(passwordEncoder.matches("1234", "hashed_pin")).thenReturn(true);
        
        // Mock Redis distributed lock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("lock_value");

        // Mock Account fetching
        when(accountRepository.findById(fromAccount.getId())).thenReturn(Optional.of(fromAccount));

        // Mock daily transfer volume summation (no transfers today)
        when(transactionRepository.sumSuccessfulTransfersToday(eq(fromAccount), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);

        // Mock executeTransferTx
        Transaction mockTx = Transaction.builder()
                .id(UUID.randomUUID())
                .referenceNumber("TXN-123456789")
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .build();
        when(transferTxExecutor.executeTransferTx(any(), any(), any())).thenReturn(mockTx);

        // Run Transfer
        TransactionResponse response = transferService.transfer(senderUser, request, "idempotency_key_1");

        assertNotNull(response);
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        assertEquals(new BigDecimal("2000.0000"), response.getAmount());
        assertEquals("XXXX XXXX XXXX 4912", response.getFromAccountNumber());
        assertEquals("XXXX XXXX XXXX 2222", response.getToAccountNumber());

        verify(accountService, times(1)).invalidateBalanceCache(fromAccount.getId());
        verify(accountService, times(1)).invalidateBalanceCache(toAccount.getId());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testTransfer_IncorrectPin() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:idempotency_key_2")).thenReturn(null);
        when(passwordEncoder.matches("1234", "hashed_pin")).thenReturn(false);

        assertThrows(InvalidTransactionPinException.class, () -> {
            transferService.transfer(senderUser, request, "idempotency_key_2");
        });

        // Verify balance not altered
        assertEquals(new BigDecimal("10000.0000"), fromAccount.getBalance());
    }

    @Test
    public void testTransfer_InsufficientBalance() {
        // Set request amount larger than balance
        request.setAmount(new BigDecimal("25000.0000"));

        when(passwordEncoder.matches("1234", "hashed_pin")).thenReturn(true);
        when(accountRepository.findById(fromAccount.getId())).thenReturn(Optional.of(fromAccount));
        
        when(transactionRepository.sumSuccessfulTransfersToday(eq(fromAccount), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:idempotency_key_3")).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(valueOperations.get("transfer_lock:" + fromAccount.getId().toString())).thenReturn("lock_value");
        
        // Mock executeTransferTx to throw InsufficientBalanceException
        when(transferTxExecutor.executeTransferTx(any(), any(), any()))
                .thenThrow(new InsufficientBalanceException("Insufficient balance"));

        assertThrows(InsufficientBalanceException.class, () -> {
            transferService.transfer(senderUser, request, "idempotency_key_3");
        });

        // Balance remains unchanged
        assertEquals(new BigDecimal("10000.0000"), fromAccount.getBalance());
    }
}
