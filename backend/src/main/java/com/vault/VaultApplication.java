package com.vault;

import com.vault.auth.entity.User;
import com.vault.auth.entity.UserRole;
import com.vault.auth.entity.UserStatus;
import com.vault.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
public class VaultApplication {
    public static void main(String[] args) {
        SpringApplication.run(VaultApplication.class, args);
    }

    @Bean
    public CommandLineRunner initAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String adminEmail = "admin@vault.com";
            if (!userRepository.existsByEmail(adminEmail)) {
                User admin = User.builder()
                        .fullName("Vault Admin")
                        .email(adminEmail)
                        .phone("+19999999999")
                        .passwordHash(passwordEncoder.encode("AdminSecurePassword2026!"))
                        .transactionPinHash(passwordEncoder.encode("1234"))
                        .role(UserRole.ADMIN)
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.save(admin);
                System.out.println("========================================");
                System.out.println("ADMIN USER CREATED SUCCESSFULLY!");
                System.out.println("Email: " + adminEmail);
                System.out.println("Password: AdminSecurePassword2026!");
                System.out.println("========================================");
            } else {
                System.out.println("Admin user already exists.");
            }
        };
    }
}
