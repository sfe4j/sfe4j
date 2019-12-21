package com.v2code.sfe4j.util;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ServletUtils {

    public static void reThrowServletFilterException(Throwable t) throws ServletException,
            IOException {
        if (t instanceof ServletException) {
            throw (ServletException) t;
        }
        if (t instanceof IOException) {
            throw (IOException) t;
        }
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        throw new ServletException(t);
    }

    public static List<NameValuePair> listSessionAttributes(HttpSession session) {
        List<NameValuePair> list = new ArrayList<NameValuePair>();
        Enumeration attrNames = session.getAttributeNames();
        String name;
        Object value;

        while (attrNames.hasMoreElements()) {
            name = (String) attrNames.nextElement();
            value = session.getAttribute(name);

            list.add(new NameValuePair(name, value.toString()));
        }

        return list;
    }

    public static List<NameValuePair> listRequestAttributes(HttpServletRequest request) {

        List<NameValuePair> list = new ArrayList<NameValuePair>();
        Enumeration parmNames = request.getParameterNames();
        String name;
        Object value;

        while (parmNames.hasMoreElements()) {

            name = (String) parmNames.nextElement();
            if ("javax.faces.ViewState".equals(name)) {
                value = "[suppressed]";
            } else {
                value = request.getParameter(name);
            }

            list.add(new NameValuePair(name, value.toString()));
        }

        return list;
    }

    public static String getApplicationName(HttpServletRequest request) {
        return request.getRequestURI().substring(1, request.getRequestURI().indexOf("/", 1));
    }

    public static String getConfigurationSetting(ServletConfig config, String... settingKeysInPrecedenceOrder) {
        String value = null;
        for (String key : settingKeysInPrecedenceOrder) {
            value = config.getInitParameter(key);
            if (!StringUtils.isEmpty(value)) {
                return value;
            }
        }

        return value;
    }

}
