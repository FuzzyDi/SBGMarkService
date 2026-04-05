package uz.sbg.marking.plugin;

import ru.crystals.pos.spi.IntegrationProperties;

import java.time.Duration;

public class PluginConfig {
    public static final String URL = "marking.service.url";
    public static final String CONNECT_TIMEOUT = "marking.service.connect.timeout.ms";
    public static final String READ_TIMEOUT = "marking.service.read.timeout.ms";

    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public PluginConfig(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        this.baseUrl = baseUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public static PluginConfig fromProperties(IntegrationProperties properties) {
        String url = properties.getServiceProperties().get(URL);
        if (url == null || url.trim().isEmpty()) {
            url = "http://localhost:8080";
        }
        int connectTimeoutMs = properties.getServiceProperties().getInt(CONNECT_TIMEOUT, 3000);
        int readTimeoutMs = properties.getServiceProperties().getInt(READ_TIMEOUT, 5000);
        return new PluginConfig(url, Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs));
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }
}
