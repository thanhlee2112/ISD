package com.example.CMS.Repository;

import com.example.CMS.Model.PasswordResetOtp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    /** Row-locked so concurrent guesses cannot each get a free attempt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetOtp> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
