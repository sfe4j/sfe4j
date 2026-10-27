package com.amoylabs.sfe4j.core.service;

import com.amoylabs.sfe4j.core.access.FileAccessContext;
import com.amoylabs.sfe4j.core.access.FileAccessService;
import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.constant.GlobalConstants;
import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;
import com.amoylabs.sfe4j.core.text.TextReadRequest;
import com.amoylabs.sfe4j.core.text.TextReadResult;
import com.amoylabs.sfe4j.core.vo.FileContentVO;
import com.amoylabs.sfe4j.core.vo.FileDownloadVO;
import com.amoylabs.sfe4j.core.vo.FileTreeVO;
import com.amoylabs.sfe4j.core.vo.TextPreviewVO;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Page oriented facade kept for source compatibility.
 *
 * <p>Every method delegates to {@link FileAccessService}. The legacy methods without an identity
 * parameter run with {@link FileAccessContext#anonymous()}, which means a protected directory is always
 * rejected: compatibility never keeps an unauthorized read channel.</p>
 */
public class FileExplorerService {

    private Sfe4jConfiguration sfe4jConfiguration;

    private FileAccessService fileAccessService;

    public Map<String, Object> buildMetaInfo() {
        Map<String, Object> metaInfo = new HashMap<>();
        metaInfo.put("title", StringUtils.isNotEmpty(sfe4jConfiguration.getTitle())
                ? sfe4jConfiguration.getTitle() : GlobalConstants.DEFAULT_TITLE);
        metaInfo.put("description", StringUtils.isNotEmpty(sfe4jConfiguration.getDescription())
                ? sfe4jConfiguration.getDescription() : GlobalConstants.DEFAULT_DESCRIPTION);
        metaInfo.put("quickLinks", sfe4jConfiguration.getQuickLinks() != null
                ? sfe4jConfiguration.getQuickLinks() : Collections.emptyMap());
        return metaInfo;
    }

    /**
     * @deprecated use {@link #buildFileTree(String, FileAccessContext)} and pass the caller identity
     */
    @Deprecated(since = "1.1.0")
    public FileTreeVO buildFileTree(String dir) {
        return buildFileTree(dir, FileAccessContext.anonymous());
    }

    public FileTreeVO buildFileTree(String dir, FileAccessContext context) {
        return access().list(dir, context);
    }

    /**
     * @deprecated the legacy signature has no identity; protected files are rejected and the read is
     * bounded by {@code sfe4j.preview.max-bytes}
     */
    @Deprecated(since = "1.1.0")
    public FileContentVO readFile(String filePath) {
        return readFile(filePath, FileAccessContext.anonymous());
    }

    public FileContentVO readFile(String filePath, FileAccessContext context) {
        return access().read(filePath, context);
    }

    public TextPreviewVO preview(String filePath, FileAccessContext context) {
        return access().preview(filePath, context);
    }

    public TextReadResult readText(String filePath, FileAccessContext context, TextReadRequest request) {
        return access().readText(filePath, context, request);
    }

    public TextReadResult readTextIncrement(String filePath, FileAccessContext context, TextReadRequest request) {
        return access().readTextIncrement(filePath, context, request);
    }

    public FileDownloadVO openDownload(String filePath, FileAccessContext context) {
        return access().openDownload(filePath, context);
    }

    public FileAccessService getFileAccessService() {
        return access();
    }

    private FileAccessService access() {
        FileAccessService service = this.fileAccessService;
        if (service != null) {
            return service;
        }
        synchronized (this) {
            if (this.fileAccessService == null) {
                if (this.sfe4jConfiguration == null) {
                    throw new Sfe4jRuntimeException("Sfe4jConfiguration must be set before using FileExplorerService.");
                }
                this.fileAccessService = new FileAccessService(this.sfe4jConfiguration);
            }
            return this.fileAccessService;
        }
    }

    /**
     * Setter
     */
    public void setSfe4jConfiguration(Sfe4jConfiguration sfe4jConfiguration) {
        this.sfe4jConfiguration = sfe4jConfiguration;
        this.fileAccessService = null;
    }

    public void setFileAccessService(FileAccessService fileAccessService) {
        this.fileAccessService = fileAccessService;
    }
}
