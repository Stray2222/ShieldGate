package org.example.models;

import java.util.ArrayList;
import java.util.List;

public class SecurityConfig {
    private int maxDepth;
    private int maxStringLen;
    private int maxViolations;
    private int banTimeMinutes;
    private int maxObjectSize;
    private int maxTotalElements;

    // --- НОВЫЕ ПОЛЯ ДЛЯ АВТОРИЗАЦИИ ---
    private List<String> whiteListIps = new ArrayList<>();
    private String apiSecretKey;

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxStringLen() {
        return maxStringLen;
    }

    public void setMaxStringLen(int maxStringLen) {
        this.maxStringLen = maxStringLen;
    }

    public int getMaxViolations() {
        return maxViolations;
    }

    public void setMaxViolations(int maxViolations) {
        this.maxViolations = maxViolations;
    }

    public int getBanTimeMinutes() {
        return banTimeMinutes;
    }

    public void setBanTimeMinutes(int banTimeMinutes) {
        this.banTimeMinutes = banTimeMinutes;
    }

    public int getMaxTotalElements() {
        return maxTotalElements;
    }

    public void setMaxTotalElements(int maxTotalElements) {
        this.maxTotalElements = maxTotalElements;
    }

    public int getMaxObjectSize() {
        return maxObjectSize;
    }

    public void setMaxObjectSize(int maxObjectSize) {
        this.maxObjectSize = maxObjectSize;
    }

    // --- ГЕТТЕРЫ И СЕТТЕРЫ ДЛЯ НОВЫХ ПОЛЕЙ ---
    public List<String> getWhiteListIps() {
        return whiteListIps;
    }

    public void setWhiteListIps(List<String> whiteListIps) {
        this.whiteListIps = whiteListIps;
    }

    public String getApiSecretKey() {
        return apiSecretKey;
    }

    public void setApiSecretKey(String apiSecretKey) {
        this.apiSecretKey = apiSecretKey;
    }
}
