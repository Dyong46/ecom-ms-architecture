package com.duong.module.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;

public class UserInfoUtils {
	public static boolean isValidPhoneNumber(String phone) {
		if (StringUtils.isBlank(phone) || phone.length() < 10)
			return false;
		phone = phone.replaceAll("\\+", "").trim();
		if (phone.startsWith("0")) {
			return checkPhoneNumber(phone);
		} else {
			String prefix = phone.substring(0, 2);
			if ("84".equalsIgnoreCase(prefix)) {
				phone.substring(1, phone.length());
				return checkPhoneNumber("0".concat(phone.substring(2, phone.length())));
			}
		}
		return false;
	}

	private static boolean checkPhoneNumber(String vietnamPhone) {
		String vietnamPattern = "\\d{10}|(?:\\d{3}-){2}\\d{4}|\\(\\d{3}\\)\\d{3}-?\\d{4}";
		Pattern p = Pattern.compile(vietnamPattern);
		Matcher match = p.matcher(vietnamPhone);
		return match.matches();
	}

	public static boolean isValidEmail(String email) {
		if (StringUtils.isBlank(email))
			return false;
		EmailValidator validator = EmailValidator.getInstance();
		return validator.isValid(email);
	}

	public static boolean isValidName(String name) {
		if (StringUtils.isBlank(name))
			return false;
		char[] special = { '#', '`', '\'', '"', '_', '“', '”' };
		return !StringUtils.containsAny(name, special);
	}
}
