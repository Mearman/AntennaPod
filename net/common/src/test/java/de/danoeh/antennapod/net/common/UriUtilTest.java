package de.danoeh.antennapod.net.common;

import de.danoeh.antennapod.net.common.UriUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Test class for URIUtil
 */
public class UriUtilTest {

    @Test
    public void testGetURIFromRequestUrlShouldNotEncode() {
        final String testUrl = "http://example.com/this%20is%20encoded";
        assertEquals(testUrl, UriUtil.getURIFromRequestUrl(testUrl).toString());
    }

    @Test
    public void testGetURIFromRequestUrlShouldEncode() {
        final String testUrl = "http://example.com/this is not encoded";
        final String expected = "http://example.com/this%20is%20not%20encoded";
        assertEquals(expected, UriUtil.getURIFromRequestUrl(testUrl).toString());
    }

    @Test
    public void testGetURIFromRequestUrlEncodesPathQueryAndFragmentSeparately() {
        final String testUrl = "http://example.com:8080/a b?q=x y#frag ment";
        final String expected = "http://example.com:8080/a%20b?q=x%20y#frag%20ment";
        assertEquals(expected, UriUtil.getURIFromRequestUrl(testUrl).toString());
    }

    @Test
    public void testGetURIFromRequestUrlKeepsUserInfoWhenEncoding() {
        assertEquals("user:pass",
                UriUtil.getURIFromRequestUrl("http://user:pass@example.com/a b").getUserInfo());
    }

    @Test
    public void testGetURIFromRequestUrlRejectsUrlWithoutKnownProtocol() {
        assertThrows(IllegalArgumentException.class, () -> UriUtil.getURIFromRequestUrl("not a url"));
    }
}
