package com.vault.transaction.repository;

import com.vault.account.entity.Account;
import com.vault.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    Optional<Transaction> findByReferenceNumber(String referenceNumber);
    
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE t.fromAccount = :account OR t.toAccount = :account ORDER BY t.initiatedAt DESC")
    List<Transaction> findMiniStatement(@Param("account") Account account, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.fromAccount = :account OR t.toAccount = :account")
    Page<Transaction> findTransactionHistory(@Param("account") Account account, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.fromAccount = :account AND t.type = com.vault.transaction.entity.TransactionType.TRANSFER AND t.status = com.vault.transaction.entity.TransactionStatus.SUCCESS AND t.initiatedAt >= :startOfToday")
    BigDecimal sumSuccessfulTransfersToday(@Param("account") Account account, @Param("startOfToday") LocalDateTime startOfToday);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.status = com.vault.transaction.entity.TransactionStatus.SUCCESS AND t.initiatedAt >= :startOfToday")
    BigDecimal sumAllTransactionVolumeToday(@Param("startOfToday") LocalDateTime startOfToday);
}
