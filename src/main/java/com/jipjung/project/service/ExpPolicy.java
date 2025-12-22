package com.jipjung.project.service;

/**
 * EXP policy helpers shared across services.
 */
final class ExpPolicy {

    static final long SAVINGS_EXP_UNIT_AMOUNT = 10_000L;
    static final int SAVINGS_EXP_PER_UNIT = 1;
    static final int MAX_EXP_PER_SAVINGS = 500;

    private ExpPolicy() {}

    static int calculateSavingsExp(Long amount) {
        if (amount == null || amount <= 0) {
            return 0;
        }
        long units = amount / SAVINGS_EXP_UNIT_AMOUNT;
        long exp = units * SAVINGS_EXP_PER_UNIT;
        if (exp <= 0) {
            return 0;
        }
        return (int) Math.min(exp, MAX_EXP_PER_SAVINGS);
    }

    static int calculateTargetExp(Long targetAmount) {
        if (targetAmount == null || targetAmount <= 0) {
            return 0;
        }
        long units = (targetAmount + SAVINGS_EXP_UNIT_AMOUNT - 1) / SAVINGS_EXP_UNIT_AMOUNT;
        return units > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) units;
    }
}
