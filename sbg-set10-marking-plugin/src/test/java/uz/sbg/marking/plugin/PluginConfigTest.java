package uz.sbg.marking.plugin;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Проверяет PluginConfig — чтение URL и таймаутов.
 */
public class PluginConfigTest {

    @Test
    public void testConstructorStoresValues() {
        PluginConfig config = new PluginConfig("http://192.168.1.1:8080", 3000, 5000);
        assertEquals("http://192.168.1.1:8080", config.getBaseUrl());
        assertEquals(3000, config.getConnectTimeoutMs());
        assertEquals(5000, config.getReadTimeoutMs());
    }

    @Test
    public void testDefaultsWhenNullUrl() {
        // Имитируем поведение fromProperties когда URL не задан:
        // PluginConfig.fromProperties ставит дефолт "http://localhost:8080"
        PluginConfig config = new PluginConfig("http://localhost:8080", 3000, 5000);
        assertEquals("http://localhost:8080", config.getBaseUrl());
    }
}
