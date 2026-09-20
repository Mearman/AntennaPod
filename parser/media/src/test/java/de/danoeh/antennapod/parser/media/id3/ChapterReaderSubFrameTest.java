package de.danoeh.antennapod.parser.media.id3;

import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage;
import de.danoeh.antennapod.parser.media.id3.model.FrameHeader;
import org.apache.commons.io.input.CountingInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
public class ChapterReaderSubFrameTest {
    private static final byte TYPE_OTHER = 0;
    private static final byte TYPE_COVER = 3;

    private static byte[] iso(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] terminated(String text) {
        return Id3ReaderTest.concat(iso(text), new byte[] {0});
    }

    private static byte[] linkFrame(String description, String url) {
        return Id3ReaderTest.concat(new byte[] {ID3Reader.ENCODING_ISO}, terminated(description), terminated(url));
    }

    private static byte[] urlPictureFrame(byte type, String url) {
        return Id3ReaderTest.concat(new byte[] {ID3Reader.ENCODING_ISO}, terminated(ChapterReader.MIME_IMAGE_URL),
                new byte[] {type}, terminated(""), terminated(url));
    }

    private static byte[] embeddedPictureHeader(byte type) {
        return Id3ReaderTest.concat(new byte[] {ID3Reader.ENCODING_ISO}, terminated("image/png"),
                new byte[] {type}, terminated(""));
    }

    private static ChapterReader readerFor(byte[] data) {
        return new ChapterReader(new CountingInputStream(new ByteArrayInputStream(data)));
    }

    private static void readSubFrame(ChapterReader reader, String id, int size, Chapter chapter)
            throws IOException, ID3ReaderException {
        reader.readChapterSubFrame(new FrameHeader(id, size, (short) 0), chapter);
    }

    @Test
    public void linkSubFrameIsUrlDecoded() throws Exception {
        byte[] frame = linkFrame("description", "https://example.com/a%20b");
        Chapter chapter = new Chapter();
        readSubFrame(readerFor(frame), ChapterReader.FRAME_ID_LINK, frame.length, chapter);
        assertEquals("https://example.com/a b", chapter.getLink());
    }

    @Test
    public void linkSubFrameWithBadEscapeLeavesLinkUnset() throws Exception {
        byte[] frame = linkFrame("description", "https://example.com/%zz");
        Chapter chapter = new Chapter();
        readSubFrame(readerFor(frame), ChapterReader.FRAME_ID_LINK, frame.length, chapter);
        assertNull(chapter.getLink());
    }

    @Test
    public void pictureSubFrameWithUrlMimeProvidesImageUrl() throws Exception {
        byte[] frame = urlPictureFrame(TYPE_OTHER, "https://example.com/image.png");
        Chapter chapter = new Chapter();
        readSubFrame(readerFor(frame), ChapterReader.FRAME_ID_PICTURE, frame.length, chapter);
        assertEquals("https://example.com/image.png", chapter.getImageUrl());
    }

    @Test
    public void nonCoverPictureDoesNotReplaceExistingImageUrl() throws Exception {
        byte[] frame = urlPictureFrame(TYPE_OTHER, "https://example.com/second.png");
        Chapter chapter = new Chapter();
        chapter.setImageUrl("https://example.com/first.png");
        readSubFrame(readerFor(frame), ChapterReader.FRAME_ID_PICTURE, frame.length, chapter);
        assertEquals("https://example.com/first.png", chapter.getImageUrl());
    }

    @Test
    public void coverPictureReplacesExistingImageUrl() throws Exception {
        byte[] frame = urlPictureFrame(TYPE_COVER, "https://example.com/cover.png");
        Chapter chapter = new Chapter();
        chapter.setImageUrl("https://example.com/first.png");
        readSubFrame(readerFor(frame), ChapterReader.FRAME_ID_PICTURE, frame.length, chapter);
        assertEquals("https://example.com/cover.png", chapter.getImageUrl());
    }

    @Test
    public void embeddedPictureIsReferencedByPositionAndLength() throws Exception {
        byte[] header = embeddedPictureHeader(TYPE_OTHER);
        byte[] frame = Id3ReaderTest.concat(header, new byte[] {1, 2, 3, 4});
        Chapter chapter = new Chapter();
        readSubFrame(readerFor(frame), ChapterReader.FRAME_ID_PICTURE, frame.length, chapter);
        assertEquals(EmbeddedChapterImage.makeUrl(header.length, 4), chapter.getImageUrl());
    }

    @Test
    public void embeddedNonCoverPictureDoesNotReplaceExistingImageUrl() throws Exception {
        byte[] frame = Id3ReaderTest.concat(embeddedPictureHeader(TYPE_OTHER), new byte[] {1, 2, 3, 4});
        Chapter chapter = new Chapter();
        chapter.setImageUrl("https://example.com/first.png");
        readSubFrame(readerFor(frame), ChapterReader.FRAME_ID_PICTURE, frame.length, chapter);
        assertEquals("https://example.com/first.png", chapter.getImageUrl());
    }

    @Test
    public void embeddedCoverPictureReplacesExistingImageUrl() throws Exception {
        byte[] header = embeddedPictureHeader(TYPE_COVER);
        byte[] frame = Id3ReaderTest.concat(header, new byte[] {1, 2, 3, 4});
        Chapter chapter = new Chapter();
        chapter.setImageUrl("https://example.com/first.png");
        readSubFrame(readerFor(frame), ChapterReader.FRAME_ID_PICTURE, frame.length, chapter);
        assertEquals(EmbeddedChapterImage.makeUrl(header.length, 4), chapter.getImageUrl());
    }

    @Test
    public void unknownSubFrameIsSkippedCompletely() throws Exception {
        byte[] frame = {9, 9, 9, 9, 9};
        ChapterReader reader = readerFor(frame);
        Chapter chapter = new Chapter();
        readSubFrame(reader, "XXXX", frame.length, chapter);
        assertEquals(frame.length, reader.getPosition());
        assertNull(chapter.getTitle());
    }

    @Test
    public void subFrameConsumingMoreThanItsDeclaredSizeIsRejected() {
        byte[] frame = Id3ReaderTest.concat(new byte[] {ID3Reader.ENCODING_ISO}, terminated("image/png"),
                new byte[] {TYPE_OTHER}, terminated(""));
        ID3ReaderException exception = assertThrows(ID3ReaderException.class,
                () -> readSubFrame(readerFor(frame), ChapterReader.FRAME_ID_PICTURE, 3, new Chapter()));
        assertEquals("Trying to read a negative number of bytes", exception.getMessage());
    }

    @Test
    public void chapterFrameReadsAllSubFramesUntilItsDeclaredEnd() throws Exception {
        byte[] chapterHeader = {'C', 'H', '1', 0, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] title = Id3ReaderTest.concat(new byte[] {ID3Reader.ENCODING_ISO}, terminated("Intro"));
        byte[] link = linkFrame("d", "https://example.com");
        byte[] chapterData = Id3ReaderTest.concat(chapterHeader,
                Id3ReaderTest.generateFrameHeader(ChapterReader.FRAME_ID_TITLE, title.length), title,
                Id3ReaderTest.generateFrameHeader(ChapterReader.FRAME_ID_LINK, link.length), link);
        Chapter chapter = readerFor(chapterData).readChapter(
                new FrameHeader(ChapterReader.FRAME_ID_CHAPTER, chapterData.length, (short) 0));
        assertEquals("CH1", chapter.getChapterId());
        assertEquals(5, chapter.getStart());
        assertEquals("Intro", chapter.getTitle());
        assertEquals("https://example.com", chapter.getLink());
    }
}
