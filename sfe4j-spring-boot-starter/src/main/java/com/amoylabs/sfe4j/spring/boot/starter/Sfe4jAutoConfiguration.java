package com.amoylabs.sfe4j.spring.boot.starter;

import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Sfe4jProperties.class)
@ComponentScan(
        basePackages = {"com.amoylabs.sfe4j.spring.boot"}
)
public class Sfe4jAutoConfiguration {

    @Bean
    public Sfe4jConfiguration initSfe4jConfiguration(Sfe4jProperties properties) {
        Sfe4jConfiguration configuration = new Sfe4jConfiguration();
        configuration.setTitle(properties.getTitle());
        configuration.setDescription(properties.getDescription());
        configuration.setQuickLinks(properties.getQuickLinks());
        configuration.setBaseDirPath(StringUtils.isNotEmpty(properties.getBaseDirPath()) ? properties.getBaseDirPath() : "/");
        configuration.setRestrictToBaseDir(properties.getRestrictToBaseDir() != null ? properties.getRestrictToBaseDir() : false);
        return configuration;
    }
}
