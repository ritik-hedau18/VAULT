package com.vault.scheduler;

import com.vault.account.entity.Account;
import com.vault.account.entity.AccountStatus;
import com.vault.account.entity.AccountType;
import com.vault.account.repository.AccountRepository;
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
public class FDMaturityAlertJob extends QuartzJobBean {

    private final AccountRepository accountRepository;
    private final NotificationService notificationService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("Executing FD Maturity Alert Job...");
        
        LocalDate targetMaturityDate = LocalDate.now().plusDays(7);
        
        List<Account> maturingFds = accountRepository.findAll().stream()
                .filter(a -> a.getAccountType() == AccountType.FIXED_DEPOSIT 
                        && a.getStatus() == AccountStatus.ACTIVE 
                        && targetMaturityDate.equals(a.getMaturityDate()))
                .toList();

        for (Account fd : maturingFds) {
            try {
                String subject = "VAULT Notice - Fixed Deposit Maturing Soon";
                String body = String.format(
                        "Dear %s,\n\n" +
                        "This is an automated notice regarding your VAULT Fixed Deposit account ending in %s.\n\n" +
                        "Maturity Date: %s\n" +
                        "Principal Amount: INR %s\n" +
                        "Interest Rate: %s%% p.a.\n\n" +
                        "Your deposit will mature in 7 days. If you wish to configure maturity instructions (auto-renewal or disbursement to savings), please log in to the VAULT portal.\n\n" +
                        "Regards,\nVAULT Core Operations Team",
                        fd.getUser().getFullName(),
                        fd.getAccountNumber().substring(fd.getAccountNumber().length() - 4),
                        fd.getMaturityDate().toString(),
                        fd.getBalance().toString(),
                        fd.getInterestRate().toString()
                );

                notificationService.sendEmailNotification(fd.getUser(), subject, body);
                log.info("Sent FD maturity alert email to user: {}", fd.getUser().getEmail());
            } catch (Exception e) {
                log.error("Failed to send FD maturity alert to user: {}", fd.getUser().getId(), e);
            }
        }
        log.info("Completed FD Maturity Alert Job");
    }
}
