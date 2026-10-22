package com.duong.module.common.constant;

import org.apache.commons.lang3.StringUtils;

public enum MbTransactionStatusEnum {
	FAILED, PENDING, PAID;

	public static MbTransactionStatusEnum findByName(String status) {
		if (StringUtils.isBlank(status)) {
			return null;
		}

		for (MbTransactionStatusEnum s : values()) {
			if (s.toString().equalsIgnoreCase(status))
				return s;
		}

		return null;
	}
}
