package org.example.models;

public class RateLimitConfig {
    private int capacity;
    private int refillTokens;

    // Геттеры и сеттеры
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getRefillTokens() { return refillTokens; }
    public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }
}

