package com.gjl.music.auth.security;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES-256-GCM 加密/解密工具 — 为 Subsonic token+salt 认证保护密码明文副本。
 *
 * <p>使用 PBKDF2 从系统 JWT secret 派生 AES 密钥，12 字节随机 IV 嵌入密文前缀。
 * 密文格式: Base64( IV[12] + ciphertext + GCM_tag[16] )
 */
@Slf4j
public final class SubsonicSecretCrypto {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int AES_KEY_LENGTH = 256;

    private final SecretKey aesKey;

    /**
     * @param jwtSecret JWT 签名密钥，用于派生 AES 加密密钥
     */
    public SubsonicSecretCrypto(String jwtSecret) {
        this.aesKey = deriveKey(jwtSecret);
    }

    /** 加密明文密码 → Base64 密文 */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // IV + ciphertext + GCM tag
            byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("AES 加密失败", e);
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /** 解密 Base64 密文 → 明文密码，解密失败返回 null */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES 解密失败（可能是 JWT secret 已变更）", e);
            return null;
        }
    }

    /** PBKDF2 派生 256-bit AES 密钥 */
    private static SecretKey deriveKey(String secret) {
        try {
            // 固定盐（非机密，仅用于密钥派生）
            byte[] salt = "music-mode-subsonic-2024".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            throw new RuntimeException("无法派生 AES 密钥", e);
        }
    }
}
