package com.amoylabs.sfe4j.core.service;

import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.text.TextReadRequest;
import com.amoylabs.sfe4j.core.text.TextReadResult;
import com.amoylabs.sfe4j.core.text.TextReadMode;
import com.amoylabs.sfe4j.core.text.TextReadService;
import com.amoylabs.sfe4j.core.vo.TextPreviewVO;

import java.nio.file.Path;

/**
 * Reads a bounded head of an authorized file as plain text.
 *
 * <p>The file is never loaded as a whole: at most {@code maxBytes} bytes are decoded, so the memory
 * used does not depend on the file size. The work is done by {@link TextReadService}, which also owns
 * the binary heuristic and the encoding handling.</p>
 */
public class TextFileReader {

    private final TextReadService textReadService = new TextReadService();

    /**
     * Reads the head of a file.
     *
     * @param realPath    fully resolved path of a regular file
     * @param lexicalPath lexical path used to build page links
     * @param fileName    base name of the file
     * @param maxBytes    maximum number of bytes to decode, {@code sfe4j.preview.max-bytes}
     * @return bounded preview
     * @throws FileAccessException {@code UNSUPPORTED_PREVIEW} for binary content or a non regular
     *                             target, {@code FILE_NOT_FOUND} for a missing target,
     *                             {@code FILE_IO_ERROR} for an I/O failure
     */
    public TextPreviewVO read(Path realPath, String lexicalPath, String fileName, long maxBytes) {
        TextReadRequest request = new TextReadRequest();
        request.setRealPath(realPath);
        request.setMode(TextReadMode.HEAD);
        request.setLines(TextReadRequest.NO_LINE_LIMIT);
        request.setMaxBytes(maxBytes);
        request.setMaxScanBytes(maxBytes);

        TextReadResult result = textReadService.read(request);

        TextPreviewVO preview = new TextPreviewVO();
        preview.setFileName(fileName);
        preview.setPath(lexicalPath);
        preview.setContent(result.getText());
        preview.setSize(result.getFileSize());
        preview.setMaxBytes(maxBytes);
        preview.setTruncated(result.isTruncated());
        preview.setDecodingIssue(result.isDecodingIssue());
        preview.setEncoding(result.getCharset());
        return preview;
    }
}
