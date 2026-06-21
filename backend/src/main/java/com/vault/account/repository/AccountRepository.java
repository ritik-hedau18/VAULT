package com.vault.account.repository;

import com.vault.account.entity.Account;
import com.vault.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    default Optional<Account> findByAccountNumber(String accountNumber) {
        return findAll().stream()
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .findFirst();
    }

    List<Account> findByUser(User user);
    List<Account> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
    
    default Optional<Account> findByAccountNumberForUpdate(String accountNumber) {
        return findByAccountNumber(accountNumber)
                .flatMap(a -> findByIdForUpdate(a.getId()));
    }

    @Query("SELECT SUM(a.balance) FROM Account a")
    java.math.BigDecimal sumAllBalances();
}
