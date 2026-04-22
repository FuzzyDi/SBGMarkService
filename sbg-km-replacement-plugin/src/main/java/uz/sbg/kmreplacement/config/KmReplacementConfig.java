package uz.sbg.kmreplacement.config;

import ru.crystals.pos.spi.PropertiesReader;

/**
 * Неизменяемая конфигурация плагина. Читается из IntegrationProperties
 * на старте в {@code @PostConstruct} и больше не меняется.
 *
 * <p>Ключи URL / connect / read — совпадают с лицензионным
 * {@code sbg-set10-marking-plugin} и задаются в миллисекундах (как было).
 * Новые опции (TTL оверлея, max.attempts) — в секундах.</p>
 *
 * <p>Подтверждённое API {@link PropertiesReader} (см.
 * sbg-set10-marking-plugin/PluginConfig):</p>
 * <ul>
 *   <li>{@code String get(String key)} — возможен {@code null};</li>
 *   <li>{@code int getInt(String key, int defaultValue)}.</li>
 * </ul>
 */
public final class KmReplacementConfig {

    // Лицензионные ключи — имена НЕ менять.
    public static final String KEY_URL          = "marking.service.url";
    public static final String KEY_CONNECT_MS   = "marking.service.connect.timeout.ms";
    public static final String KEY_READ_MS      = "marking.service.read.timeout.ms";
    // Новые опции — специфичны для overlay-функциональности.
    public static final String KEY_QR_TTL_SEC   = "marking.service.qr.ttl.sec";
    public static final String KEY_MAX_ATTEMPTS = "marking.service.max.attempts";
    /**
     * true → резолвер {@code StubReplacementResolver} (без сети, для стендовой
     * проверки UX). false → {@code HttpReplacementResolver} (реальные вызовы
     * в sbg-marking-server-py). Значение читается как {@code getInt != 0}.
     */
    public static final String KEY_STUB_ENABLED = "marking.service.stub.enabled";
    /**
     * true → режим автоподмены КМ с QR overlay (полный сценарий плагина).
     * false → плагин работает как обычный валидатор маркировки: КМ всё равно
     * проверяется на backend, но ветка {@code REPLACE_WITH} понижается до
     * REJECT без показа overlay и без создания state/резерва на стороне
     * плагина. Полезно для магазинов без FIFO-пула или для временного
     * отключения автоподмены без переразвёртывания.
     */
    public static final String KEY_REPLACEMENT_ENABLED = "marking.service.replacement.enabled";

    public static final String DEFAULT_URL          = "http://localhost:8080";
    public static final int    DEFAULT_CONNECT_MS   = 3000;
    public static final int    DEFAULT_READ_MS      = 5000;
    public static final int    DEFAULT_QR_TTL_SEC   = 60;
    public static final int    DEFAULT_MAX_ATTEMPTS = 2;
    public static final boolean DEFAULT_STUB_ENABLED        = false;
    public static final boolean DEFAULT_REPLACEMENT_ENABLED = true;

    private final String  baseUrl;
    private final int     connectTimeoutMs;
    private final int     readTimeoutMs;
    private final int     qrTtlMs;
    private final int     maxAttempts;
    private final boolean stubEnabled;
    private final boolean replacementEnabled;

    public KmReplacementConfig(String baseUrl,
                               int connectTimeoutMs,
                               int readTimeoutMs,
                               int qrTtlMs,
                               int maxAttempts) {
        this(baseUrl, connectTimeoutMs, readTimeoutMs, qrTtlMs, maxAttempts,
                DEFAULT_STUB_ENABLED, DEFAULT_REPLACEMENT_ENABLED);
    }

    public KmReplacementConfig(String baseUrl,
                               int connectTimeoutMs,
                               int readTimeoutMs,
                               int qrTtlMs,
                               int maxAttempts,
                               boolean stubEnabled) {
        this(baseUrl, connectTimeoutMs, readTimeoutMs, qrTtlMs, maxAttempts,
                stubEnabled, DEFAULT_REPLACEMENT_ENABLED);
    }

    public KmReplacementConfig(String baseUrl,
                               int connectTimeoutMs,
                               int readTimeoutMs,
                               int qrTtlMs,
                               int maxAttempts,
                               boolean stubEnabled,
                               boolean replacementEnabled) {
        this.baseUrl = baseUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.qrTtlMs = qrTtlMs;
        this.maxAttempts = maxAttempts;
        this.stubEnabled = stubEnabled;
        this.replacementEnabled = replacementEnabled;
    }

    public static KmReplacementConfig fromProperties(PropertiesReader p) {
        String url = (p == null) ? null : p.get(KEY_URL);
        if (url == null || url.trim().isEmpty()) {
            url = DEFAULT_URL;
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        int connectMs = (p == null) ? DEFAULT_CONNECT_MS  : p.getInt(KEY_CONNECT_MS,  DEFAULT_CONNECT_MS);
        int readMs    = (p == null) ? DEFAULT_READ_MS     : p.getInt(KEY_READ_MS,     DEFAULT_READ_MS);
        int ttlSec    = (p == null) ? DEFAULT_QR_TTL_SEC  : p.getInt(KEY_QR_TTL_SEC,  DEFAULT_QR_TTL_SEC);
        int attempts  = (p == null) ? DEFAULT_MAX_ATTEMPTS : p.getInt(KEY_MAX_ATTEMPTS, DEFAULT_MAX_ATTEMPTS);

        if (connectMs < 100)  connectMs = DEFAULT_CONNECT_MS;
        if (readMs    < 100)  readMs    = DEFAULT_READ_MS;
        if (ttlSec    < 10)   ttlSec    = DEFAULT_QR_TTL_SEC;
        if (attempts  < 1)    attempts  = DEFAULT_MAX_ATTEMPTS;

        boolean stub = (p == null)
                ? DEFAULT_STUB_ENABLED
                : (p.getInt(KEY_STUB_ENABLED, DEFAULT_STUB_ENABLED ? 1 : 0) != 0);

        boolean replacement = (p == null)
                ? DEFAULT_REPLACEMENT_ENABLED
                : (p.getInt(KEY_REPLACEMENT_ENABLED, DEFAULT_REPLACEMENT_ENABLED ? 1 : 0) != 0);

        return new KmReplacementConfig(url, connectMs, readMs, ttlSec * 1000, attempts, stub, replacement);
    }

    public String  getBaseUrl()          { return baseUrl; }
    public int     getConnectTimeoutMs() { return connectTimeoutMs; }
    public int     getReadTimeoutMs()    { return readTimeoutMs; }
    public int     getQrTtlMs()          { return qrTtlMs; }
    public int     getMaxAttempts()      { return maxAttempts; }
    public boolean isStubEnabled()        { return stubEnabled; }
    public boolean isReplacementEnabled() { return replacementEnabled; }

    @Override
    public String toString() {
        return "KmReplacementConfig{baseUrl=" + baseUrl
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", qrTtlMs=" + qrTtlMs
                + ", maxAttempts=" + maxAttempts
                + ", stubEnabled=" + stubEnabled
                + ", replacementEnabled=" + replacementEnabled + "}";
    }
}
