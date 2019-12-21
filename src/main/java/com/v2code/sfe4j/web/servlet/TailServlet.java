package com.v2code.sfe4j.web.servlet;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by nikog on 6/21/2016.
 */
public class TailServlet extends BaseServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String filePath = request.getParameter("file");

        if (StringUtils.isNotEmpty(filePath)) {
            String encodedFilePath = new String(Base64.getEncoder().encode(filePath.getBytes()));
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();
            String contextPath = request.getContextPath();
            if (contextPath.startsWith("/")) {
                contextPath = contextPath.substring(1);
            }

            String socketURL = String.format("ws://%s:%s/%s/socket/tail/%s", serverName, serverPort, contextPath, encodedFilePath);

            if (serverPort == 80 || serverPort == 443) {
                socketURL = String.format("ws://%s/%s/socket/tail/%s", serverName, contextPath, encodedFilePath);
            }


            Map<String, Object> variableMap = new HashMap<>();
            variableMap.put("socketURL", socketURL);
            variableMap.put("filePath", filePath);

            render(request, response, "tail.ftl", variableMap);
        } else {
            Map<String, Object> variableMap = new HashMap<>();
            variableMap.put("message", "Parameter 'file' can't be empty!");
            render(request, response, "message.ftl", variableMap);
        }
    }
}
