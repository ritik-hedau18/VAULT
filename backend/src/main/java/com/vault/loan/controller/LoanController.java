package com.vault.loan.controller;

import com.vault.auth.entity.User;
import com.vault.loan.dto.EmiScheduleResponse;
import com.vault.loan.dto.LoanApplicationRequest;
import com.vault.loan.dto.LoanRepaymentRequest;
import com.vault.loan.dto.LoanResponse;
import com.vault.loan.entity.Loan;
import com.vault.loan.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    @PostMapping("/apply")
    public ResponseEntity<LoanResponse> applyForLoan(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody LoanApplicationRequest request) {
        Loan loan = loanService.applyForLoan(user, request);
        return new ResponseEntity<>(LoanResponse.fromEntity(loan), HttpStatus.CREATED);
    }

    @GetMapping("/my")
    public ResponseEntity<List<LoanResponse>> getMyLoans(@AuthenticationPrincipal User user) {
        List<LoanResponse> loans = loanService.getMyLoans(user).stream()
                .map(LoanResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(loans);
    }

    @PostMapping("/{id}/repay")
    public ResponseEntity<LoanResponse> repayLoan(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody LoanRepaymentRequest request) {
        Loan loan = loanService.repayLoan(user, id, request);
        return ResponseEntity.ok(LoanResponse.fromEntity(loan));
    }

    @GetMapping("/{id}/emi-schedule")
    public ResponseEntity<List<EmiScheduleResponse>> getEmiSchedule(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        
        // Load loan and check ownership
        List<Loan> userLoans = loanService.getMyLoans(user);
        boolean ownsLoan = userLoans.stream().anyMatch(l -> l.getId().equals(id));
        boolean isAdmin = user.getRole().name().equals("ADMIN");
        
        if (!ownsLoan && !isAdmin) {
            throw new AccessDeniedException("Access denied to view this loan schedule");
        }

        // Generate amortization schedule
        Loan loan = userLoans.stream().filter(l -> l.getId().equals(id)).findFirst()
                .orElseGet(() -> loanService.getAllLoansForAdmin().stream().filter(l -> l.getId().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Loan not found")));

        List<EmiScheduleResponse> schedule = new ArrayList<>();
        BigDecimal outstanding = loan.getPrincipal().setScale(4, RoundingMode.HALF_UP);
        BigDecimal monthlyRate = loan.getInterestRate().divide(BigDecimal.valueOf(12L * 100L), 8, RoundingMode.HALF_UP);
        BigDecimal emi = loan.getEmiAmount();
        
        LocalDate dueDate = loan.getNextDueDate() != null ? loan.getNextDueDate() : LocalDate.now().plusMonths(1);

        for (int i = 1; i <= loan.getTenureMonths(); i++) {
            BigDecimal interest = outstanding.multiply(monthlyRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal principalPaid = emi.subtract(interest).setScale(4, RoundingMode.HALF_UP);
            
            // Adjust last payment
            if (outstanding.compareTo(principalPaid) < 0 || i == loan.getTenureMonths()) {
                principalPaid = outstanding;
                emi = principalPaid.add(interest);
            }
            
            outstanding = outstanding.subtract(principalPaid).setScale(4, RoundingMode.HALF_UP);
            
            schedule.add(EmiScheduleResponse.builder()
                    .installmentNo(i)
                    .emi(emi)
                    .interest(interest)
                    .principalPaid(principalPaid)
                    .remainingBalance(outstanding.max(BigDecimal.ZERO))
                    .dueDate(dueDate.plusMonths(i - 1))
                    .build());
            
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }
        
        return ResponseEntity.ok(schedule);
    }
}
