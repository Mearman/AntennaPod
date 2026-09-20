package de.test.antennapod.util.media;

import de.test.antennapod.util.TestAssets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MediaFixtures {
    public static final String AUDIO_ASSET = "3sec.mp3";
    public static final String LONG_AUDIO_ASSET = "30sec.mp3";

    private static final int ID3_HEADER_LENGTH = 10;
    private static final int ID3_SYNCSAFE_BITS = 7;
    private static final int ID3_SYNCSAFE_MASK = 0x7F;
    private static final int ID3_SIZE_OFFSET = 6;
    private static final int ID3_SIZE_LENGTH = 4;
    private static final int ID3_PADDING_LENGTH = 32;
    private static final byte ENCODING_ISO = 0;
    private static final byte ENCODING_UTF8 = 3;
    private static final int UNKNOWN_OFFSET = 0xFFFFFFFF;
    private static final int IMAGE_TYPE_COVER = 3;
    private static final int OGG_MAX_SEGMENT_LENGTH = 255;
    private static final int OGG_HEADER_TYPE_BEGIN = 0x02;
    private static final int OGG_HEADER_TYPE_CONTINUED = 0x00;
    private static final int OGG_SERIAL = 0x1234;
    private static final int VORBIS_PACKET_IDENTIFICATION = 1;
    private static final int VORBIS_PACKET_COMMENT = 3;
    private static final int VORBIS_IDENTIFICATION_LENGTH = 23;
    private static final int FLAC_BLOCK_STREAMINFO = 0;
    private static final int FLAC_BLOCK_COMMENT = 4;
    private static final int FLAC_LAST_BLOCK_FLAG = 0x80;
    private static final int FLAC_STREAMINFO_LENGTH = 34;
    private static final int NERO_TICKS_PER_MILLISECOND = 10000;
    private static final int MP4_DATA_TYPE_TEXT = 1;

    public static class ChapterSpec {
        public final long startMs;
        public final String title;
        public final String link;
        public final String imageUrl;

        public ChapterSpec(long startMs, String title, String link, String imageUrl) {
            this.startMs = startMs;
            this.title = title;
            this.link = link;
            this.imageUrl = imageUrl;
        }

        public ChapterSpec(long startMs, String title) {
            this(startMs, title, null, null);
        }
    }

    private MediaFixtures() {
    }

    public static byte[] plainAudio() throws IOException {
        return plainAudio(AUDIO_ASSET);
    }

    public static byte[] plainAudio(String asset) throws IOException {
        byte[] mp3 = TestAssets.readBytes(asset);
        int tagLength = ID3_HEADER_LENGTH + readSyncsafe(mp3, ID3_SIZE_OFFSET);
        return Arrays.copyOfRange(mp3, tagLength, mp3.length);
    }

    public static byte[] mp3WithChapters(int version, List<ChapterSpec> chapters, String comment)
            throws IOException {
        return mp3WithChapters(AUDIO_ASSET, version, chapters, comment);
    }

    public static byte[] mp3WithChapters(String asset, int version, List<ChapterSpec> chapters, String comment)
            throws IOException {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        frames.write(id3Frame(version, "TIT2", textPayload("Tagged episode")));
        frames.write(id3Frame(version, "TPE1", textPayload("Tagged artist")));
        if (comment != null) {
            frames.write(id3Frame(version, "COMM", commentPayload(comment)));
        }
        for (int i = 0; i < chapters.size(); i++) {
            frames.write(id3Frame(version, "CHAP", chapterPayload(version, "chp" + i, chapters.get(i))));
        }
        return concat(id3Tag(version, frames.toByteArray()), plainAudio(asset));
    }

    public static byte[] mp3WithCustomComment(String comment) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(ENCODING_ISO);
        payload.write("comment".getBytes(StandardCharsets.ISO_8859_1));
        payload.write(0);
        payload.write(comment.getBytes(StandardCharsets.ISO_8859_1));
        byte[] frame = id3Frame(3, "TXXX", payload.toByteArray());
        return concat(id3Tag(3, frame), plainAudio());
    }

    public static byte[] mp3WithPaddedTagWithoutChapters() throws IOException {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        frames.write(id3Frame(3, "TIT2", textPayload("Tagged episode")));
        frames.write(id3Frame(3, "COMM", commentPayload("A comment")));
        frames.write(new byte[ID3_PADDING_LENGTH]);
        return concat(id3Tag(3, frames.toByteArray()), plainAudio());
    }

    public static byte[] oggVorbis(List<String> comments) throws IOException {
        ByteArrayOutputStream identification = new ByteArrayOutputStream();
        identification.write(VORBIS_PACKET_IDENTIFICATION);
        identification.write("vorbis".getBytes(StandardCharsets.US_ASCII));
        identification.write(new byte[VORBIS_IDENTIFICATION_LENGTH]);

        ByteArrayOutputStream commentPacket = new ByteArrayOutputStream();
        commentPacket.write(VORBIS_PACKET_COMMENT);
        commentPacket.write("vorbis".getBytes(StandardCharsets.US_ASCII));
        commentPacket.write(vorbisCommentBlock(comments));
        commentPacket.write(1);
        return concat(oggPage(OGG_HEADER_TYPE_BEGIN, 0, identification.toByteArray()),
                oggPage(OGG_HEADER_TYPE_CONTINUED, 1, commentPacket.toByteArray()));
    }

    public static byte[] oggOpus(List<String> comments) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write("OpusHead".getBytes(StandardCharsets.US_ASCII));
        head.write(new byte[VORBIS_IDENTIFICATION_LENGTH]);

        ByteArrayOutputStream tags = new ByteArrayOutputStream();
        tags.write("OpusTags".getBytes(StandardCharsets.US_ASCII));
        tags.write(vorbisCommentBlock(comments));
        return concat(oggPage(OGG_HEADER_TYPE_BEGIN, 0, head.toByteArray()),
                oggPage(OGG_HEADER_TYPE_CONTINUED, 1, tags.toByteArray()));
    }

    public static byte[] flac(List<String> comments) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("fLaC".getBytes(StandardCharsets.US_ASCII));
        out.write(flacBlock(FLAC_BLOCK_STREAMINFO, false, new byte[FLAC_STREAMINFO_LENGTH]));
        out.write(flacBlock(FLAC_BLOCK_COMMENT, true, vorbisCommentBlock(comments)));
        return out.toByteArray();
    }

    public static byte[] flacInOgg(List<String> comments) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(0x7F);
        packet.write("FLAC".getBytes(StandardCharsets.US_ASCII));
        packet.write(new byte[]{1, 0, 0, 2});
        packet.write("fLaC".getBytes(StandardCharsets.US_ASCII));
        packet.write(flacBlock(FLAC_BLOCK_STREAMINFO, false, new byte[FLAC_STREAMINFO_LENGTH]));
        packet.write(flacBlock(FLAC_BLOCK_COMMENT, true, vorbisCommentBlock(comments)));
        return oggPage(OGG_HEADER_TYPE_BEGIN, 0, packet.toByteArray());
    }

    public static List<String> vorbisChapterComments(List<ChapterSpec> chapters) {
        List<String> comments = new ArrayList<>();
        for (int i = 0; i < chapters.size(); i++) {
            ChapterSpec chapter = chapters.get(i);
            String id = String.format(Locale.US, "CHAPTER%03d", i);
            comments.add(id + "=" + vorbisTime(chapter.startMs));
            if (chapter.title != null) {
                comments.add(id + "NAME=" + chapter.title);
            }
            if (chapter.link != null) {
                comments.add(id + "URL=" + chapter.link);
            }
        }
        return comments;
    }

    public static byte[] m4aWithChapters(List<ChapterSpec> chapters) throws IOException {
        ByteArrayOutputStream chpl = new ByteArrayOutputStream();
        chpl.write(new byte[]{1, 0, 0, 0, 0, 0, 0, 0});
        chpl.write(chapters.size());
        for (ChapterSpec chapter : chapters) {
            chpl.write(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN)
                    .putLong(chapter.startMs * NERO_TICKS_PER_MILLISECOND).array());
            byte[] name = chapter.title.getBytes(StandardCharsets.UTF_8);
            chpl.write(name.length);
            chpl.write(name);
        }
        byte[] udta = mp4Box("udta", mp4Box("chpl", chpl.toByteArray()));
        return concat(ftyp(), mp4Box("moov", udta));
    }

    public static byte[] m4aWithDescriptions(String description, String comment) throws IOException {
        ByteArrayOutputStream ilst = new ByteArrayOutputStream();
        if (description != null) {
            ilst.write(mp4Box("desc", mp4Box("data", mp4TextData(description))));
        }
        if (comment != null) {
            ilst.write(mp4Box(new byte[]{(byte) 0xA9, 'c', 'm', 't'}, mp4Box("data", mp4TextData(comment))));
        }
        ilst.write(mp4Box("ldes", mp4Box("data", new byte[]{0, 0, 0, 13, 0, 0, 0, 0})));
        byte[] metaContent = concat(new byte[]{0, 0, 0, 0}, mp4Box("ilst", ilst.toByteArray()));
        byte[] udta = mp4Box("udta", mp4Box("meta", metaContent));
        return concat(ftyp(), mp4Box("moov", udta));
    }

    private static byte[] ftyp() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write("M4A ".getBytes(StandardCharsets.US_ASCII));
        payload.write(new byte[]{0, 0, 0, 0});
        payload.write("M4A mp42isom".getBytes(StandardCharsets.US_ASCII));
        return mp4Box("ftyp", payload.toByteArray());
    }

    private static byte[] mp4TextData(String text) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(ByteBuffer.allocate(Integer.BYTES * 2).putInt(MP4_DATA_TYPE_TEXT).putInt(0).array());
        data.write(text.getBytes(StandardCharsets.UTF_8));
        return data.toByteArray();
    }

    private static byte[] mp4Box(String type, byte[] payload) throws IOException {
        return mp4Box(type.getBytes(StandardCharsets.US_ASCII), payload);
    }

    private static byte[] mp4Box(byte[] type, byte[] payload) throws IOException {
        ByteArrayOutputStream box = new ByteArrayOutputStream();
        box.write(ByteBuffer.allocate(Integer.BYTES).putInt(payload.length + Integer.BYTES + type.length).array());
        box.write(type);
        box.write(payload);
        return box.toByteArray();
    }

    private static String vorbisTime(long millis) {
        long hours = millis / 3600000L;
        long minutes = millis / 60000L % 60;
        double seconds = millis % 60000L / 1000.0;
        return String.format(Locale.US, "%02d:%02d:%06.3f", hours, minutes, seconds);
    }

    private static byte[] flacBlock(int type, boolean last, byte[] payload) throws IOException {
        ByteArrayOutputStream block = new ByteArrayOutputStream();
        block.write(type | (last ? FLAC_LAST_BLOCK_FLAG : 0));
        block.write(payload.length >> 16);
        block.write(payload.length >> 8);
        block.write(payload.length);
        block.write(payload);
        return block.toByteArray();
    }

    private static byte[] vorbisCommentBlock(List<String> comments) throws IOException {
        ByteArrayOutputStream block = new ByteArrayOutputStream();
        byte[] vendor = "fixture".getBytes(StandardCharsets.UTF_8);
        block.write(littleEndianInt(vendor.length));
        block.write(vendor);
        block.write(littleEndianInt(comments.size()));
        for (String comment : comments) {
            byte[] bytes = comment.getBytes(StandardCharsets.UTF_8);
            block.write(littleEndianInt(bytes.length));
            block.write(bytes);
        }
        return block.toByteArray();
    }

    private static byte[] littleEndianInt(int value) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private static byte[] oggPage(int headerType, int sequence, byte[] packet) throws IOException {
        ByteArrayOutputStream lacing = new ByteArrayOutputStream();
        int remaining = packet.length;
        while (remaining >= OGG_MAX_SEGMENT_LENGTH) {
            lacing.write(OGG_MAX_SEGMENT_LENGTH);
            remaining -= OGG_MAX_SEGMENT_LENGTH;
        }
        lacing.write(remaining);

        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.write("OggS".getBytes(StandardCharsets.US_ASCII));
        page.write(0);
        page.write(headerType);
        page.write(new byte[Long.BYTES]);
        page.write(littleEndianInt(OGG_SERIAL));
        page.write(littleEndianInt(sequence));
        page.write(new byte[Integer.BYTES]);
        page.write(lacing.size());
        page.write(lacing.toByteArray());
        page.write(packet);
        return page.toByteArray();
    }

    private static byte[] id3Tag(int version, byte[] frames) throws IOException {
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        tag.write("ID3".getBytes(StandardCharsets.US_ASCII));
        tag.write(version);
        tag.write(0);
        tag.write(0);
        tag.write(syncsafe(frames.length));
        tag.write(frames);
        return tag.toByteArray();
    }

    private static byte[] id3Frame(int version, String id, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(id.getBytes(StandardCharsets.US_ASCII));
        frame.write(version >= 4 ? syncsafe(payload.length)
                : ByteBuffer.allocate(Integer.BYTES).putInt(payload.length).array());
        frame.write(new byte[]{0, 0});
        frame.write(payload);
        return frame.toByteArray();
    }

    private static byte[] chapterPayload(int version, String elementId, ChapterSpec chapter) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(elementId.getBytes(StandardCharsets.ISO_8859_1));
        payload.write(0);
        payload.write(ByteBuffer.allocate(Integer.BYTES * 4)
                .putInt((int) chapter.startMs)
                .putInt((int) chapter.startMs)
                .putInt(UNKNOWN_OFFSET)
                .putInt(UNKNOWN_OFFSET).array());
        if (chapter.title != null) {
            payload.write(id3Frame(version, "TIT2", textPayload(chapter.title)));
        }
        if (chapter.link != null) {
            ByteArrayOutputStream link = new ByteArrayOutputStream();
            link.write(ENCODING_ISO);
            link.write(0);
            link.write(chapter.link.getBytes(StandardCharsets.ISO_8859_1));
            payload.write(id3Frame(version, "WXXX", link.toByteArray()));
        }
        if (chapter.imageUrl != null) {
            ByteArrayOutputStream picture = new ByteArrayOutputStream();
            picture.write(ENCODING_ISO);
            picture.write("-->".getBytes(StandardCharsets.ISO_8859_1));
            picture.write(0);
            picture.write(IMAGE_TYPE_COVER);
            picture.write(0);
            picture.write(chapter.imageUrl.getBytes(StandardCharsets.ISO_8859_1));
            picture.write(0);
            payload.write(id3Frame(version, "APIC", picture.toByteArray()));
        }
        return payload.toByteArray();
    }

    private static byte[] textPayload(String text) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(ENCODING_UTF8);
        payload.write(text.getBytes(StandardCharsets.UTF_8));
        return payload.toByteArray();
    }

    private static byte[] commentPayload(String comment) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(ENCODING_ISO);
        payload.write("eng".getBytes(StandardCharsets.US_ASCII));
        payload.write(0);
        payload.write(comment.getBytes(StandardCharsets.ISO_8859_1));
        return payload.toByteArray();
    }

    private static byte[] syncsafe(int value) {
        byte[] bytes = new byte[ID3_SIZE_LENGTH];
        for (int i = ID3_SIZE_LENGTH - 1; i >= 0; i--) {
            bytes[i] = (byte) (value & ID3_SYNCSAFE_MASK);
            value >>= ID3_SYNCSAFE_BITS;
        }
        return bytes;
    }

    private static int readSyncsafe(byte[] bytes, int offset) {
        int value = 0;
        for (int i = 0; i < ID3_SIZE_LENGTH; i++) {
            value = (value << ID3_SYNCSAFE_BITS) | (bytes[offset + i] & ID3_SYNCSAFE_MASK);
        }
        return value;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
