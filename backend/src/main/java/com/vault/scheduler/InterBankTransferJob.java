package com.vault.scheduler;

import com.vault.transaction.TransactionEvent;
import com.vault.transaction.entity.Transaction;
import com.vault.transaction.entity.TransactionStatus;
import com.vault.transaction.entity.TransactionType;
import com.vault.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterBankTransferJob extends QuartzJobBean {

    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("Executing Interbank Transfer Processing Job...");
        
        List<Transaction> pendingTransfers = transactionRepository.findAll().stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER 
                        && t.getStatus() == TransactionStatus.PENDING 
                        && t.getToAccount() == null)
                .toList();

        for (Transaction txn : pendingTransfers) {
            try {
                log.info("Clearing interbank transfer: Ref {}, Amount {}", txn.getReferenceNumber(), txn.getAmount());
                
                // Simulate gateway clearing (NEFT/IMPS)
                txn.setStatus(TransactionStatus.SUCCESS);
                txn.setCompletedAt(LocalDateTime.now());
                transactionRepository.save(txn);
                
                // Trigger event-driven audit and email/SMS alerts to borrower
                eventPublisher.publishEvent(new TransactionEvent(this, txn, txn.getFromAccount().getUser().getId()));
                
                log.info("Interbank transfer cleared successfully: {}", txn.getReferenceNumber());
            } catch (Exception e) {
                log.error("Failed to clear interbank transaction: {}", txn.getId(), e);
            }
        }
        log.info("Completed Interbank Transfer Processing Job");
    }
}
