package com.gjl.music.config;


public record ConfigChangedEvent(String configKey, String oldValue, String newValue) {

    public int asInt() {
        return Integer.parseInt(newValue);
    }

    public int asInt(int fallback) {
        try { return Integer.parseInt(newValue); }
        catch (NumberFormatException e) { return fallback; }
    }

    public long asLong() {
        return Long.parseLong(newValue);
    }

    public long asLong(long fallback) {
        try { return Long.parseLong(newValue); }
        catch (NumberFormatException e) { return fallback; }
    }

    public double asDouble() {
        return Double.parseDouble(newValue);
    }

    public double asDouble(double fallback) {
        try { return Double.parseDouble(newValue); }
        catch (NumberFormatException e) { return fallback; }
    }

    public boolean asBoolean() {
        return Boolean.parseBoolean(newValue);
    }
}
