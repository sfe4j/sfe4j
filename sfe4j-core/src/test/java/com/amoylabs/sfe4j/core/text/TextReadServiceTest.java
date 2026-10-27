package com.amoylabs.sfe4j.core.text;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextReadServiceTest {

    @TempDir
    Path root;

    private final TextReadService service = new TextReadService();

    private TextReadRequest request(Path file) {
        TextReadRequest request = new TextReadRequest();
        request.setRealPath(file);
        return request;
    }

    private static Path write(Path root, String name, byte[] content) throws IOException {
        Path file = root.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static Path write(Path root, String name, String content, Charset charset) throws IOException {
        return write(root, name, content.getBytes(charset));
    }

    @Test
    void tailReturnsTheLastLines() throws IOException {
        Path file = write(root, "app.log", "line-1\nline-2\nline-3\nline-4\nline-5\n", StandardCharsets.UTF_8);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setLines(2);

        TextReadResult result = service.read(request);

        assertEquals("line-4\nline-5\n", result.getText());
        assertEquals(2, result.getLines());
        assertEquals(TextCharsets.UTF_8, result.getCharset());
        assertEquals(35L, result.getFileSize());
        assertEquals(21L, result.getStartPosition());
        assertEquals(35L, result.getNextPosition());
        assertTrue(result.isEndOfFile());
        assertFalse(result.isTruncated());
    }

    @Test
    void tailKeepsAPartialLastLineWithoutTrailingNewline() throws IOException {
        Path file = write(root, "app.log", "a\nb\npartial", StandardCharsets.UTF_8);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setLines(2);

        TextReadResult result = service.read(request);

        assertEquals("b\npartial", result.getText());
        assertEquals(2, result.getLines());
        assertTrue(result.isEndOfFile());
    }

    @Test
    void crlfNewlinesAreCountedOnce() throws IOException {
        Path file = write(root, "app.log", "one\r\ntwo\r\nthree\r\n", StandardCharsets.UTF_8);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setLines(2);

        TextReadResult result = service.read(request);

        assertEquals("two\r\nthree\r\n", result.getText());
        assertEquals(2, result.getLines());
    }

    @Test
    void tailAsksForMoreLinesThanTheFileHas() throws IOException {
        Path file = write(root, "app.log", "only\n", StandardCharsets.UTF_8);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setLines(200);

        TextReadResult result = service.read(request);

        assertEquals("only\n", result.getText());
        assertEquals(5L, result.getFileSize());
    }

    @Test
    void headIsBoundedByBytesAndLines() throws IOException {
        Path file = write(root, "app.log", "a\nb\nc\nd\ne\n", StandardCharsets.UTF_8);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.HEAD);
        request.setLines(2);

        TextReadResult result = service.read(request);

        assertEquals("a\nb\n", result.getText());
        assertTrue(result.isTruncated());
        assertEquals(4L, result.getNextPosition());

        TextReadRequest bytesOnly = request(file);
        bytesOnly.setMode(TextReadMode.HEAD);
        bytesOnly.setLines(TextReadRequest.NO_LINE_LIMIT);
        bytesOnly.setMaxBytes(3L);

        assertEquals("a\nb", service.read(bytesOnly).getText());
    }

    @Test
    void aVeryLongSingleLineIsCutWithAHint() throws IOException {
        byte[] content = new byte[100_000];
        java.util.Arrays.fill(content, (byte) 'x');
        Path file = write(root, "long.log", content);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.HEAD);
        request.setLines(TextReadRequest.NO_LINE_LIMIT);
        request.setMaxBytes(1000L);

        TextReadResult result = service.read(request);

        assertEquals(1000, result.getText().length());
        assertTrue(result.isTruncated());
        assertTrue(result.isLineTruncated(), "the page has to tell that the line is not shown completely");
    }

    @Test
    void tailIsBoundedByTheScanLimitAndNotByTheFileSize() throws IOException {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 40_000; i++) {
            content.append("line-").append(i).append('\n');
        }
        Path file = write(root, "big.log", content.toString(), StandardCharsets.UTF_8);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setLines(200);
        request.setMaxScanBytes(4096L);

        TextReadResult result = service.read(request);

        assertTrue(result.getFileSize() > 200_000L, "the fixture has to be larger than the scan budget");
        assertTrue(result.getText().length() <= 4096, "only the scanned window is returned");
        assertTrue(result.isTruncated(), "earlier lines exist and are not part of the result");
        assertTrue(result.isEndOfFile());
        assertTrue(result.getText().endsWith("line-39999\n"));
    }

    @Test
    void multiByteCharactersSurviveABlockBoundary() throws IOException {
        StringBuilder content = new StringBuilder();
        while (content.toString().getBytes(StandardCharsets.UTF_8).length < 8190) {
            content.append('a');
        }
        content.append("中").append('\n').append("last line\n");
        Path file = write(root, "mixed.log", content.toString(), StandardCharsets.UTF_8);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setLines(1);

        TextReadResult result = service.read(request);

        assertEquals("last line\n", result.getText());
        assertFalse(result.isDecodingIssue());
    }

    @Test
    void utf16LeIsDetectedFromTheByteOrderMark() throws IOException {
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] body = "hello 😀".getBytes(StandardCharsets.UTF_16LE);
        byte[] content = new byte[bom.length + body.length + 2];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);
        content[content.length - 2] = 0x0A;
        content[content.length - 1] = 0x00;
        Path file = write(root, "utf16.log", content);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setLines(5);

        TextReadResult result = service.read(request);

        assertEquals(TextCharsets.UTF_16LE, result.getCharset());
        assertTrue(result.isByteOrderMark());
        assertEquals("hello 😀\n", result.getText());
        assertFalse(result.isDecodingIssue());
    }

    @Test
    void utf16BeIsReadWithAnExplicitEncoding() throws IOException {
        Path file = write(root, "utf16be.log", "first\nsecond\n", StandardCharsets.UTF_16BE);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setLines(1);
        request.setCharset(TextCharsets.UTF_16BE);

        TextReadResult result = service.read(request);

        assertEquals(TextCharsets.UTF_16BE, result.getCharset());
        assertEquals("second\n", result.getText());
    }

    @Test
    void gb18030IsReadWithAnExplicitEncoding() throws IOException {
        Charset gb18030 = Charset.forName(TextCharsets.GB18030);
        Path file = write(root, "gb.log", "第一行\n第二行\n第三行\n", gb18030);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setLines(1);
        request.setCharset(TextCharsets.GB18030);

        TextReadResult result = service.read(request);

        assertEquals(TextCharsets.GB18030, result.getCharset());
        assertEquals("第三行\n", result.getText());
        assertFalse(result.isDecodingIssue());
    }

    @Test
    void anEncodingConflictingWithTheByteOrderMarkIsRefused() throws IOException {
        byte[] content = {(byte) 0xFF, (byte) 0xFE, 'a', 0x00};
        Path file = write(root, "conflict.log", content);

        TextReadRequest request = request(file);
        request.setCharset(TextCharsets.UTF_8);

        FileAccessException exception = assertThrows(FileAccessException.class, () -> service.read(request));
        assertEquals(FileAccessErrorCode.INVALID_REQUEST, exception.getCode());
    }

    @Test
    void unsupportedEncodingIsRefused() throws IOException {
        Path file = write(root, "app.log", "text\n", StandardCharsets.UTF_8);

        TextReadRequest request = request(file);
        request.setCharset("ISO-8859-1");

        FileAccessException exception = assertThrows(FileAccessException.class, () -> service.read(request));
        assertEquals(FileAccessErrorCode.INVALID_REQUEST, exception.getCode());
    }

    @Test
    void binaryContentIsRefused() throws IOException {
        byte[] content = new byte[512];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        Path file = write(root, "dump.bin", content);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.HEAD);

        FileAccessException exception = assertThrows(FileAccessException.class, () -> service.read(request));
        assertEquals(FileAccessErrorCode.UNSUPPORTED_PREVIEW, exception.getCode());
    }

    @Test
    void utf16TextIsNotRejectedAsBinary() throws IOException {
        Path file = write(root, "utf16.log", "hello world\nsecond line\n", StandardCharsets.UTF_16LE);

        TextReadRequest request = request(file);
        request.setMode(TextReadMode.TAIL);
        request.setCharset(TextCharsets.UTF_16LE);

        TextReadResult result = service.read(request);

        assertFalse(result.isDecodingIssue());
        assertTrue(result.getText().contains("hello world"));
    }

    @Test
    void emptyFileProducesAnEmptyResult() throws IOException {
        Path file = write(root, "empty.log", new byte[0]);

        TextReadResult result = service.read(request(file));

        assertEquals("", result.getText());
        assertEquals(0L, result.getFileSize());
        assertEquals(0L, result.getNextPosition());
        assertTrue(result.isEndOfFile());
        assertNotNull(result.getGeneration());
    }

    @Test
    void incrementReadsAppendedBytesAndNeverSplitsACharacter() throws IOException {
        Path file = write(root, "grow.log", "line-1\n", StandardCharsets.UTF_8);

        TextReadRequest first = request(file);
        first.setMode(TextReadMode.TAIL);
        TextReadResult snapshot = service.read(first);
        assertEquals("line-1\n", snapshot.getText());

        Files.write(file, "line-2\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

        TextReadRequest increment = request(file);
        increment.setStartPosition(snapshot.getNextPosition());
        increment.setExpectedGeneration(snapshot.getGeneration());
        TextReadResult appended = service.readIncrement(increment);

        assertEquals("line-2\n", appended.getText());
        assertEquals(14L, appended.getNextPosition());
        assertTrue(appended.isEndOfFile());
        assertFalse(appended.isReset());

        Files.write(file, new byte[]{(byte) 0xE4}, StandardOpenOption.APPEND);

        TextReadRequest partial = request(file);
        partial.setStartPosition(appended.getNextPosition());
        partial.setExpectedGeneration(appended.getGeneration());
        TextReadResult incomplete = service.readIncrement(partial);

        assertEquals("", incomplete.getText(), "an incomplete character waits for the rest");
        assertEquals(14L, incomplete.getNextPosition());

        Files.write(file, new byte[]{(byte) 0xB8, (byte) 0xAD}, StandardOpenOption.APPEND);

        TextReadResult completed = service.readIncrement(partial);
        assertEquals("中", completed.getText());
        assertEquals(17L, completed.getNextPosition());
    }

    @Test
    void shrinkIsReportedAsResetInsteadOfANegativeOffset() throws IOException {
        Path file = write(root, "truncate.log", "aaaa\nbbbb\ncccc\n", StandardCharsets.UTF_8);

        TextReadRequest first = request(file);
        first.setMode(TextReadMode.TAIL);
        first.setLines(1);
        TextReadResult snapshot = service.read(first);

        Files.write(file, "x\n".getBytes(StandardCharsets.UTF_8));

        TextReadRequest increment = request(file);
        increment.setStartPosition(snapshot.getNextPosition());
        increment.setExpectedGeneration(snapshot.getGeneration());
        TextReadResult result = service.readIncrement(increment);

        assertTrue(result.isReset());
        assertEquals("truncated", result.getResetReason());
        assertEquals("", result.getText());
    }

    @Test
    void replacementIsReportedAsAGenerationChange() throws IOException {
        Path file = write(root, "rotate.log", "old content\n", StandardCharsets.UTF_8);

        TextReadRequest first = request(file);
        first.setMode(TextReadMode.TAIL);
        TextReadResult snapshot = service.read(first);

        Path replacement = write(root, "rotate.log.new", "new content\n", StandardCharsets.UTF_8);
        Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);

        TextReadRequest increment = request(file);
        increment.setStartPosition(snapshot.getNextPosition());
        increment.setExpectedGeneration(snapshot.getGeneration());
        TextReadResult result = service.readIncrement(increment);

        assertTrue(result.isReset());
        assertEquals("generation-changed", result.getResetReason());
    }

    @Test
    void unsupportedTargetIsRefused() throws IOException {
        Path file = root.resolve("missing.log");

        FileAccessException exception = assertThrows(FileAccessException.class, () -> service.read(request(file)));
        assertEquals(FileAccessErrorCode.FILE_NOT_FOUND, exception.getCode());
    }
}
