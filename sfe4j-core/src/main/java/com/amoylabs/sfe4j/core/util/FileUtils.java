package com.amoylabs.sfe4j.core.util;

import java.io.*;

public class FileUtils {

    public static byte[] readToByteArray(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }
}
