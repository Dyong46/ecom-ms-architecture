package com.duong.module.common.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.lang.NonNull;

import com.google.common.hash.Hashing;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureAlgorithm;
import lombok.SneakyThrows;

public class CryptoUtils {

	static {
		Security.addProvider(new BouncyCastleProvider());
	}

	public static String generateRs256Signature(String data, String privateKeyString) {
		return generateSignature(data, privateKeyString, Jwts.SIG.RS256);
	}

	public static String generateSignature(String data, String privateKeyString) {
		return generateSignature(data, privateKeyString, null);
	}

	@SneakyThrows
	public static String generateSignature(String data, String privateKeyString, SignatureAlgorithm algorithm) {
		// Create privateKey

		byte[] decodedPrivateKey = Base64.getDecoder().decode(cleanKeyString(privateKeyString));
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedPrivateKey);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = keyFactory.generatePrivate(spec);

		// Sign data with privateKey using algorithm
		Signature signature = Signature.getInstance(signatureAlgorithmName(algorithm));
		signature.initSign(privateKey);
		signature.update(data.getBytes(StandardCharsets.UTF_8));
		byte[] signedSignature = signature.sign();
		return Base64.getEncoder().encodeToString(signedSignature);

	}

	public static boolean verifyRs256Signature(String data, String signedSignature, String publicKeyString)
			throws Exception {
		return verifySignature(data, signedSignature, publicKeyString, Jwts.SIG.RS256);
	}

	public static boolean verifySignature(String data, String signedSignature, String publicKeyString)
			throws Exception {
		return verifySignature(data, signedSignature, publicKeyString, null);
	}

	public static boolean verifySignature(String data, String signedSignature, String publicKeyString,
			SignatureAlgorithm algorithm) throws Exception {

		// Create publicKey
		byte[] decodedPublicKey = Base64.getDecoder().decode(cleanKeyString(publicKeyString));
		X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedPublicKey);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PublicKey publicKey = keyFactory.generatePublic(spec);

		// Verify signature with publicKey using algorithm
		Signature signature = Signature.getInstance(signatureAlgorithmName(algorithm));
		signature.initVerify(publicKey);
		signature.update(data.getBytes(StandardCharsets.UTF_8));
		byte[] decodedSignature = Base64.getDecoder().decode(signedSignature);
		return signature.verify(decodedSignature);

	}

	private static String signatureAlgorithmName(SignatureAlgorithm algorithm) {
		if (algorithm == null) {
			return "SHA256withRSA";
		}
		return switch (algorithm.getId()) {
			case "RS256" -> "SHA256withRSA";
			case "RS384" -> "SHA384withRSA";
			case "RS512" -> "SHA512withRSA";
			default -> throw new IllegalArgumentException("Unsupported RSA signature algorithm: " + algorithm.getId());
		};
	}

	private static String cleanKeyString(@NonNull String keyString) {
		return keyString.replaceAll("-+[\\w\\s]+-+", "").replaceAll("\n", "");
	}

	public static String hashSha256(String input) {
		return Hashing.sha256().hashString(input, StandardCharsets.UTF_8).toString();
	}
}
