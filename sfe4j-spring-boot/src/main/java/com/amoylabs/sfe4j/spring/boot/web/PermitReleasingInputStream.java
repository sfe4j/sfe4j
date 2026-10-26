package com.amoylabs.sfe4j.spring.boot.web;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Releases a download permit exactly once, when the stream is closed.
 *
 * <p>The framework closes the resource stream after the response body has been written, including when
 * the client aborts the transfer, so the permit follows the real lifetime of the transfer.</p>
 */
public class PermitReleasingInputStream extends FilterInputStream {

    private final Runnable onClose;

    private final AtomicBoolean released = new AtomicBoolean();

    public PermitReleasingInputStream(InputStream delegate, Runnable onClose) {
        super(delegate);
        this.onClose = onClose;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            if (released.compareAndSet(false, true)) {
                onClose.run();
            }
        }
    }
}
