package com.vault.config;

import com.vault.scheduler.*;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    // 1. Daily Interest Job
    @Bean
    public JobDetail dailyInterestJobDetail() {
        return JobBuilder.newJob(DailyInterestJob.class)
                .withIdentity("dailyInterestJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger dailyInterestJobTrigger(JobDetail dailyInterestJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(dailyInterestJobDetail)
                .withIdentity("dailyInterestTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 * * ?")) // Midnight daily
                .build();
    }

    // 2. Monthly Statement Job
    @Bean
    public JobDetail monthlyStatementJobDetail() {
        return JobBuilder.newJob(MonthlyStatementJob.class)
                .withIdentity("monthlyStatementJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger monthlyStatementJobTrigger(JobDetail monthlyStatementJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(monthlyStatementJobDetail)
                .withIdentity("monthlyStatementTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 1 1 * ?")) // 1:00 AM on 1st day of month
                .build();
    }

    // 3. FD Maturity Alert Job
    @Bean
    public JobDetail fdMaturityAlertJobDetail() {
        return JobBuilder.newJob(FDMaturityAlertJob.class)
                .withIdentity("fdMaturityAlertJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger fdMaturityAlertJobTrigger(JobDetail fdMaturityAlertJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(fdMaturityAlertJobDetail)
                .withIdentity("fdMaturityAlertTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 9 * * ?")) // 9:00 AM daily
                .build();
    }

    // 4. Loan EMI Reminder Job
    @Bean
    public JobDetail loanEmiReminderJobDetail() {
        return JobBuilder.newJob(LoanEMIReminderJob.class)
                .withIdentity("loanEmiReminderJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger loanEmiReminderJobTrigger(JobDetail loanEmiReminderJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(loanEmiReminderJobDetail)
                .withIdentity("loanEmiReminderTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 8 * * ?")) // 8:00 AM daily
                .build();
    }

    // 5. OTP Cleanup Job
    @Bean
    public JobDetail otpCleanupJobDetail() {
        return JobBuilder.newJob(OTPCleanupJob.class)
                .withIdentity("otpCleanupJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger otpCleanupJobTrigger(JobDetail otpCleanupJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(otpCleanupJobDetail)
                .withIdentity("otpCleanupTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 */5 * * * ?")) // Every 5 minutes
                .build();
    }

    // 6. Interbank Transfer Job
    @Bean
    public JobDetail interBankTransferJobDetail() {
        return JobBuilder.newJob(InterBankTransferJob.class)
                .withIdentity("interBankTransferJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger interBankTransferJobTrigger(JobDetail interBankTransferJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(interBankTransferJobDetail)
                .withIdentity("interBankTransferTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 */30 * * * ?")) // Every 30 minutes
                .build();
    }
}
