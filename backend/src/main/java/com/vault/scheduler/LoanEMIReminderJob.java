package com.vault.scheduler;

import com.vault.loan.entity.Loan;
import com.vault.loan.entity.LoanStatus;
import com.vault.loan.repository.LoanRepository;
import com.vault.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanEMIReminderJob extends QuartzJobBean {

    private final LoanRepository loanRepository;
    private final NotificationService notificationService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("Executing Loan EMI Reminder Job...");
        
        LocalDate targetDueDate = LocalDate.now().plusDays(3);
        List<Loan> dueLoans = loanRepository.findByStatusAndNextDueDateBefore(LoanStatus.ACTIVE, targetDueDate.plusDays(1)).stream()
                .filter(l -> targetDueDate.equals(l.getNextDueDate()))
                .toList();

        for (Loan loan : dueLoans) {
            try {
                String subject = "VAULT Loan Payment Reminder - EMI Due Soon";
                String body = String.format(
                        "Dear %s,\n\n" +
                        "This is a friendly reminder that your EMI installment of INR %s for your %s Loan (#%s) is due on %s.\n\n" +
                        "Outstanding Loan Balance: INR %s\n" +
                        "Repayment Account: %s\n\n" +
                        "To prevent late fees or impact to your credit score, please ensure that your associated repayment account has a sufficient balance. The EMI will be auto-debited or can be manually paid via the portal.\n\n" +
                        "Regards,\nVAULT Retail Lending Operations",
                        loan.getUser().getFullName(),
                        loan.getEmiAmount().toString(),
                        loan.getLoanType().name(),
                        loan.getId().toString().substring(0, 8),
                        loan.getNextDueDate().toString(),
                        loan.getOutstandingAmount().toString(),
                        "XXXX XXXX XXXX " + loan.getAccount().getAccountNumber().substring(loan.getAccount().getAccountNumber().length() - 4)
                );

                notificationService.sendEmailNotification(loan.getUser(), subject, body);
                log.info("Sent EMI reminder email to borrower: {}", loan.getUser().getEmail());
            } catch (Exception e) {
                log.error("Failed to send EMI reminder to user: {}", loan.getUser().getId(), e);
            }
        }
        log.info("Completed Loan EMI Reminder Job");
    }
}
