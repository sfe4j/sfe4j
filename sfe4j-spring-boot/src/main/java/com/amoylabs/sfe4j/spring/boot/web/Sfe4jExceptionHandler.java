package com.amoylabs.sfe4j.spring.boot.web;

import com.amoylabs.sfe4j.core.exception.FileAccessErrorCode;
import com.amoylabs.sfe4j.core.exception.FileAccessException;
import com.amoylabs.sfe4j.core.service.FileExplorerService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps {@link FileAccessException} and request level failures to the documented error contract.
 *
 * <p>Browser requests receive an HTML page with the right status code; requests that accept JSON receive
 * {@code {code, message, requestId}}. Neither variant contains the original exception text, a stack trace
 * or a host path.</p>
 */
@ControllerAdvice
public class Sfe4jExceptionHandler {

    private static final String REQUEST_ID_ATTRIBUTE = Sfe4jExceptionHandler.class.getName() + ".requestId";

    /**
     * Prefix of the JSON text read API; P2 makes it configurable.
     */
    private static final String API_PREFIX = "/sfe4j/api";

    private FileExplorerService fileExplorerService;

    @ExceptionHandler(FileAccessException.class)
    public ModelAndView handleFileAccessException(FileAccessException exception, HttpServletRequest request,
                                                  HttpServletResponse response) throws IOException {
        return render(exception.getCode(), exception.getMessage(), request, response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ModelAndView handleMissingParameter(MissingServletRequestParameterException exception,
                                               HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        return render(FileAccessErrorCode.INVALID_REQUEST, "A required parameter is missing.", request, response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request,
                                           HttpServletResponse response) throws IOException {
        return render(FileAccessErrorCode.INVALID_REQUEST, "A parameter has an invalid value.", request, response);
    }

    @ExceptionHandler(IOException.class)
    public ModelAndView handleIoException(IOException exception, HttpServletRequest request,
                                          HttpServletResponse response) throws IOException {
        return render(FileAccessErrorCode.FILE_IO_ERROR, "The requested operation failed because of an I/O error.",
                request, response);
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ModelAndView handleUncheckedIoException(UncheckedIOException exception, HttpServletRequest request,
                                                   HttpServletResponse response) throws IOException {
        return render(FileAccessErrorCode.FILE_IO_ERROR, "The requested operation failed because of an I/O error.",
                request, response);
    }

    private ModelAndView render(FileAccessErrorCode code, String message, HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        String requestId = requestId(request);
        int status = code.getHttpStatus();

        response.setStatus(status);
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (code == FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED || code == FileAccessErrorCode.ACCESS_DENIED) {
            response.setHeader("Cache-Control", "no-store");
        }

        if (isApiRequest(request) || wantsJson(request)) {
            writeJson(response, code, message, requestId);
            return null;
        }

        ModelAndView modelAndView = new ModelAndView("file-error");
        modelAndView.addObject("ctx", request.getContextPath());
        modelAndView.addObject("status", status);
        modelAndView.addObject("code", code.name());
        modelAndView.addObject("message", message);
        modelAndView.addObject("requestId", requestId);
        modelAndView.addObject("meta", meta());
        if (code == FileAccessErrorCode.DIRECTORY_AUTH_REQUIRED) {
            modelAndView.addObject("backLink", authorizationLink(request));
            modelAndView.addObject("backLabel", "Go to the authorization page");
        }
        return modelAndView;
    }

    private Map<String, Object> meta() {
        return fileExplorerService == null ? Map.of() : fileExplorerService.buildMetaInfo();
    }

    /**
     * Points to the authorization page of the parent directory of the requested file.
     */
    private String authorizationLink(HttpServletRequest request) {
        String base = request.getContextPath() + "/file-explorer";
        String file = request.getParameter("file");
        if (file == null || file.isEmpty()) {
            return base;
        }
        int separator = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        if (separator < 0) {
            return base;
        }
        String parent = separator == 0 ? file.substring(0, 1) : file.substring(0, separator);
        return base + "?dir=" + UrlCodec.encodeQueryValue(parent);
    }

    /**
     * The JSON API always answers JSON, even when the client does not send an {@code Accept} header:
     * an HTML error page inside an event stream or a fetch response is not usable.
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(request.getContextPath() + API_PREFIX);
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && (accept.contains("application/json") || accept.contains("application/problem+json"));
    }

    private void writeJson(HttpServletResponse response, FileAccessErrorCode code, String message, String requestId)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code.name());
        body.put("message", message == null ? "" : message);
        body.put("requestId", requestId);

        StringBuilder json = new StringBuilder(64);
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : body.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":\"").append(JsonWriter.escape(entry.getValue())).append('"');
        }
        json.append('}');

        response.getWriter().write(json.toString());
    }

    private static String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String) {
            return (String) existing;
        }
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return requestId;
    }

    @Autowired
    public void setFileExplorerService(FileExplorerService fileExplorerService) {
        this.fileExplorerService = fileExplorerService;
    }
}
