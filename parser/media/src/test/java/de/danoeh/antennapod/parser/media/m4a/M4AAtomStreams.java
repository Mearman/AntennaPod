package de.danoeh.antennapod.parser.media.m4a;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class M4AAtomStreams {
    private static final int HEADER_LENGTH = 8;
    private static final int DATA_TYPE_UTF8 = 1;
    private static final int DATA_TYPE_BINARY = 0;

    private M4AAtomStreams() {
    }

    static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            output.write(array, 0, array.length);
        }
        return output.toByteArray();
    }

    static byte[] bigEndianInt(long value) {
        return ByteBuffer.allocate(4).putInt((int) value).array();
    }

    static byte[] atomWithSize(long size, String type, byte[]... payloads) {
        return concat(bigEndianInt(size), type.getBytes(StandardCharsets.ISO_8859_1), concat(payloads));
    }

    static byte[] atom(String type, byte[]... payloads) {
        return atomWithSize(HEADER_LENGTH + concat(payloads).length, type, payloads);
    }

    static byte[] largeSizeAtom(String type, byte[]... payloads) {
        byte[] payload = concat(payloads);
        byte[] largeSize = ByteBuffer.allocate(8).putLong(HEADER_LENGTH + 8 + payload.length).array();
        return concat(bigEndianInt(1), type.getBytes(StandardCharsets.ISO_8859_1), largeSize, payload);
    }

    static byte[] fileTypeAtom() {
        return atom("ftyp", "M4A ".getBytes(StandardCharsets.ISO_8859_1), new byte[4]);
    }

    static byte[] dataAtom(int dataType, String text) {
        return atom("data", bigEndianInt(dataType), new byte[4], text.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] textDataAtom(String text) {
        return dataAtom(DATA_TYPE_UTF8, text);
    }

    static byte[] binaryDataAtom(String text) {
        return dataAtom(DATA_TYPE_BINARY, text);
    }

    static byte[] metadataTree(byte[]... itemAtoms) {
        byte[] ilst = atom("ilst", itemAtoms);
        byte[] meta = atom("meta", new byte[4], ilst);
        byte[] udta = atom("udta", meta);
        return atom("moov", atom("mvhd", new byte[20]), udta);
    }

    static byte[] chapterAtom(String[] titles, long[] startTimesInHundredNanoseconds) {
        byte[] payload = concat(new byte[5], bigEndianInt(titles.length));
        for (int i = 0; i < titles.length; i++) {
            byte[] title = titles[i].getBytes(StandardCharsets.UTF_8);
            payload = concat(payload, ByteBuffer.allocate(8).putLong(startTimesInHundredNanoseconds[i]).array(),
                    new byte[] {(byte) title.length}, title);
        }
        return atom("chpl", payload);
    }

    static byte[] chapterTree(byte[] chapterAtom) {
        return atom("moov", atom("mvhd", new byte[20]), atom("udta", chapterAtom));
    }
}
