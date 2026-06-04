package com.remitz.modules.payin.volume.service;

import com.remitz.modules.payin.volume.config.VolumeProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
public class VolumeSignatureService {

    private final VolumeProperties volumeProperties;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            String pem = new RestTemplate().getForObject(volumeProperties.getPemUrl(), String.class);
            if (pem != null) {
                publicKey = KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(Base64.getMimeDecoder().decode(pem)));
                log.info("Volume Pay RSA public key loaded successfully");
            }
        } catch (Exception e) {
            log.warn("Could not load Volume Pay public key from {}: {}", volumeProperties.getPemUrl(), e.getMessage());
        }
    }

    public boolean verify(String jsonString, String signature) {
        if (publicKey == null) {
            log.warn("Volume Pay public key not loaded — cannot verify webhook signature");
            return false;
        }
        return verify(jsonString.getBytes(StandardCharsets.UTF_8), signature);
    }

    public boolean verify(byte[] data, String signature) {
        if (publicKey == null || ObjectUtils.isEmpty(signature)) return false;
        String[] parts = signature.split(" ");
        if (parts.length != 2) return false;
        try {
            Signature sig = Signature.getInstance(parts[0]);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(Base64.getDecoder().decode(parts[1]));
        } catch (Exception e) {
            log.warn("Volume webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
