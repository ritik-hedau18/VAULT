package com.vault.transaction.service;

import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.entity.AccountType;
import com.vault.account.repository.AccountRepository;
import com.vault.auth.entity.User;
import com.vault.auth.entity.UserRole;
import com.vault.auth.entity.UserStatus;
import com.vault.auth.repository.UserRepository;
import com.vault.transaction.dto.TransactionResponse;
import com.vault.transaction.dto.TransferRequest;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.ssl.enabled=false")
@Testcontainers
public class TransferIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User senderUser;
    private User receiverUser;
    private Account senderAccount;
    private Account receiverAccount;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Seed users
        senderUser = userRepository.save(User.builder()
                .fullName("Sender Test")
                .email("sender@test.com")
                .passwordHash(passwordEncoder.encode("Password123"))
                .transactionPinHash(passwordEncoder.encode("1234"))
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());

        receiverUser = userRepository.save(User.builder()
                .fullName("Receiver Test")
                .email("receiver@test.com")
                .passwordHash(passwordEncoder.encode("Password123"))
                .transactionPinHash(passwordEncoder.encode("1234"))
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());

        // Seed accounts
        senderAccount = accountRepository.save(Account.builder()
                .user(senderUser)
                .accountNumber("111122223333")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("50000.0000"))
                .dailyTransferLimit(new BigDecimal("100000.0000"))
                .status(AccountStatus.ACTIVE)
                .build());

        receiverAccount = accountRepository.save(Account.builder()
                .user(receiverUser)
                .accountNumber("444455556666")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("10000.0000"))
                .dailyTransferLimit(new BigDecimal("100000.0000"))
                .status(AccountStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Allow async listener tasks (audit logs/notifications) to complete before tearing down context
        Thread.sleep(1000);
    }

    @Test
    void testTransfer_SameBank_Success() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(senderAccount.getId());
        request.setToAccountNumber("444455556666");
        request.setInterBank(false);
        request.setAmount(new BigDecimal("15000.00"));
        request.setTransactionPin("1234");
        request.setDescription("Rent Payment");

        TransactionResponse response = transferService.transfer(senderUser, request, UUID.randomUUID().toString());

        assertNotNull(response);
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());

        Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account updatedReceiver = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        // 50000 - 15000 = 35000
        assertEquals(0, new BigDecimal("35000.0000").compareTo(updatedSender.getBalance()));
        // 10000 + 15000 = 25000
        assertEquals(0, new BigDecimal("25000.0000").compareTo(updatedReceiver.getBalance()));

        List<Transaction> txs = transactionRepository.findAll();
        assertFalse(txs.isEmpty());
    }

    @Test
    void testTransfer_Idempotency() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(senderAccount.getId());
        request.setToAccountNumber("444455556666");
        request.setInterBank(false);
        request.setAmount(new BigDecimal("5000.00"));
        request.setTransactionPin("1234");
        request.setDescription("Idempotent Payment");

        String idempotencyKey = UUID.randomUUID().toString();

        // First transfer submission
        TransactionResponse response1 = transferService.transfer(senderUser, request, idempotencyKey);
        assertNotNull(response1);

        // Second transfer submission (exact same key)
        TransactionResponse response2 = transferService.transfer(senderUser, request, idempotencyKey);
        assertNotNull(response2);

        // Verify balances are only debited/credited once
        Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account updatedReceiver = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("45000.0000").compareTo(updatedSender.getBalance()));
        assertEquals(0, new BigDecimal("15000.0000").compareTo(updatedReceiver.getBalance()));

        // Verify only 1 transaction record exists
        List<Transaction> txs = transactionRepository.findAll();
        assertEquals(1, txs.size());
    }

    @Test
    void testTransfer_ConcurrentTransfers_NoDoubleSpend() throws InterruptedException {
        TransferRequest request1 = new TransferRequest();
        request1.setFromAccountId(senderAccount.getId());
        request1.setToAccountNumber("444455556666");
        request1.setInterBank(false);
        request1.setAmount(new BigDecimal("30000.00"));
        request1.setTransactionPin("1234");
        request1.setDescription("Concurrent Payment 1");

        TransferRequest request2 = new TransferRequest();
        request2.setFromAccountId(senderAccount.getId());
        request2.setToAccountNumber("444455556666");
        request2.setInterBank(false);
        request2.setAmount(new BigDecimal("30000.00"));
        request2.setTransactionPin("1234");
        request2.setDescription("Concurrent Payment 2");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<Boolean> task1 = () -> {
            latch.await();
            try {
                transferService.transfer(senderUser, request1, UUID.randomUUID().toString());
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Callable<Boolean> task2 = () -> {
            latch.await();
            try {
                transferService.transfer(senderUser, request2, UUID.randomUUID().toString());
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Future<Boolean> f1 = executor.submit(task1);
        Future<Boolean> f2 = executor.submit(task2);

        latch.countDown();

        try {
            boolean r1 = f1.get();
            boolean r2 = f2.get();

            assertTrue(r1 || r2);
            assertFalse(r1 && r2);
        } catch (ExecutionException e) {
            fail("Execution failed: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("20000.0000").compareTo(updatedSender.getBalance()));
    }

    @Test
    void testTransfer_InsufficientBalance_RollsBack() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(senderAccount.getId());
        request.setToAccountNumber("444455556666");
        request.setInterBank(false);
        request.setAmount(new BigDecimal("60000.00"));
        request.setTransactionPin("1234");
        request.setDescription("Failed Payment");

        assertThrows(Exception.class, () -> {
            transferService.transfer(senderUser, request, UUID.randomUUID().toString());
        });

        Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account updatedReceiver = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("50000.0000").compareTo(updatedSender.getBalance()));
        assertEquals(0, new BigDecimal("10000.0000").compareTo(updatedReceiver.getBalance()));

        List<Transaction> txs = transactionRepository.findAll();
        assertTrue(txs.isEmpty());
    }
}
