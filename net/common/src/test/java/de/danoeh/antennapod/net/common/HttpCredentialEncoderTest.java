package de.danoeh.antennapod.net.common;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
public class HttpCredentialEncoderTest {

    @Test
    public void testEncodePrefixesBasicAndBase64EncodesUsernameAndPassword() {
        assertEquals("Basic dXNlcjpwYXNz", HttpCredentialEncoder.encode("user", "pass", "UTF-8"));
    }

    @Test
    public void testEncodeWithEmptyPasswordKeepsSeparator() {
        assertEquals("Basic dXNlcjo=", HttpCredentialEncoder.encode("user", "", "UTF-8"));
    }

    @Test
    public void testEncodeWithEmptyUsernameAndPasswordEncodesOnlySeparator() {
        assertEquals("Basic Og==", HttpCredentialEncoder.encode("", "", "UTF-8"));
    }

    @Test
    public void testEncodeDoesNotInsertLineBreaksForLongCredentials() {
        String longPassword = "p".repeat(200);
        String encoded = HttpCredentialEncoder.encode("user", longPassword, "UTF-8");
        assertEquals(-1, encoded.indexOf('\n'));
    }

    @Test
    public void testEncodeUsesRequestedCharsetForNonAsciiCharacters() {
        String username = "jürgen";
        String password = "pä:ss";
        assertEquals("Basic avxyZ2VuOnDkOnNz", HttpCredentialEncoder.encode(username, password, "ISO-8859-1"));
        assertEquals("Basic asO8cmdlbjpww6Q6c3M=", HttpCredentialEncoder.encode(username, password, "UTF-8"));
    }

    @Test
    public void testEncodeGivesSameResultForBothCharsetsWhenCredentialsAreAscii() {
        assertEquals(HttpCredentialEncoder.encode("user", "pass", "ISO-8859-1"),
                HttpCredentialEncoder.encode("user", "pass", "UTF-8"));
        assertNotEquals(HttpCredentialEncoder.encode("user", "päss", "ISO-8859-1"),
                HttpCredentialEncoder.encode("user", "päss", "UTF-8"));
    }

    @Test
    public void testEncodeWithUnknownCharsetThrowsAssertionError() {
        assertThrows(AssertionError.class, () -> HttpCredentialEncoder.encode("user", "pass", "not-a-charset"));
    }
}
