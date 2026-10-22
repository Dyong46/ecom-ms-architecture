package com.duong.module.common.util;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

public class EvnUtils {

	public static final String DEFAULT_PROFILE = "default";

	public static String buildActiveProfilesString(Environment environment) {
		return StringUtils.defaultIfBlank(Stream.of(environment.getActiveProfiles()).map(StringUtils::trimToNull)
				.filter(Objects::nonNull).collect(Collectors.joining("-")), DEFAULT_PROFILE);
	}
}
