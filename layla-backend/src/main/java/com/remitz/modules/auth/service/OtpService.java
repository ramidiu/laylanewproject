package com.remitz.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final String OTP_PREFIX = "otp:";
    private static final long OTP_TTL_MINUTES = 10;
    private static final Random RANDOM = new Random();

    private final StringRedisTemplate stringRedisTemplate;

    public String generateOtp() {
        int otp = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(otp);
    }

    public void storeOtp(String email, String otp) {
        String key = OTP_PREFIX + email;
        stringRedisTemplate.opsForValue().set(key, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("OTP stored for email: {}", email);
      
    }

    public boolean validateOtp(String email, String otp) {
        String key = OTP_PREFIX + email;
        String storedOtp = stringRedisTemplate.opsForValue().get(key);
        return otp != null && otp.equals(storedOtp);
    }

    public void deleteOtp(String email) {
        String key = OTP_PREFIX + email;
        stringRedisTemplate.delete(key);
        log.debug("OTP deleted for email: {}", email);
    }
}
