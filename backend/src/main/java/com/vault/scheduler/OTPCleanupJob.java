package com.vault.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class OTPCleanupJob extends QuartzJobBean {

    private final StringRedisTemplate redisTemplate;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("Executing OTP Cleanup Monitoring Job...");
        try {
            // Find active OTP keys (Redis handles eviction automatically, we just monitor / log)
            Set<String> keys = redisTemplate.keys("otp:*");
            int activeOtps = keys != null ? keys.size() : 0;
            log.info("Redis active OTP keys count: {}. Expired keys are auto-evicted by TTL.", activeOtps);
        } catch (Exception e) {
            log.error("Failed to query Redis for OTP keys during cleanup check", e);
        }
    }
}
