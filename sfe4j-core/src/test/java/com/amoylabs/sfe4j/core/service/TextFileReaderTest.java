package com.amoylabs.sfe4j.core.service;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.vo.TextPreviewVO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFileReaderTest {

    @TempDir
    Path root;

    private final TextFileReader reader = new TextFileReader();

    @Test
    void smallFileIsReadCompletely() throws IOException {
        Path file = Files.writeString(root.resolve("small.txt"), "hello 世界", StandardCharsets.UTF_8);

        TextPreviewVO preview = reader.read(file, file.toString(), "small.txt", 1024L);

        assertEquals("hello 世界", preview.getContent());
        assertFalse(preview.isTruncated());
        assertFalse(preview.isDecodingIssue());
        assertEquals(StandardCharsets.UTF_8.name(), preview.getEncoding());
    }

    @Test
    void emptyFileProducesEmptyContent() throws IOException {
        Path file = Files.write(root.resolve("empty.txt"), new byte[0]);

        TextPreviewVO preview = reader.read(file, file.toString(), "empty.txt", 1024L);

        assertEquals("", preview.getContent());
        assertFalse(preview.isTruncated());
    }

    @Test
    void fileExactlyAtTheLimitIsNotTruncated() throws IOException {
        byte[] content = new byte[1024];
        java.util.Arrays.fill(content, (byte) 'a');
        Path file = Files.write(root.resolve("exact.txt"), content);

        TextPreviewVO preview = reader.read(file, file.toString(), "exact.txt", 1024L);

        assertFalse(preview.isTruncated());
        assertEquals(1024, preview.getContent().length());
    }

    @Test
    void utf8BomIsRemoved() throws IOException {
        byte[] content = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', 'b'};
        Path file = Files.write(root.resolve("bom.txt"), content);

        TextPreviewVO preview = reader.read(file, file.toString(), "bom.txt", 1024L);

        assertEquals("ab", preview.getContent());
        assertFalse(preview.isDecodingIssue());
    }

    @Test
    void invalidBytesAreReplacedAndReported() throws IOException {
        byte[] content = {'o', 'k', ' ', (byte) 0xFF, (byte) 0xFE};
        Path file = Files.write(root.resolve("broken.txt"), content);

        TextPreviewVO preview = reader.read(file, file.toString(), "broken.txt", 1024L);

        assertTrue(preview.isDecodingIssue());
        assertTrue(preview.getContent().contains("\uFFFD"));
    }

    @Test
    void truncatedMultiByteCharacterIsDroppedInsteadOfBecomingGarbage() throws IOException {
        String content = "a" + "é".repeat(10);
        Path file = Files.writeString(root.resolve("cut.txt"), content, StandardCharsets.UTF_8);

        TextPreviewVO preview = reader.read(file, file.toString(), "cut.txt", 4L);

        assertTrue(preview.isTruncated());
        assertFalse(preview.isDecodingIssue());
        assertEquals("aé", preview.getContent());
    }

    @Test
    void binaryContentIsRejected() throws IOException {
        Path file = Files.write(root.resolve("binary.bin"), new byte[]{1, 0, 2, 3});

        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> reader.read(file, file.toString(), "binary.bin", 1024L));
        assertEquals(FileAccessErrorCode.UNSUPPORTED_PREVIEW, exception.getCode());
    }

    @Test
    void missingFileIsReportedAsIoError() {
        Path missing = root.resolve("missing.txt");

        FileAccessException exception = assertThrows(FileAccessException.class,
                () -> reader.read(missing, missing.toString(), "missing.txt", 1024L));
        assertEquals(FileAccessErrorCode.FILE_IO_ERROR, exception.getCode());
    }
}
