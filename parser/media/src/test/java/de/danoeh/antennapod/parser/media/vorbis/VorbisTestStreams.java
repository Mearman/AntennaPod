package de.danoeh.antennapod.parser.media.vorbis;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class VorbisTestStreams {
    private static final int MAX_SEGMENT_LENGTH = 255;
    private static final int STREAMINFO_LENGTH = 34;
    private static final int FLAC_LAST_BLOCK_FLAG = 0x80;
    private static final int FLAC_TYPE_STREAMINFO = 0;
    private static final int FLAC_TYPE_COMMENT = 4;

    private VorbisTestStreams() {
    }

    static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            output.write(array, 0, array.length);
        }
        return output.toByteArray();
    }

    static byte[] littleEndianInt(long value) {
        return new byte[] {(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)};
    }

    static byte[] commentBlock(String vendor, String... comments) {
        byte[] vendorBytes = vendor.getBytes(StandardCharsets.UTF_8);
        byte[] block = concat(littleEndianInt(vendorBytes.length), vendorBytes, littleEndianInt(comments.length));
        for (String comment : comments) {
            byte[] commentBytes = comment.getBytes(StandardCharsets.UTF_8);
            block = concat(block, littleEndianInt(commentBytes.length), commentBytes);
        }
        return block;
    }

    static byte[] oggPage(byte[] payload) {
        ByteArrayOutputStream segmentTable = new ByteArrayOutputStream();
        int remaining = payload.length;
        while (remaining >= MAX_SEGMENT_LENGTH) {
            segmentTable.write(MAX_SEGMENT_LENGTH);
            remaining -= MAX_SEGMENT_LENGTH;
        }
        segmentTable.write(remaining);
        byte[] header = {'O', 'g', 'g', 'S', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] table = segmentTable.toByteArray();
        return concat(header, new byte[] {(byte) table.length}, table, payload);
    }

    static byte[] opusStream(byte[] commentBlock) {
        return concat(
                oggPage("OpusHead".getBytes(StandardCharsets.ISO_8859_1)),
                oggPage(concat("OpusTags".getBytes(StandardCharsets.ISO_8859_1), commentBlock)));
    }

    static byte[] vorbisStream(byte[] commentBlock) {
        byte[] identification = concat(new byte[] {1}, "vorbis".getBytes(StandardCharsets.ISO_8859_1));
        byte[] commentHeader = concat(new byte[] {3}, "vorbis".getBytes(StandardCharsets.ISO_8859_1), commentBlock);
        return concat(oggPage(identification), oggPage(commentHeader));
    }

    static byte[] vorbisStreamSplitAcrossPages(byte[] commentBlock, int firstPagePayloadLength) {
        byte[] identification = concat(new byte[] {1}, "vorbis".getBytes(StandardCharsets.ISO_8859_1));
        byte[] commentHeader = concat(new byte[] {3}, "vorbis".getBytes(StandardCharsets.ISO_8859_1), commentBlock);
        byte[] firstPart = new byte[firstPagePayloadLength];
        byte[] secondPart = new byte[commentHeader.length - firstPagePayloadLength];
        System.arraycopy(commentHeader, 0, firstPart, 0, firstPart.length);
        System.arraycopy(commentHeader, firstPart.length, secondPart, 0, secondPart.length);
        return concat(oggPage(identification), oggPage(firstPart), oggPage(secondPart));
    }

    private static byte[] flacBlock(int type, boolean last, byte[] data) {
        byte[] header = {
                (byte) (type | (last ? FLAC_LAST_BLOCK_FLAG : 0)),
                (byte) (data.length >> 16), (byte) (data.length >> 8), (byte) data.length};
        return concat(header, data);
    }

    static byte[] flacStreamWithComments(byte[] commentBlock) {
        return concat("fLaC".getBytes(StandardCharsets.ISO_8859_1),
                flacBlock(FLAC_TYPE_STREAMINFO, false, new byte[STREAMINFO_LENGTH]),
                flacBlock(FLAC_TYPE_COMMENT, true, commentBlock));
    }

    static byte[] flacStreamWithoutComments() {
        return concat("fLaC".getBytes(StandardCharsets.ISO_8859_1),
                flacBlock(FLAC_TYPE_STREAMINFO, true, new byte[STREAMINFO_LENGTH]));
    }
}
