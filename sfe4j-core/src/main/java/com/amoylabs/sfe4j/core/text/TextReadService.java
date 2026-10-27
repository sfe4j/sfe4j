package com.amoylabs.sfe4j.core.text;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Bounded plain text reads of an already authorized regular file.
 *
 * <p>Neither mode loads the whole file. {@link TextReadMode#TAIL} scans backwards within
 * {@code sfe4j.logs.max-scan-bytes}, so the effort depends on the scan limit and not on the file size:
 * a file larger than 1 GiB costs the same as a small one.</p>
 *
 * <p>Offsets are byte offsets. A read never splits a multi byte character: an incomplete trailing
 * sequence stays for the next read, so an increment starts exactly where the previous one stopped.</p>
 *
 * <p>Binary detection is a heuristic over the decoded content (share of printable characters and share
 * of decoding errors). It deliberately does not look for a NUL byte in the raw content, because UTF-16
 * text contains zero bytes as well.</p>
 */
public class TextReadService {

    private static final int BLOCK_SIZE = 8192;

    private static final int HEAD_LENGTH = 4;

    private static final int MAX_CHAR_BYTES = 4;

    private static final int SAMPLE_LIMIT = 8192;

    private static final double MAX_ERROR_RATIO = 0.3d;

    private static final double MIN_PRINTABLE_RATIO = 0.7d;

    /**
     * Reads the head or the tail of a file.
     *
     * @param request authorized target and read parameters
     * @throws FileAccessException {@code INVALID_REQUEST} for an unsupported encoding or a byte order
     *                             mark conflict, {@code UNSUPPORTED_PREVIEW} for binary content or a
     *                             non regular target, {@code FILE_IO_ERROR} for an I/O failure
     */
    public TextReadResult read(TextReadRequest request) {
        Path path = requireRegularFile(request);
        FileAttributes attributes = attributesOf(path);
        long size = attributes.size();

        byte[] head = readRange(path, 0L, HEAD_LENGTH);
        TextCharsets.Bom bom = TextCharsets.detectBom(head, head.length);
        Charset charset = effectiveCharset(request.getCharset(), bom);
        int bomLength = bom == null ? 0 : bom.length();

        TextReadResult result = new TextReadResult();
        result.setCharset(charset.name());
        result.setByteOrderMark(bom != null);
        result.setFileSize(size);
        result.setGeneration(attributes.generation());
        result.setStartPosition(Math.min(bomLength, size));
        result.setNextPosition(size);
        result.setEndOfFile(true);

        if (size <= bomLength) {
            return result;
        }

        if (request.getMode() == TextReadMode.HEAD) {
            readHead(request, path, size, charset, bomLength, result);
        } else {
            readTail(request, path, size, charset, bomLength, result);
        }
        return result;
    }

    /**
     * Reads the bytes appended after {@code startPosition}.
     *
     * <p>The read is bounded by {@code maxBytes}. When the file was replaced or shrank the result
     * carries {@code reset} instead of a negative offset, and the caller reads a new snapshot.</p>
     */
    public TextReadResult readIncrement(TextReadRequest request) {
        Path path = requireRegularFile(request);
        FileAttributes attributes = attributesOf(path);
        long size = attributes.size();

        byte[] head = readRange(path, 0L, HEAD_LENGTH);
        TextCharsets.Bom bom = TextCharsets.detectBom(head, head.length);
        Charset charset = effectiveCharset(request.getCharset(), bom);
        int bomLength = bom == null ? 0 : bom.length();

        TextReadResult result = new TextReadResult();
        result.setCharset(charset.name());
        result.setByteOrderMark(bom != null);
        result.setFileSize(size);
        result.setGeneration(attributes.generation());

        if (request.getExpectedGeneration() != null
                && !request.getExpectedGeneration().equals(attributes.generation())) {
            return reset(result, "generation-changed");
        }

        long start = Math.max(bomLength, request.getStartPosition());
        if (start > size) {
            return reset(result, "truncated");
        }

        long budget = Math.max(1L, request.getMaxBytes());
        long end = Math.min(size, start + budget);
        result.setStartPosition(start);
        result.setNextPosition(start);
        result.setEndOfFile(start >= size);
        if (end <= start) {
            return result;
        }

        byte[] data = readRange(path, start, (int) (end - start));
        int from = alignToCharStart(data, data.length, charset);
        Decoded decoded = decode(data, from, data.length, charset, false);
        long consumed = from + decoded.consumed();

        result.setText(decoded.text());
        result.setDecodingIssue(decoded.replaced() || from > 0);
        result.setStartPosition(start + from);
        result.setNextPosition(start + consumed);
        result.setEndOfFile(start + consumed >= size);
        result.setTruncated(start + consumed < size);
        result.setLines(countLines(decoded.text()));
        return result;
    }

    private void readHead(TextReadRequest request, Path path, long size, Charset charset, int bomLength,
                          TextReadResult result) {
        int lines = request.effectiveLines();
        long budget = Math.max(1L, request.getMaxBytes());
        long start = Math.max(bomLength, request.getStartPosition());

        if (start > size) {
            reset(result, "truncated");
            return;
        }

        int unit = TextCharsets.newlineUnit(charset);
        byte[] block = new byte[(int) Math.min(BLOCK_SIZE, budget)];
        byte[] content = new byte[(int) Math.min(budget, size - start)];
        int written = 0;
        int newlines = 0;
        boolean stopped = false;

        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            channel.position(start);
            while (written < content.length && !stopped) {
                int want = Math.min(block.length, content.length - written);
                int read = fill(channel, ByteBuffer.wrap(block, 0, want));
                if (read <= 0) {
                    break;
                }
                int take = read;
                for (int i = 0; i < read; i++) {
                    if (TextCharsets.isNewlineAt(block, i, read, start + written + i, charset)) {
                        newlines++;
                        if (newlines >= lines) {
                            take = Math.min(read, i + unit);
                            stopped = true;
                            break;
                        }
                    }
                }
                System.arraycopy(block, 0, content, written, take);
                written += take;
            }
        } catch (IOException e) {
            throw ioError(e);
        }

        finish(result, content, written, charset, start, size, bomLength, start + written >= size, false);
    }

    private void readTail(TextReadRequest request, Path path, long size, Charset charset, int bomLength,
                          TextReadResult result) {
        int lines = request.effectiveLines();
        long budget = Math.max(1L, request.getMaxBytes());
        long scanBudget = Math.max(BLOCK_SIZE, request.getMaxScanBytes());
        long scanFloor = Math.max(bomLength, size - scanBudget);
        int unit = TextCharsets.newlineUnit(charset);

        byte[] block = new byte[BLOCK_SIZE];

        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            long floor = size;
            long newlines = 0L;
            while (floor > scanFloor && newlines <= lines) {
                long blockStart = alignDown(Math.max(scanFloor, floor - BLOCK_SIZE), bomLength, unit);
                if (blockStart >= floor) {
                    break;
                }
                int read = readChannel(channel, blockStart, block, (int) (floor - blockStart));
                if (read <= 0) {
                    break;
                }
                newlines += countNewlines(block, read, blockStart, charset);
                floor = blockStart;
            }

            boolean scanLimited = floor <= scanFloor && scanFloor > bomLength;
            boolean endsWithNewline = endsWithNewline(channel, size, charset);
            long available = newlines + (endsWithNewline ? 0L : 1L);
            long skip = Math.max(0L, available - lines);

            long start = floor;
            if (skip > 0L) {
                start = offsetAfterSkippingNewlines(channel, floor, size, skip, charset);
            }
            start = Math.max(start, bomLength);

            long end = Math.min(size, start + budget);
            byte[] content = new byte[(int) (end - start)];
            if (content.length > 0) {
                int read = readChannel(channel, start, content, content.length);
                if (read < content.length) {
                    content = trim(content, read);
                    end = start + Math.max(read, 0);
                }
            }
            finish(result, content, content.length, charset, start, size, bomLength, end >= size, scanLimited);
        } catch (IOException e) {
            throw ioError(e);
        }
    }

    /**
     * Decodes the bytes, rejects binary content and fills the remaining result fields.
     */
    private void finish(TextReadResult result, byte[] content, int length, Charset charset, long start, long size,
                        long firstOffset, boolean endReached, boolean scanLimited) {
        int from = alignToCharStart(content, length, charset);
        Decoded decoded = decode(content, from, length, charset, endReached);
        String text = decoded.text();

        Analysis analysis = analyze(content, from, length, charset, endReached);
        if (looksBinary(text, analysis)) {
            throw new FileAccessException(FileAccessErrorCode.UNSUPPORTED_PREVIEW,
                    "The requested file looks like binary content and cannot be shown as text.");
        }

        long consumed = decoded.consumed();
        result.setText(text);
        result.setDecodingIssue(decoded.replaced() || from > 0);
        result.setStartPosition(start + from);
        result.setNextPosition(start + consumed);
        result.setTruncated(start + consumed < size || scanLimited);
        result.setEndOfFile(start + consumed >= size);
        result.setLines(countLines(text));
        result.setLineTruncated(result.isTruncated() && !endsWithNewline(text));
    }

    /**
     * @return {@code true} when the file ends with a newline in the given encoding
     */
    private static boolean endsWithNewline(SeekableByteChannel channel, long size, Charset charset)
            throws IOException {
        int unit = TextCharsets.newlineUnit(charset);
        if (size < unit) {
            return false;
        }
        byte[] tail = new byte[unit];
        int read = readChannel(channel, size - unit, tail, unit);
        if (read < unit) {
            return false;
        }
        return TextCharsets.isNewlineAt(tail, 0, read, size - unit, charset);
    }

    private static boolean endsWithNewline(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        return last == '\n' || last == '\r';
    }

    private static Charset effectiveCharset(String requested, TextCharsets.Bom bom) {
        Charset explicit = TextCharsets.resolve(requested);
        if (explicit == null) {
            return bom == null ? StandardCharsets.UTF_8 : bom.charset();
        }
        if (bom != null && !explicit.equals(bom.charset())) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST,
                    "The selected encoding conflicts with the byte order mark of the file.");
        }
        return explicit;
    }

    private static Path requireRegularFile(TextReadRequest request) {
        if (request == null || request.getRealPath() == null) {
            throw new FileAccessException(FileAccessErrorCode.INVALID_REQUEST, "The target file is missing.");
        }
        Path path = request.getRealPath();
        if (!Files.exists(path)) {
            throw new FileAccessException(FileAccessErrorCode.FILE_NOT_FOUND, "The requested file does not exist.");
        }
        if (!Files.isRegularFile(path)) {
            throw new FileAccessException(FileAccessErrorCode.UNSUPPORTED_PREVIEW,
                    "The requested target cannot be shown as text.");
        }
        return path;
    }

    private static FileAttributes attributesOf(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            Object fileKey = attributes.fileKey();
            String generation = fileKey != null
                    ? "k:" + fileKey
                    : "c:" + attributes.creationTime().toMillis();
            return new FileAttributes(attributes.size(), generation);
        } catch (IOException e) {
            throw ioError(e);
        }
    }

    private static FileAccessException ioError(IOException cause) {
        return new FileAccessException(FileAccessErrorCode.FILE_IO_ERROR, "The requested file cannot be read.", cause);
    }

    private static TextReadResult reset(TextReadResult result, String reason) {
        result.setReset(true);
        result.setResetReason(reason);
        result.setStartPosition(0L);
        result.setNextPosition(0L);
        result.setEndOfFile(false);
        return result;
    }

    private static long alignDown(long offset, long base, int unit) {
        if (unit <= 1) {
            return offset;
        }
        long delta = (offset - base) % unit;
        if (delta < 0) {
            delta += unit;
        }
        return offset - delta;
    }

    /**
     * Skips the bytes that cannot start a character, so a block boundary inside a multi byte character
     * does not produce a replacement character at the beginning of the text.
     *
     * <p>Bytes that start a character but are not complete yet are kept: an increment waits for the
     * rest of the character instead of emitting a replacement character.</p>
     */
    private static int alignToCharStart(byte[] content, int length, Charset charset) {
        if (length <= 0) {
            return 0;
        }
        int start = 0;
        for (int attempt = 0; attempt < MAX_CHAR_BYTES && start < length; attempt++) {
            int window = Math.min(length - start, MAX_CHAR_BYTES);
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer in = ByteBuffer.wrap(content, start, window);
            CoderResult partial = decoder.decode(in, CharBuffer.allocate(MAX_CHAR_BYTES), false);
            if (partial.isUnderflow() && in.position() == 0) {
                return start;
            }

            CharsetDecoder complete = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer probe = ByteBuffer.wrap(content, start, window);
            CoderResult result = complete.decode(probe, CharBuffer.allocate(MAX_CHAR_BYTES), true);
            if (!result.isError() || probe.position() > 0) {
                return start;
            }
            start += Math.max(1, result.length());
        }
        return Math.min(start, length);
    }

    private static int countNewlines(byte[] block, int length, long baseOffset, Charset charset) {
        int count = 0;
        for (int i = 0; i < length; i++) {
            if (TextCharsets.isNewlineAt(block, i, length, baseOffset + i, charset)) {
                count++;
            }
        }
        return count;
    }

    private static long offsetAfterSkippingNewlines(SeekableByteChannel channel, long from, long size, long skip,
                                                    Charset charset) throws IOException {
        int unit = TextCharsets.newlineUnit(charset);
        byte[] block = new byte[BLOCK_SIZE];
        long position = from;
        long remaining = skip;
        while (position < size && remaining > 0) {
            int read = readChannel(channel, position, block, (int) Math.min(BLOCK_SIZE, size - position));
            if (read <= 0) {
                break;
            }
            for (int i = 0; i < read; i++) {
                if (TextCharsets.isNewlineAt(block, i, read, position + i, charset)) {
                    remaining--;
                    if (remaining == 0) {
                        return Math.min(position + i + unit, size);
                    }
                }
            }
            position += read;
        }
        return position;
    }

    private static int readChannel(SeekableByteChannel channel, long position, byte[] buffer, int length)
            throws IOException {
        channel.position(position);
        return fill(channel, ByteBuffer.wrap(buffer, 0, length));
    }

    private static int fill(SeekableByteChannel channel, ByteBuffer buffer) throws IOException {
        int total = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static byte[] readRange(Path path, long position, int length) {
        byte[] buffer = new byte[length];
        if (length == 0) {
            return buffer;
        }
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            int read = readChannel(channel, position, buffer, length);
            return read < length ? trim(buffer, read) : buffer;
        } catch (IOException e) {
            throw ioError(e);
        }
    }

    private static byte[] trim(byte[] buffer, int length) {
        byte[] trimmed = new byte[Math.max(length, 0)];
        System.arraycopy(buffer, 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private static Decoded decode(byte[] content, int from, int length, Charset charset, boolean endOfInput) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(content, from, length - from);
        CharBuffer out = CharBuffer.allocate(Math.max(16, length - from));

        CoderResult result = decoder.decode(in, out, endOfInput);
        while (result.isOverflow()) {
            out = grow(out);
            result = decoder.decode(in, out, endOfInput);
        }

        if (!result.isError()) {
            out.flip();
            return new Decoded(out.toString(), false, in.position());
        }

        CharsetDecoder replacing = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer retryIn = ByteBuffer.wrap(content, from, length - from);
        CharBuffer retryOut = CharBuffer.allocate(Math.max(16, length - from));
        CoderResult retry = replacing.decode(retryIn, retryOut, endOfInput);
        while (retry.isOverflow()) {
            retryOut = grow(retryOut);
            retry = replacing.decode(retryIn, retryOut, endOfInput);
        }
        retryOut.flip();
        return new Decoded(retryOut.toString(), true, retryIn.position());
    }

    private static CharBuffer grow(CharBuffer current) {
        CharBuffer bigger = CharBuffer.allocate(current.capacity() * 2 + 16);
        current.flip();
        bigger.put(current);
        return bigger;
    }

    /**
     * Counts decoding errors over a bounded sample.
     *
     * <p>A read that stops inside a multi byte character is not counted as an error: the incomplete
     * trailing sequence is a property of the limit, not of the content.</p>
     */
    private static Analysis analyze(byte[] content, int from, int length, Charset charset, boolean endOfInput) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(content, from, length - from);
        CharBuffer out = CharBuffer.allocate(256);
        int errors = 0;
        int chars = 0;

        while (in.hasRemaining() && chars + errors < SAMPLE_LIMIT) {
            CoderResult result = decoder.decode(in, out, endOfInput);
            if (result.isUnderflow()) {
                break;
            }
            if (result.isOverflow()) {
                chars += out.position();
                out.clear();
                continue;
            }
            if (result.isError()) {
                errors++;
                in.position(in.position() + Math.max(1, result.length()));
                continue;
            }
        }
        chars += out.position();
        return new Analysis(errors, chars);
    }

    /**
     * Heuristic over the decoded content: mostly non printable characters or mostly decoding errors
     * mean the file is not text in the selected encoding.
     *
     * <p>NUL characters are left out of the ratio on purpose. UTF-16 text decoded as UTF-8 produces
     * exactly such an interleaved NUL pattern, and rejecting it here would hide the possibility to
     * select the right encoding.</p>
     */
    private static boolean looksBinary(String text, Analysis analysis) {
        int limit = Math.min(text.length(), SAMPLE_LIMIT);
        if (limit == 0) {
            return false;
        }
        int considered = 0;
        int printable = 0;
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c == '\u0000' || c == '\uFFFD') {
                // A NUL comes from a wrong encoding guess, a replacement character from an undecodable
                // byte; both are judged by the error ratio and not by the printable ratio.
                continue;
            }
            considered++;
            if (c == '\n' || c == '\r' || c == '\t') {
                printable++;
            } else if (!Character.isISOControl(c) && !Character.isSurrogate(c)) {
                printable++;
            }
        }
        if (considered == 0) {
            return true;
        }
        double printableRatio = (double) printable / considered;
        double errorRatio = analysis.errors() <= 0
                ? 0d
                : (double) analysis.errors() / (analysis.errors() + Math.max(1, analysis.chars()));
        return printableRatio < MIN_PRINTABLE_RATIO || errorRatio > MAX_ERROR_RATIO;
    }

    private static int countLines(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return text.charAt(text.length() - 1) == '\n' ? count : count + 1;
    }

    private record Decoded(String text, boolean replaced, long consumed) {
    }

    private record Analysis(int errors, int chars) {
    }

    private record FileAttributes(long size, String generation) {
    }
}
