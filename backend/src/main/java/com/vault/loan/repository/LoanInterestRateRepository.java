package com.vault.loan.repository;

import com.vault.loan.entity.LoanInterestRate;
import com.vault.loan.entity.LoanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanInterestRateRepository extends JpaRepository<LoanInterestRate, LoanType> {
}
