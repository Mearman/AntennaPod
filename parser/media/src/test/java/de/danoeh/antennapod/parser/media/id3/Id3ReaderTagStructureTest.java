package de.danoeh.antennapod.parser.media.id3;

import de.danoeh.antennapod.parser.media.id3.model.TagHeader;
import org.apache.commons.io.input.CountingInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
public class Id3ReaderTagStructureTest {
    private static final int TAG_HEADER_LENGTH = 10;
    private static final int FRAME_HEADER_LENGTH = 10;

    private static ID3Reader readerFor(byte[] data) {
        return new ID3Reader(new CountingInputStream(new ByteArrayInputStream(data)));
    }

    @Test
    public void tagWithoutId3IdentifierIsRejected() {
        ID3ReaderException exception = assertThrows(ID3ReaderException.class,
                () -> readerFor(new byte[] {'X', 'D', '3', 0, 0, 0, 0, 0, 0, 0}).readTagHeader());
        assertEquals("Expected I and got X", exception.getMessage());
    }

    @Test
    public void negativeSkipIsRejected() {
        ID3ReaderException exception = assertThrows(ID3ReaderException.class,
                () -> readerFor(new byte[] {1, 2, 3}).skipBytes(-1));
        assertEquals("Trying to read a negative number of bytes", exception.getMessage());
    }

    @Test
    public void tagSizeIsReadAsSynchsafeInteger() throws Exception {
        byte[] data = {'I', 'D', '3', 4, 0, 0, 0, 0, 1, 0};
        TagHeader header = readerFor(data).readTagHeader();
        assertEquals(128, header.getSize());
    }

    @Test
    public void extendedHeaderIsSkipped() throws Exception {
        byte[] data = Id3ReaderTest.concat(
                new byte[] {'I', 'D', '3', 4, 0, 0x40, 0, 0, 0, 20},
                new byte[] {0, 0, 0, 6, 9, 9});
        ID3Reader reader = readerFor(data);
        reader.readTagHeader();
        assertEquals(data.length, reader.getPosition());
    }

    @Test
    public void frameSizesAreSynchsafeFromVersionFourOnwards() throws Exception {
        byte[] frameHeader = {'T', 'E', 'S', 'T', 0, 0, 1, 0, 0, 0};
        byte[] data = Id3ReaderTest.concat(
                new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0, 1, (byte) FRAME_HEADER_LENGTH},
                frameHeader, new byte[128]);
        ID3Reader reader = readerFor(data);
        reader.readInputStream();
        assertEquals(TAG_HEADER_LENGTH + FRAME_HEADER_LENGTH + 128, reader.getPosition());
    }

    @Test
    public void frameSizesArePlainIntegersBeforeVersionFour() throws Exception {
        byte[] frameHeader = {'T', 'E', 'S', 'T', 0, 0, 0, (byte) 128, 0, 0};
        byte[] data = Id3ReaderTest.concat(
                new byte[] {'I', 'D', '3', 3, 0, 0, 0, 0, 1, (byte) FRAME_HEADER_LENGTH},
                frameHeader, new byte[128]);
        ID3Reader reader = readerFor(data);
        reader.readInputStream();
        assertEquals(TAG_HEADER_LENGTH + FRAME_HEADER_LENGTH + 128, reader.getPosition());
    }

    @Test
    public void readingStopsAtPaddingAfterLastFrame() throws Exception {
        byte[] data = Id3ReaderTest.concat(
                new byte[] {'I', 'D', '3', 3, 0, 0, 0, 0, 0, 40},
                new byte[] {'T', 'E', 'S', 'T', 0, 0, 0, 2, 0, 0, 1, 1},
                new byte[28]);
        ID3Reader reader = readerFor(data);
        reader.readInputStream();
        assertEquals(TAG_HEADER_LENGTH + FRAME_HEADER_LENGTH + 2 + FRAME_HEADER_LENGTH, reader.getPosition());
    }
}
