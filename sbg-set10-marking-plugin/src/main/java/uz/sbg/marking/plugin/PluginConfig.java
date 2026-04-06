package uz.sbg.marking.plugin;

import ru.crystals.pos.spi.IntegrationProperties;

public class PluginConfig {
    public static final String URL             = "marking.service.url";
    public static final String CONNECT_TIMEOUT = "marking.service.connect.timeout.ms";
    public static final String READ_TIMEOUT    = "marking.service.read.timeout.ms";

    private final String baseUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public PluginConfig(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = baseUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public static PluginConfig fromProperties(IntegrationProperties properties) {
        String url = properties.getServiceProperties().get(URL);
        if (url == null || url.trim().isEmpty()) {
            url = "http://localhost:8080";
        }
        int connectTimeoutMs = properties.getServiceProperties().getInt(CONNECT_TIMEOUT, 3000);
        int readTimeoutMs    = properties.getServiceProperties().getInt(READ_TIMEOUT, 5000);
        return new PluginConfig(url.trim(), connectTimeoutMs, readTimeoutMs);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }
}
