package com.duong.module.common.util;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import com.google.common.base.CaseFormat;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Map;
import java.util.Random;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

public class StrUtils {

	public static String normalize(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFC);
	}

	public static CaseFormat getCaseFormat(String s) {
		if (s.matches("[_a-z0-9]*")) {
			return LOWER_UNDERSCORE;
		} else if (s.matches("[_A-Z0-9]*")) {
			return UPPER_UNDERSCORE;
		} else if (s.matches("[-a-z0-9]*")) {
			return LOWER_HYPHEN;
		} else if (s.matches("[a-z0-9]+([A-Z][a-z0-9]*)*")) {
			return LOWER_CAMEL;
		} else if (s.matches("([A-Z][a-z0-9]*)*")) {
			return UPPER_CAMEL;
		}
		return null;
	}

	public static String toCaseFormat(String s, CaseFormat caseFormat) {
		return getCaseFormat(s).to(caseFormat, s);
	}

	public static String toCamelCase(String s) {
		return toCaseFormat(s, LOWER_CAMEL);
	}

	public static String escapeSqlWildcard(String s) {
		if (s != null) {
			return s.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
		} else {
			return null;
		}
	}

	public static String randomString(int targetStringLength) {
		int leftLimit = 97; // letter 'a'
		int rightLimit = 122; // letter 'z'
		Random random = new Random();
		StringBuilder buffer = new StringBuilder(targetStringLength);
		for (int i = 0; i < targetStringLength; i++) {
			int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
			buffer.append((char) randomLimitedInt);
		}
		return buffer.toString();
	}

	public static String buildSqlLikePattern(String s) {
		String escapedString = StringUtils.trimToNull(escapeSqlWildcard(s));
		return escapedString != null ? "%" + escapedString + "%" : null;
	}

	public static String extractUsernameFromEmail(String email) {
		return email.replaceAll("@.*", "").toLowerCase();
	}

	public static String replace(String source, Map<String, Object> params) {
		return replace(source, params, true);
	}

	public static String replace(String source, Map<String, Object> params, boolean enableSubstitutionInVariables) {
		return replace(source, params, "{", "}", enableSubstitutionInVariables);
	}

	public static String replace(String source, Map<String, Object> params, String prefix, String suffix) {
		return replace(source, params, prefix, suffix, true);
	}

	public static String replace(String source, Map<String, Object> params, String prefix, String suffix,
			boolean enableSubstitutionInVariables) {
		StringSubstitutor stringSubstitutor = new StringSubstitutor(params, prefix, suffix);
		stringSubstitutor.setEnableSubstitutionInVariables(enableSubstitutionInVariables);
		return stringSubstitutor.replace(source);
	}

	public static boolean checkMailDomain(String email, Collection<String> allowedDomains) {
		if (email == null) {
			return false;
		} else if (CollectionUtils.isEmpty(allowedDomains)) {
			return true;
		} else {
			return allowedDomains.stream().anyMatch(domain -> email.endsWith("@" + domain));
		}
	}

}
