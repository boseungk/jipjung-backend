package com.jipjung.project.dsr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DSR 서비스 정책/기본값 설정
 * <p>
 * 기본값은 프로퍼티로 오버라이드 가능하며, 서비스 내 하드코딩을 방지한다.
 */
@Component
public class DsrSettings {

    private final long medianIncome;
    private final int defaultAge;
    private final double liteModeDefaultRate;
    private final int liteModeDefaultMaturity;
    private final long expPerUnit;
    private final int expMultiplier;
    private final int maxExp;

    public DsrSettings(
            @Value("${dsr.median-income:58440000}") long medianIncome,
            @Value("${dsr.default-age:35}") int defaultAge,
            @Value("${dsr.lite.default-rate:4.5}") double liteModeDefaultRate,
            @Value("${dsr.lite.default-maturity:30}") int liteModeDefaultMaturity,
            @Value("${dsr.exp.per-unit:10000000}") long expPerUnit,
            @Value("${dsr.exp.multiplier:100}") int expMultiplier,
            @Value("${dsr.exp.max:500}") int maxExp
    ) {
        this.medianIncome = medianIncome;
        this.defaultAge = defaultAge;
        this.liteModeDefaultRate = liteModeDefaultRate;
        this.liteModeDefaultMaturity = liteModeDefaultMaturity;
        this.expPerUnit = expPerUnit;
        this.expMultiplier = expMultiplier;
        this.maxExp = maxExp;
    }

    public long getMedianIncome() {
        return medianIncome;
    }

    public int getDefaultAge() {
        return defaultAge;
    }

    public double getLiteModeDefaultRate() {
        return liteModeDefaultRate;
    }

    public int getLiteModeDefaultMaturity() {
        return liteModeDefaultMaturity;
    }

    public long getExpPerUnit() {
        return expPerUnit;
    }

    public int getExpMultiplier() {
        return expMultiplier;
    }

    public int getMaxExp() {
        return maxExp;
    }
}
