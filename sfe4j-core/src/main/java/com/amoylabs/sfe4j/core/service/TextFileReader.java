package com.amoylabs.sfe4j.core.service;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.vo.TextPreviewVO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a bounded head of a regular file as plain text.
 *
 * <p>The file is never loaded as a whole: at most {@code maxBytes + 1} bytes are read, so the caller
 * can detect the truncation without allocating memory proportional to the file size. The input stream
 * is closed by this method on every exit path.</p>
 *
 * <p>Binary detection is a heuristic (NUL byte or a high ratio of control characters in the sample),
 * stated as such in the documentation.</p>
 */
public class TextFileReader {

    private static final int BINARY_SAMPLE_LIMIT = 8192;

    private static final double MAX_CONTROL_RATIO = 0.3d;

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * Reads the head of a file.
     *
     * @param realPath    fully resolved path of a regular file
     * @param lexicalPath lexical path used to build page links
     * @param fileName    base name of the file
     * @param maxBytes    maximum number of bytes to decode, {@code sfe4j.preview.max-bytes}
     * @return bounded preview
     * @throws FileAccessException {@code UNSUPPORTED_PREVIEW} for binary content, {@code FILE_IO_ERROR}
     *                             for an I/O failure
     */
    public TextPreviewVO read(Path realPath, String lexicalPath, String fileName, long maxBytes) {
        long size;
        try {
            size = Files.size(realPath);
        } catch (IOException e) {
            throw new FileAccessException(FileAccessErrorCode.FILE_IO_ERROR, "The requested file cannot be read.", e);
        }

        if (size <= 0L) {
            return buildPreview(lexicalPath, fileName, "", 0L, maxBytes, false, false);
        }

        int limit = (int) Math.min(maxBytes, Integer.MAX_VALUE - 1L);
        byte[] buffer;
        try (InputStream inputStream = Files.newInputStream(realPath)) {
            buffer = inputStream.readNBytes(limit + 1);
        } catch (IOException e) {
            throw new FileAccessException(FileAccessErrorCode.FILE_IO_ERROR, "The requested file cannot be read.", e);
        }

        boolean truncated = buffer.length > limit;
        int length = truncated ? limit : buffer.length;

        int offset = 0;
        if (length >= 3 && startsWithBom(buffer)) {
            offset = 3;
        }

        if (looksBinary(buffer, offset, length - offset)) {
            throw new FileAccessException(FileAccessErrorCode.UNSUPPORTED_PREVIEW,
                    "The requested file looks like binary content and cannot be previewed as text.");
        }

        Decoded decoded = decode(buffer, offset, length - offset, StandardCharsets.UTF_8, !truncated);

        return buildPreview(lexicalPath, fileName, decoded.text, size, maxBytes, truncated, decoded.replaced);
    }

    private static boolean startsWithBom(byte[] buffer) {
        for (int i = 0; i < UTF_8_BOM.length; i++) {
            if (buffer[i] != UTF_8_BOM[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksBinary(byte[] buffer, int offset, int length) {
        int sampleSize = Math.min(length, BINARY_SAMPLE_LIMIT);
        if (sampleSize <= 0) {
            return false;
        }
        int controlCount = 0;
        for (int i = offset; i < offset + sampleSize; i++) {
            int value = buffer[i] & 0xFF;
            if (value == 0) {
                return true;
            }
            boolean allowed = value == '\t' || value == '\n' || value == '\r' || value == '\f' || value == '\b';
            if (value < 0x20 && !allowed) {
                controlCount++;
            }
        }
        return controlCount > sampleSize * MAX_CONTROL_RATIO;
    }

    private static Decoded decode(byte[] buffer, int offset, int length, Charset charset, boolean endOfInput) {
        ByteBuffer input = ByteBuffer.wrap(buffer, offset, length);
        CharBuffer output = CharBuffer.allocate(Math.max(16, length));
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        CoderResult result = decoder.decode(input, output, endOfInput);
        while (result.isOverflow()) {
            output = grow(output);
            result = decoder.decode(input, output, endOfInput);
        }

        if (!result.isError()) {
            output.flip();
            return new Decoded(output.toString(), false);
        }

        CharsetDecoder replacing = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer retryInput = ByteBuffer.wrap(buffer, offset, length);
        CharBuffer retryOutput = CharBuffer.allocate(Math.max(16, length));
        CoderResult retry = replacing.decode(retryInput, retryOutput, endOfInput);
        while (retry.isOverflow()) {
            retryOutput = grow(retryOutput);
            retry = replacing.decode(retryInput, retryOutput, endOfInput);
        }
        retryOutput.flip();
        return new Decoded(retryOutput.toString(), true);
    }

    private static CharBuffer grow(CharBuffer current) {
        CharBuffer bigger = CharBuffer.allocate(current.capacity() * 2 + 16);
        current.flip();
        bigger.put(current);
        return bigger;
    }

    private static TextPreviewVO buildPreview(String lexicalPath, String fileName, String content, long size,
                                              long maxBytes, boolean truncated, boolean decodingIssue) {
        TextPreviewVO preview = new TextPreviewVO();
        preview.setFileName(fileName);
        preview.setPath(lexicalPath);
        preview.setContent(content);
        preview.setSize(size);
        preview.setMaxBytes(maxBytes);
        preview.setTruncated(truncated);
        preview.setDecodingIssue(decodingIssue);
        preview.setEncoding(StandardCharsets.UTF_8.name());
        return preview;
    }

    private static final class Decoded {

        private final String text;

        private final boolean replaced;

        private Decoded(String text, boolean replaced) {
            this.text = text;
            this.replaced = replaced;
        }
    }
}
