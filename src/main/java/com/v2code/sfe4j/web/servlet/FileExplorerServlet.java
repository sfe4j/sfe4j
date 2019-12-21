package com.v2code.sfe4j.web.servlet;

import com.v2code.sfe4j.exception.Sfe4jRuntimeException;
import com.v2code.sfe4j.util.ServletUtils;
import com.v2code.sfe4j.vo.FileWrapperVO;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class FileExplorerServlet extends BaseServlet {

    private static final long serialVersionUID = -3651856296828821466L;

    private FileExplorerRestrictions fileExplorerRestrictions = new FileExplorerRestrictions();
    private String baseDirectoryName = null;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        String restrictStr = ServletUtils.getConfigurationSetting(config, "restrict.to.base.dir");

        if ("false".equalsIgnoreCase(restrictStr)) {
            this.fileExplorerRestrictions.setRestrictToBase(false);
        }

        String restrictFromExecutionStr = ServletUtils.getConfigurationSetting(config, "restrict.from.exec");
        if ("false".equalsIgnoreCase(restrictFromExecutionStr)) {
            this.fileExplorerRestrictions.setRestrictFromExecution(false);
        }

        String restrictFromWriteStr = ServletUtils.getConfigurationSetting(config, "restrict.from.write");
        if ("false".equalsIgnoreCase(restrictFromWriteStr)) {
            this.fileExplorerRestrictions.setRestrictFromWrite(false);
        }

        baseDirectoryName = ServletUtils.getConfigurationSetting(config, "base.dir.name");
        if (StringUtils.isEmpty(baseDirectoryName)) {
            baseDirectoryName = System.getProperty("user.dir");
        }

        File baseDir = new File(baseDirectoryName);
        if (!baseDir.exists()) {
            throw new Sfe4jRuntimeException("Base Directory (base.dir.name) doesn't exist").addContextValue("base.dir.name", baseDirectoryName);
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        String displayDirectoryName = request.getParameter("dir");
        if (StringUtils.isEmpty(displayDirectoryName)) {
            displayDirectoryName = request.getParameter("dirInMkDir");
        }
        String downloadFileName = request.getParameter("download");
        List<FileItem> fileItems = getMultipartRequestItems(request);
        String baseDirectoryNameDuringUpload = getMultipartRequestParam(fileItems, "dirInUpload");

        if (StringUtils.isEmpty(displayDirectoryName) && StringUtils.isEmpty(baseDirectoryNameDuringUpload)) {
            displayDirectoryName = this.baseDirectoryName;
        } else if (StringUtils.isEmpty(displayDirectoryName)) {
            displayDirectoryName = baseDirectoryNameDuringUpload;
        }

        if (StringUtils.isEmpty(downloadFileName)) {
            File baseDir = new File(displayDirectoryName);
            String requestMessage = processRequest(request, baseDir, fileItems);
            this.presentDirectoryListing(baseDir, requestMessage, request, response);
        } else {
            this.presentFileContent(downloadFileName, request, response);
        }
    }

    private void presentFileContent(String filename, HttpServletRequest request, HttpServletResponse response) throws IOException {

        File displayFile = new File(filename);
        FileInputStream stream = new FileInputStream(displayFile);
        byte[] content = IOUtils.toByteArray(stream);
        stream.close();

        response.setContentType("application/octet-stream"); // or "application/octet-stream", your choice
        response.setCharacterEncoding("UTF-8");
        response.setHeader("content-type", "text/html;charset=UTF-8");
        response.setContentLength(content.length);
        response.setHeader("Content-disposition", "attachment; filename=\"" + new String(displayFile.getName().getBytes("UTF-8"), "ISO-8859-1") + "\"");
        try {
            response.getOutputStream().write(content);
        } catch (IOException e) {
            throw new Sfe4jRuntimeException("Failed to write out the file.", e);
        }
    }

    private void presentDirectoryListing(File baseDir, String requestMessage, HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");

        Set<FileWrapperVO> subDirectorySet = new TreeSet<FileWrapperVO>();
        Set<FileWrapperVO> fileSet = new TreeSet<FileWrapperVO>();
        Set<FileWrapperVO> rootSet = new TreeSet<FileWrapperVO>();

        for (File file : baseDir.listFiles()) {
            if (file.isDirectory()) subDirectorySet.add(new FileWrapperVO(file));
            else fileSet.add(new FileWrapperVO(file));
        }
        for (File file : File.listRoots()) {
            rootSet.add(new FileWrapperVO(file));
        }

        Map<String, Object> variableMap = new HashMap<String, Object>();
        variableMap.put("restrictions", fileExplorerRestrictions);
        variableMap.put("message", requestMessage);
        variableMap.put("currentDirectory", new FileWrapperVO(baseDir));
        if (baseDir.getParentFile() != null && !fileExplorerRestrictions.isRestrictToBase()) {
            variableMap.put("parentDirectory", new FileWrapperVO(baseDir.getParentFile()));
        } else if (baseDir.getParentFile() != null && fileExplorerRestrictions.isRestrictToBase() && isWithinBase(baseDir.getParentFile())) {
            variableMap.put("parentDirectory", new FileWrapperVO(baseDir.getParentFile()));
        }
        variableMap.put("subdirectoryList", subDirectorySet);
        variableMap.put("rootList", rootSet);
        variableMap.put("fileList", fileSet);

        render(request, response, "file-explorer.ftl", variableMap);
    }

    private boolean isWithinBase(File dir) {
        boolean answer = false;
        File localDir = dir;
        File baseDir = new File(this.baseDirectoryName);
        if (baseDir.equals(dir)) {
            answer = true;
        }

        while (!answer && localDir.getParentFile() != null) {
            if (baseDir.equals(localDir.getParentFile())) {
                answer = true;
            }
            localDir = localDir.getParentFile();
        }

        return answer;
    }

    private String processRequest(HttpServletRequest request, File baseDir, List<FileItem> items) {
        String runExecutableName = request.getParameter("run");
        String deleteFileName = request.getParameter("delete");
        String mkDirName = request.getParameter("dirName");

        String message = "";
        if (!StringUtils.isEmpty(runExecutableName)) {
            message = this.runExecutable(runExecutableName);
        } else if (ServletFileUpload.isMultipartContent(request)) {
            message = this.processUpload(request, baseDir, items);
        } else if (!StringUtils.isEmpty(deleteFileName)) {
            message = this.processDelete(deleteFileName);
        } else if (!StringUtils.isEmpty(mkDirName)) {
            message = this.processMkdir(mkDirName, baseDir);
        }
        return message;
    }

    private String processUpload(HttpServletRequest request, File directory, List<FileItem> items) {
        if (this.fileExplorerRestrictions.isRestrictFromWrite()) {
            return "File Upload not allowed.  Modify restrict.from.write setting to allow file upload.";
        }

        String message = "";
        try {
            File uploadedFile;
            for (FileItem item : items) {
                if (!StringUtils.isEmpty(item.getName())) {
                    uploadedFile = new File(directory.getCanonicalPath() + "/" + item.getName());
                    item.write(uploadedFile);
                    message = "File " + item.getName() + " (" + item.getSize() + " bytes) Uploaded!";
                }
            }
        } catch (Exception e) {
            message = ExceptionUtils.getStackTrace(e);
        }
        return message;
    }

    private String processDelete(String deleteFileName) {
        if (this.fileExplorerRestrictions.isRestrictFromWrite()) {
            return "File delete not allowed.  Modify restrict.from.write setting to allow delete.";
        }

        File deleteFile = new File(deleteFileName);
        if (!deleteFile.delete()) {
            return "File not deleted.  Reason not known.  See javadoc for File.delete().";
        }

        return "File deleted";
    }

    private String processMkdir(String dirName, File baseDirectory) {
        if (this.fileExplorerRestrictions.isRestrictFromWrite()) {
            return "Directory creation not allowed.  Modify restrict.from.write setting to allow directory creation.";
        }

        String message = null;
        try {
            File newDir = new File(baseDirectory.getCanonicalPath() + "/" + dirName);
            if (newDir.mkdir()) {
                message = "New Directory created: " + dirName;
            } else
                message = "New Directory not created: " + dirName + ".  Reason unknown.  See javadoc for File.mkdir().";
        } catch (Exception e) {
            message = ExceptionUtils.getStackTrace(e);
        }

        return message;
    }

    private String runExecutable(String executableName) {

        if (this.fileExplorerRestrictions.isRestrictFromExecution()) {
            return "File execution not allowed.  Modify restrict.from.exec setting to allow execution.";
        }

        File executableFile = new File(executableName);
        StringBuffer message = new StringBuffer();

        try {
            Process process = Runtime.getRuntime().exec(new String[]{executableName}, null, executableFile.getParentFile());

            String error = IOUtils.toString(process.getErrorStream());
            if (!StringUtils.isEmpty(error)) {
                message.append(error);
            }

            String stdOut = IOUtils.toString(process.getInputStream());
            if (!StringUtils.isEmpty(stdOut)) {
                message.append(stdOut);
            }
        } catch (Throwable e) {
            throw new Sfe4jRuntimeException("error executing file.", e);
        }

        return message.toString();
    }

    public static class FileExplorerRestrictions implements Serializable {

        private static final long serialVersionUID = -6396405187849729874L;
        private boolean restrictToBase = true;
        private boolean restrictFromExecution = true;
        private boolean restrictFromWrite = true;

        public boolean isRestrictToBase() {
            return restrictToBase;
        }

        public void setRestrictToBase(boolean restrictToBase) {
            this.restrictToBase = restrictToBase;
        }

        public boolean isRestrictFromExecution() {
            return restrictFromExecution;
        }

        public void setRestrictFromExecution(boolean restrictFromExecution) {
            this.restrictFromExecution = restrictFromExecution;
        }

        public boolean isRestrictFromWrite() {
            return restrictFromWrite;
        }

        public void setRestrictFromWrite(boolean restrictFromWrite) {
            this.restrictFromWrite = restrictFromWrite;
        }
    }

    private List<FileItem> getMultipartRequestItems(HttpServletRequest request) {
        if (!ServletFileUpload.isMultipartContent(request)) return null;

        List<FileItem> items;
        try {
            items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
        } catch (FileUploadException e) {
            throw new RuntimeException("Could not parse multipart request.", e);
        }
        return items;
    }

    private String getMultipartRequestParam(List<FileItem> items, String paramName) {
        if (items == null) return null;

        for (FileItem item : items) {
            if (item.isFormField() && paramName.equals(item.getFieldName())) {
                return item.getString();
            }
        }
        return null;
    }
}
