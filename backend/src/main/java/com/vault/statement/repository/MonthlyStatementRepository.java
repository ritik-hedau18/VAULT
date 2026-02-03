package com.vault.statement.repository;

import com.vault.statement.entity.MonthlyStatement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MonthlyStatementRepository extends MongoRepository<MonthlyStatement, String> {
    List<MonthlyStatement> findByAccountId(UUID accountId);
    List<MonthlyStatement> findByUserId(UUID userId);
}
