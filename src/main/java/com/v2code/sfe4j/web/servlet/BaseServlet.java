package com.v2code.sfe4j.web.servlet;

import com.v2code.sfe4j.core.FreemarkerEngine;
import com.v2code.sfe4j.exception.Sfe4jRuntimeException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

/**
 * Created by nikog on 6/6/2016.
 */
public class BaseServlet extends HttpServlet {

    public static final String SESSION_VARIABLE_PREFIX = "SFE4J";

    public void render(HttpServletRequest request,
                       HttpServletResponse response, String templateName,
                       Map<String, Object> variableMap) throws IOException {

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("content-type", "text/html;charset=UTF-8");

        renderFreemarker(request, response, templateName, variableMap);
    }

    private void renderFreemarker(HttpServletRequest request,
                                  HttpServletResponse response,
                                  String templateName, Map<String, Object> variableMap) throws IOException {

        Enumeration<String> attrNameEnum = request.getSession().getAttributeNames();
        String attrName;
        while (attrNameEnum.hasMoreElements()) {
            attrName = attrNameEnum.nextElement();
            if (attrName != null && attrName.startsWith(SESSION_VARIABLE_PREFIX)) {
                variableMap.put("Session" + attrName, request.getSession().getAttribute(attrName));
            }
        }
        variableMap.put("currentRequestURI", request.getRequestURI());
        variableMap.put("ctx", request.getContextPath());

        try {
            String result = FreemarkerEngine.getInstance().process(templateName, variableMap);

            response.setContentLength(result.getBytes("UTF-8").length);
            response.getOutputStream().write(result.getBytes("UTF-8"));

            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (Exception e) {
            throw new Sfe4jRuntimeException(e);
        }
    }
}
