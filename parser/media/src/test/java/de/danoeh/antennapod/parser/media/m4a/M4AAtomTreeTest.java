package de.danoeh.antennapod.parser.media.m4a;

import de.danoeh.antennapod.model.feed.Chapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.atom;
import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.atomWithSize;
import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.binaryDataAtom;
import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.chapterAtom;
import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.chapterTree;
import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.concat;
import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.fileTypeAtom;
import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.largeSizeAtom;
import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.metadataTree;
import static de.danoeh.antennapod.parser.media.m4a.M4AAtomStreams.textDataAtom;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class M4AAtomTreeTest {
    private static final String COMMENT_ATOM_TYPE = new String(new byte[] {(byte) 0xA9, 'c', 'm', 't'},
            StandardCharsets.ISO_8859_1);

    private static M4AMetadataReader readMetadata(byte[]... parts) throws IOException {
        M4AMetadataReader reader = new M4AMetadataReader(new ByteArrayInputStream(concat(parts)));
        reader.readInputStream();
        return reader;
    }

    private static List<Chapter> readChapters(byte[]... parts) {
        M4AChapterReader reader = new M4AChapterReader(new ByteArrayInputStream(concat(parts)));
        reader.readInputStream();
        return reader.getChapters();
    }

    @Test
    public void descriptionIsReadFromDescAtom() throws Exception {
        M4AMetadataReader reader = readMetadata(fileTypeAtom(),
                metadataTree(atom("desc", textDataAtom("Episode summary"))));
        assertEquals("Episode summary", reader.getDescription());
    }

    @Test
    public void longestDescriptionAmongCandidateAtomsWins() throws Exception {
        M4AMetadataReader reader = readMetadata(fileTypeAtom(), metadataTree(
                atom("desc", textDataAtom("Short")),
                atom("ldes", textDataAtom("The long description of the episode")),
                atom(COMMENT_ATOM_TYPE, textDataAtom("Medium comment"))));
        assertEquals("The long description of the episode", reader.getDescription());
    }

    @Test
    public void dataAtomThatDoesNotHoldTextIsSkipped() throws Exception {
        M4AMetadataReader reader = readMetadata(fileTypeAtom(), metadataTree(
                atom("desc", binaryDataAtom("Not text at all")),
                atom("ldes", textDataAtom("Real text"))));
        assertEquals("Real text", reader.getDescription());
    }

    @Test
    public void dataAtomWithoutTextPayloadIsSkipped() throws Exception {
        M4AMetadataReader reader = readMetadata(fileTypeAtom(), metadataTree(
                atom("desc", textDataAtom(""))));
        assertNull(reader.getDescription());
    }

    @Test
    public void fileWithoutMetadataAtomsHasNoDescription() throws Exception {
        M4AMetadataReader reader = readMetadata(fileTypeAtom(), atom("moov", atom("mvhd", new byte[20])));
        assertNull(reader.getDescription());
    }

    @Test
    public void truncatedFileAfterHeaderHasNoDescription() throws Exception {
        assertNull(readMetadata(fileTypeAtom()).getDescription());
    }

    @Test
    public void fileWithoutFtypAtomIsRejected() {
        IOException exception = assertThrows(IOException.class,
                () -> readMetadata(atom("moov", new byte[8]), new byte[16]));
        assertEquals("Not an M4A file", exception.getMessage());
    }

    @Test
    public void atomSmallerThanItsHeaderIsRejected() {
        IOException exception = assertThrows(IOException.class,
                () -> readMetadata(fileTypeAtom(), atomWithSize(4, "moov", new byte[8])));
        assertTrue(exception.getMessage().contains("invalid size 4"));
    }

    @Test
    public void atomWithLargeSizeFieldIsRead() throws Exception {
        byte[] ilst = atom("ilst", atom("desc", textDataAtom("Large size atom")));
        byte[] meta = atom("meta", new byte[4], ilst);
        byte[] moov = largeSizeAtom("moov", atom("udta", meta));
        assertEquals("Large size atom", readMetadata(fileTypeAtom(), moov).getDescription());
    }

    @Test
    public void atomWithZeroSizeExtendsToEndOfParent() throws Exception {
        byte[] ilst = atom("ilst", atom("desc", textDataAtom("Until the end")));
        byte[] meta = atom("meta", new byte[4], ilst);
        byte[] moov = concat(atomWithSize(0, "moov", atom("udta", meta)));
        assertEquals("Until the end", readMetadata(fileTypeAtom(), moov).getDescription());
    }

    @Test
    public void chaptersAreReadFromNeroChapterAtom() {
        byte[] tree = chapterTree(chapterAtom(new String[] {"Intro", "Main topic"}, new long[] {0, 15_000_000}));
        List<Chapter> chapters = readChapters(fileTypeAtom(), tree);
        assertEquals(2, chapters.size());
        assertEquals("Intro", chapters.get(0).getTitle());
        assertEquals(0, chapters.get(0).getStart());
        assertEquals("1", chapters.get(0).getChapterId());
        assertEquals("Main topic", chapters.get(1).getTitle());
        assertEquals(1500, chapters.get(1).getStart());
        assertEquals("2", chapters.get(1).getChapterId());
    }

    @Test
    public void chapterReaderIgnoresFileThatIsNotM4A() {
        assertTrue(readChapters(new byte[64]).isEmpty());
    }

    @Test
    public void truncatedChapterAtomYieldsNoChapters() {
        byte[] chapters = chapterAtom(new String[] {"Only"}, new long[] {10_000});
        byte[] truncated = new byte[chapters.length - 3];
        System.arraycopy(chapters, 0, truncated, 0, truncated.length);
        assertTrue(readChapters(fileTypeAtom(), chapterTree(truncated)).isEmpty());
    }
}
