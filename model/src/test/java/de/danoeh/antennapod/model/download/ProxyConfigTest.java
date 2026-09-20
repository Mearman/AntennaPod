package de.danoeh.antennapod.model.download;

import org.junit.Test;

import java.net.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ProxyConfigTest {

    @Test
    public void constructor_keepsAllValues() {
        ProxyConfig config = new ProxyConfig(Proxy.Type.SOCKS, "proxy.example", 1080, "user", "secret");
        assertEquals(Proxy.Type.SOCKS, config.type);
        assertEquals("proxy.example", config.host);
        assertEquals(1080, config.port);
        assertEquals("user", config.username);
        assertEquals("secret", config.password);
    }

    @Test
    public void constructor_allowsMissingCredentials() {
        ProxyConfig config = new ProxyConfig(Proxy.Type.HTTP, "proxy.example", ProxyConfig.DEFAULT_PORT, null, null);
        assertEquals(ProxyConfig.DEFAULT_PORT, config.port);
        assertNull(config.username);
        assertNull(config.password);
    }
}
