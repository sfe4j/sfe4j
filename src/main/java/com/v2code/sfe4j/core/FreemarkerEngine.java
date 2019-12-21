package com.v2code.sfe4j.core;

import com.v2code.sfe4j.exception.Sfe4jRuntimeException;
import freemarker.cache.FileTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;

public class FreemarkerEngine {
    private Configuration cfg;

    private FreemarkerEngine() throws IOException {
        cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        cfg.setTemplateLoader(new FileTemplateLoader(new File(FreemarkerEngine.class.getResource("/templates").getPath())));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setEncoding(Locale.SIMPLIFIED_CHINESE, "UTF-8");
    }

    private Template prepareTemplate(String templatePath) throws IOException {
        Validate.notEmpty(templatePath, "Null or blank templateName not allowed.");
        return cfg.getTemplate(templatePath, Locale.SIMPLIFIED_CHINESE, "UTF-8");
    }

    public String process(String templatePath, Map<String, Object> variableMap) {
        try {
            Template template = prepareTemplate(templatePath);
            StringWriter writer = new StringWriter();
            template.process(variableMap, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new Sfe4jRuntimeException(e);
        }
    }

    // ******* Instance *******

    public static FreemarkerEngine _instance = null;

    public final static FreemarkerEngine getInstance() throws IOException {
        if (_instance == null) {
            _instance = new FreemarkerEngine();
        }
        return _instance;
    }

}
