package de.danoeh.antennapod.parser.media.vorbis;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.util.List;

import de.danoeh.antennapod.model.feed.Chapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class VorbisCommentStreamTest {

    private static VorbisCommentMetadataReader readMetadata(byte[] stream) throws VorbisCommentReaderException {
        VorbisCommentMetadataReader reader = new VorbisCommentMetadataReader(new ByteArrayInputStream(stream));
        reader.readInputStream();
        return reader;
    }

    private static List<Chapter> readChapters(byte[] stream) throws VorbisCommentReaderException {
        VorbisCommentChapterReader reader = new VorbisCommentChapterReader(new ByteArrayInputStream(stream));
        reader.readInputStream();
        return reader.getChapters();
    }

    @Test
    public void vorbisCommentIsReadFromOggStream() throws Exception {
        byte[] stream = VorbisTestStreams.vorbisStream(
                VorbisTestStreams.commentBlock("vendor", "TITLE=Episode", "DESCRIPTION=Shown notes"));
        assertEquals("Shown notes", readMetadata(stream).getDescription());
    }

    @Test
    public void opusTagsAreReadFromOggStream() throws Exception {
        byte[] stream = VorbisTestStreams.opusStream(
                VorbisTestStreams.commentBlock("vendor", "SYNOPSIS=Opus synopsis"));
        assertEquals("Opus synopsis", readMetadata(stream).getDescription());
    }

    @Test
    public void nativeFlacCommentBlockIsFoundAfterOtherBlocks() throws Exception {
        byte[] stream = VorbisTestStreams.flacStreamWithComments(
                VorbisTestStreams.commentBlock("vendor", "comment=Flac comment"));
        assertEquals("Flac comment", readMetadata(stream).getDescription());
    }

    @Test
    public void longestValueAmongDescriptionKeysWins() throws Exception {
        byte[] stream = VorbisTestStreams.vorbisStream(VorbisTestStreams.commentBlock("vendor",
                "description=short", "comment=the longest of all values", "synopsis=medium value"));
        assertEquals("the longest of all values", readMetadata(stream).getDescription());
    }

    @Test
    public void keysAreMatchedCaseInsensitively() throws Exception {
        byte[] stream = VorbisTestStreams.vorbisStream(
                VorbisTestStreams.commentBlock("vendor", "DeScRiPtIoN=Mixed case"));
        assertEquals("Mixed case", readMetadata(stream).getDescription());
    }

    @Test
    public void unrelatedKeysAreSkippedWithoutAffectingDescription() throws Exception {
        byte[] stream = VorbisTestStreams.vorbisStream(
                VorbisTestStreams.commentBlock("vendor", "ARTIST=Someone", "ALBUM=Something"));
        assertNull(readMetadata(stream).getDescription());
    }

    @Test
    public void commentHeaderSpanningTwoPagesIsReadCompletely() throws Exception {
        byte[] block = VorbisTestStreams.commentBlock("vendor", "title=Episode",
                "description=Text that is cut in the middle by a page boundary");
        byte[] stream = VorbisTestStreams.vorbisStreamSplitAcrossPages(block, 40);
        assertEquals("Text that is cut in the middle by a page boundary", readMetadata(stream).getDescription());
    }

    @Test
    public void flacWithoutCommentBlockYieldsNoDescription() throws Exception {
        assertNull(readMetadata(VorbisTestStreams.flacStreamWithoutComments()).getDescription());
    }

    @Test
    public void streamWithoutOggCapturePatternYieldsNoDescription() throws Exception {
        assertNull(readMetadata("this is not an ogg stream at all".getBytes()).getDescription());
    }

    @Test
    public void unrealisticallyLongCommentIsRejected() {
        byte[] block = VorbisTestStreams.concat(
                VorbisTestStreams.littleEndianInt(6), "vendor".getBytes(),
                VorbisTestStreams.littleEndianInt(1),
                VorbisTestStreams.littleEndianInt(21L * 1024 * 1024), "description".getBytes());
        VorbisCommentReaderException exception = assertThrows(VorbisCommentReaderException.class,
                () -> readMetadata(VorbisTestStreams.vorbisStream(block)));
        assertTrue(exception.getMessage().startsWith("User comment unrealistically long."));
    }

    @Test
    public void chaptersAreBuiltFromKeysWithNamesAndLinks() throws Exception {
        byte[] stream = VorbisTestStreams.vorbisStream(VorbisTestStreams.commentBlock("vendor",
                "CHAPTER001=00:00:00.000", "CHAPTER001NAME=Intro",
                "CHAPTER002=00:01:02.500 --> 00:02:00", "CHAPTER002NAME=Main",
                "CHAPTER002URL=https://example.com/main"));
        List<Chapter> chapters = readChapters(stream);
        assertEquals(2, chapters.size());
        assertEquals("1", chapters.get(0).getChapterId());
        assertEquals("Intro", chapters.get(0).getTitle());
        assertEquals(0, chapters.get(0).getStart());
        assertEquals("2", chapters.get(1).getChapterId());
        assertEquals("Main", chapters.get(1).getTitle());
        assertEquals(62000, chapters.get(1).getStart());
        assertEquals("https://example.com/main", chapters.get(1).getLink());
    }

    @Test
    public void attributesOfUnknownChaptersAndUnknownAttributesAreIgnored() throws Exception {
        byte[] stream = VorbisTestStreams.vorbisStream(VorbisTestStreams.commentBlock("vendor",
                "CHAPTER007NAME=Orphan", "CHAPTER007URL=https://example.com/orphan",
                "CHAPTER001=00:00:05.000", "CHAPTER001IMAGE=cover.png", "TITLE=Episode"));
        List<Chapter> chapters = readChapters(stream);
        assertEquals(1, chapters.size());
        assertNull(chapters.get(0).getTitle());
        assertNull(chapters.get(0).getLink());
        assertEquals(5000, chapters.get(0).getStart());
    }

    @Test
    public void duplicateChapterIdIsRejected() {
        byte[] stream = VorbisTestStreams.vorbisStream(VorbisTestStreams.commentBlock("vendor",
                "CHAPTER001=00:00:00.000", "CHAPTER001=00:00:10.000"));
        VorbisCommentReaderException exception =
                assertThrows(VorbisCommentReaderException.class, () -> readChapters(stream));
        assertTrue(exception.getMessage().startsWith("Found chapter with duplicate ID"));
    }

    @Test
    public void startTimeWithHoursMinutesAndSecondsIsConvertedToMillis() throws Exception {
        assertEquals(3723000, VorbisCommentChapterReader.getStartTimeFromValue("01:02:03.000"));
    }

    @Test
    public void startTimeWithTooFewSegmentsIsRejected() {
        VorbisCommentReaderException exception = assertThrows(VorbisCommentReaderException.class,
                () -> VorbisCommentChapterReader.getStartTimeFromValue("01:02"));
        assertEquals("Invalid time string", exception.getMessage());
    }

    @Test
    public void startTimeWithNonNumericSegmentIsRejectedWithCause() {
        VorbisCommentReaderException exception = assertThrows(VorbisCommentReaderException.class,
                () -> VorbisCommentChapterReader.getStartTimeFromValue("aa:02:03"));
        assertTrue(exception.getCause() instanceof NumberFormatException);
    }
}
