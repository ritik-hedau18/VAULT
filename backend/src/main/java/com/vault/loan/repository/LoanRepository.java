package com.vault.loan.repository;

import com.vault.loan.entity.Loan;
import com.vault.loan.entity.LoanStatus;
import com.vault.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {
    List<Loan> findByUser(User user);
    List<Loan> findByUserId(UUID userId);
    List<Loan> findByStatus(LoanStatus status);
    List<Loan> findByStatusAndNextDueDateBefore(LoanStatus status, LocalDate date);
}
