package com.duong.module.common.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilsTest {
    private static KeyPair keys;
    private static String privateKey;
    private static String publicKey;
    private static final String DATA = "test payload";

    @BeforeAll
    static void generateKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    }

    @ParameterizedTest
    @CsvSource({"RS256,SHA256withRSA", "RS384,SHA384withRSA", "RS512,SHA512withRSA"})
    void usesRequestedAlgorithmForSigningAndVerification(String id, String jcaName) throws Exception {
        var algorithm = (SignatureAlgorithm) Jwts.SIG.get().forKey(id);
        var signed = CryptoUtils.generateSignature(DATA, privateKey, algorithm);

        // Verify with independent JCA selection, not just a utility round trip.
        var verifier = Signature.getInstance(jcaName);
        verifier.initVerify(keys.getPublic());
        verifier.update(DATA.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(signed)));

        var signer = Signature.getInstance(jcaName);
        signer.initSign(keys.getPrivate());
        signer.update(DATA.getBytes(StandardCharsets.UTF_8));
        var externalSignature = Base64.getEncoder().encodeToString(signer.sign());
        assertTrue(CryptoUtils.verifySignature(DATA, externalSignature, publicKey, algorithm));
        assertFalse(CryptoUtils.verifySignature("modified payload", externalSignature, publicKey, algorithm));
    }

    @Test
    void defaultsToSha256WithRsa() throws Exception {
        var expected = CryptoUtils.generateRs256Signature(DATA, privateKey);
        assertEquals(expected, CryptoUtils.generateSignature(DATA, privateKey));
        assertEquals(expected, CryptoUtils.generateSignature(DATA, privateKey, null));
        assertTrue(CryptoUtils.verifySignature(DATA, expected, publicKey));
        assertTrue(CryptoUtils.verifySignature(DATA, expected, publicKey, null));
    }

    @Test
    void wrongAlgorithmDoesNotVerify() throws Exception {
        var signed = CryptoUtils.generateSignature(DATA, privateKey, Jwts.SIG.RS512);
        assertFalse(CryptoUtils.verifySignature(DATA, signed, publicKey, Jwts.SIG.RS256));
    }

    @Test
    void rejectsUnsupportedAlgorithmsInsteadOfFallingBack() {
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.generateSignature(DATA, privateKey, Jwts.SIG.ES256));
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.verifySignature(DATA, "unused", publicKey, Jwts.SIG.ES256));
    }
}
