package com.fintech.wallet.common.util;

public final class MoneyUtils {

	private MoneyUtils() {
	}

	public static long add(long left, long right) {
		return Math.addExact(left, right);
	}

	public static long negate(long amount) {
		return Math.negateExact(amount);
	}

	public static boolean isPositive(long amount) {
		return amount > 0;
	}
}
