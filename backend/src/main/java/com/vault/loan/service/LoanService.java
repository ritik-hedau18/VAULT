package com.vault.loan.service;

import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.repository.AccountRepository;
import com.vault.account.service.AccountService;
import com.vault.auth.entity.User;
import com.vault.exception.InsufficientBalanceException;
import com.vault.exception.InvalidTransactionPinException;
import com.vault.loan.dto.LoanApplicationRequest;
import com.vault.loan.dto.LoanRepaymentRequest;
import com.vault.loan.entity.Loan;
import com.vault.loan.entity.LoanStatus;
import com.vault.loan.entity.LoanInterestRate;
import com.vault.loan.repository.LoanInterestRateRepository;
import com.vault.loan.repository.LoanRepository;
import com.vault.transaction.TransactionEvent;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.entity.TransactionType;
import com.vault.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final LoanInterestRateRepository loanInterestRateRepository;

    public List<LoanInterestRate> getInterestRates() {
        return loanInterestRateRepository.findAll();
    }

    @Transactional
    public LoanInterestRate updateInterestRate(com.vault.loan.entity.LoanType loanType, BigDecimal newRate) {
        LoanInterestRate rate = loanInterestRateRepository.findById(loanType)
                .orElseGet(() -> LoanInterestRate.builder().loanType(loanType).build());
        rate.setInterestRate(newRate);
        return loanInterestRateRepository.save(rate);
    }

    public BigDecimal calculateEMI(BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        double r = annualRate.doubleValue() / 12.0 / 100.0;
        double p = principal.doubleValue();
        double pow = Math.pow(1.0 + r, tenureMonths);
        
        if (pow == 1.0) {
            // Edge case: zero interest rate
            return principal.divide(BigDecimal.valueOf(tenureMonths), 4, RoundingMode.HALF_UP);
        }
        
        double emiVal = (p * r * pow) / (pow - 1.0);
        return new BigDecimal(emiVal).setScale(4, RoundingMode.HALF_UP);
    }

    @Transactional
    public Loan applyForLoan(User user, LoanApplicationRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Account does not belong to the user");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account must be ACTIVE for loan operations");
        }

        LoanInterestRate rateConfig = loanInterestRateRepository.findById(request.getLoanType())
                .orElseThrow(() -> new IllegalArgumentException("Interest rate not configured for loan type: " + request.getLoanType()));
        BigDecimal activeInterestRate = rateConfig.getInterestRate();

        BigDecimal emi = calculateEMI(request.getPrincipal(), activeInterestRate, request.getTenureMonths());

        Loan loan = Loan.builder()
                .user(user)
                .account(account)
                .loanType(request.getLoanType())
                .principal(request.getPrincipal())
                .interestRate(activeInterestRate)
                .tenureMonths(request.getTenureMonths())
                .emiAmount(emi)
                .outstandingAmount(request.getPrincipal())
                .status(LoanStatus.PENDING)
                .build();

        return loanRepository.save(loan);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Loan approveLoan(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING loans can be approved");
        }

        Account account = accountRepository.findByIdForUpdate(loan.getAccount().getId())
                .orElseThrow(() -> new IllegalArgumentException("Associated disbursement account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Disbursement account is not ACTIVE");
        }

        // Disbursement: deposit loan principal into account
        BigDecimal amount = loan.getPrincipal().setScale(4, RoundingMode.HALF_UP);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        // Update Loan Status
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setNextDueDate(LocalDate.now().plusMonths(1));
        Loan approvedLoan = loanRepository.save(loan);

        // Log disbursement as DEPOSIT transaction
        String refNum = "LND-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        Transaction transaction = Transaction.builder()
                .referenceNumber(refNum)
                .toAccount(account)
                .amount(amount)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .description("Disbursement of " + loan.getLoanType() + " Loan #" + loan.getId().toString().substring(0, 8))
                .initiatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);

        // Clear balance cache, publish transaction event
        accountService.invalidateBalanceCache(account.getId());
        eventPublisher.publishEvent(new TransactionEvent(this, transaction, loan.getUser().getId()));

        return approvedLoan;
    }

    @Transactional
    public Loan rejectLoan(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING loans can be rejected");
        }

        loan.setStatus(LoanStatus.REJECTED);
        return loanRepository.save(loan);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Loan repayLoan(User user, UUID loanId, LoanRepaymentRequest request) {
        // Validate PIN
        if (!passwordEncoder.matches(request.getTransactionPin(), user.getTransactionPinHash())) {
            throw new InvalidTransactionPinException("Incorrect transaction PIN");
        }

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalArgumentException("Repayments can only be made on ACTIVE loans");
        }

        Account account = accountRepository.findByIdForUpdate(loan.getAccount().getId())
                .orElseThrow(() -> new IllegalArgumentException("Repayment account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Associated account does not belong to the user");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Repayment account is not ACTIVE");
        }

        BigDecimal repayAmount = request.getAmount().setScale(4, RoundingMode.HALF_UP);
        if (account.getBalance().compareTo(repayAmount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in account to repay loan EMI");
        }

        // Debit Account
        account.setBalance(account.getBalance().subtract(repayAmount));
        accountRepository.save(account);

        // Credit Loan
        BigDecimal remaining = loan.getOutstandingAmount().subtract(repayAmount);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setOutstandingAmount(BigDecimal.ZERO);
            loan.setStatus(LoanStatus.CLOSED);
            loan.setNextDueDate(null);
        } else {
            loan.setOutstandingAmount(remaining);
            loan.setNextDueDate(loan.getNextDueDate().plusMonths(1));
        }
        Loan updatedLoan = loanRepository.save(loan);

        // Log repayment as WITHDRAWAL transaction
        String refNum = "LNP-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        Transaction transaction = Transaction.builder()
                .referenceNumber(refNum)
                .fromAccount(account)
                .amount(repayAmount)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .description("Repayment for Loan #" + loan.getId().toString().substring(0, 8))
                .initiatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);

        // Clear balance cache, publish transaction event
        accountService.invalidateBalanceCache(account.getId());
        eventPublisher.publishEvent(new TransactionEvent(this, transaction, user.getId()));

        return updatedLoan;
    }

    public List<Loan> getMyLoans(User user) {
        return loanRepository.findByUserId(user.getId());
    }

    public List<Loan> getAllLoansForAdmin() {
        return loanRepository.findAll();
    }
}
