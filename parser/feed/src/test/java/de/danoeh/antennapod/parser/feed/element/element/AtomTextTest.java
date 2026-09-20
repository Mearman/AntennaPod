package de.danoeh.antennapod.parser.feed.element.element;

import de.danoeh.antennapod.parser.feed.element.AtomText;
import de.danoeh.antennapod.parser.feed.namespace.Atom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

/**
 * Unit test for {@link AtomText}.
 */
@RunWith(RobolectricTestRunner.class)
public class AtomTextTest {

    private static final String[][] TEST_DATA = {
            {"&gt;", ">"},
            {">", ">"},
            {"&lt;Fran&ccedil;ais&gt;", "<Français>"},
            {"ßÄÖÜ", "ßÄÖÜ"},
            {"&quot;", "\""},
            {"&szlig;", "ß"},
            {"&#8217;", "’"},
            {"&#x2030;", "‰"},
            {"&euro;", "€"}
    };

    @Test
    public void testProcessingHtml() {
        for (String[] pair : TEST_DATA) {
            final AtomText atomText = new AtomText("", new Atom(), AtomText.TYPE_HTML);
            atomText.setContent(pair[0]);
            assertEquals(pair[1], atomText.getProcessedContent());
        }
    }

    @Test
    public void testUntypedContentIsReturnedUnchanged() {
        final AtomText atomText = new AtomText("", new Atom(), null);
        atomText.setContent("&gt; <b>raw</b>");
        assertEquals("&gt; <b>raw</b>", atomText.getProcessedContent());
    }

    @Test
    public void testXhtmlContentIsReturnedUnchanged() {
        final AtomText atomText = new AtomText("", new Atom(), "xhtml");
        atomText.setContent("&gt; <b>raw</b>");
        assertEquals("&gt; <b>raw</b>", atomText.getProcessedContent());
    }

    @Test
    public void testUnknownTypeIsTreatedAsPlainText() {
        final AtomText atomText = new AtomText("", new Atom(), "text");
        atomText.setContent("&gt; <b>raw</b>");
        assertEquals("&gt; <b>raw</b>", atomText.getProcessedContent());
    }
}
