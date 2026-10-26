package com.amoylabs.sfe4j.core.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Low level file helpers.
 *
 * <p>Prefer {@code FileAccessService} for anything reached from a request: this class performs no
 * authorization and loads the whole file into memory.</p>
 */
public class FileUtils {

    /**
     * Reads a file completely.
     *
     * @deprecated unbounded memory usage; previews and downloads must use the bounded reader or a
     * streamed response
     */
    @Deprecated(since = "1.1.0")
    public static byte[] readToByteArray(File file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        }
    }
}
