package com.duong.module.common.util;

import java.util.Objects;

public class CalculateUtils {
	public static Long calculateTotalAmount(Long total, Integer discountPer, Long maxDiscountAmount) {
		if (Objects.isNull(total))
			return -1l;

		if (Objects.isNull(discountPer)) {
			discountPer = 0;
		}
		Long discountAmount = total * discountPer / 100;
		if (Objects.nonNull(maxDiscountAmount) && discountAmount > maxDiscountAmount)
			return total - maxDiscountAmount;

		return total - discountAmount;
	}

	public static Long calculateDiscountAmount(Long total, Integer discountPer, Long maxDiscountAmount) {
		if (Objects.isNull(total))
			return -1l;
		if (Objects.isNull(discountPer)) {
			discountPer = 0;
		}
		Long discountAmount = total * discountPer / 100;
		if (Objects.nonNull(maxDiscountAmount) && discountAmount > maxDiscountAmount)
			return maxDiscountAmount;
		return discountAmount;
	}
}
