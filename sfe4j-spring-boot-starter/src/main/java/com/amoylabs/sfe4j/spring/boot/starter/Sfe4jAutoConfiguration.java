package com.amoylabs.sfe4j.spring.boot.starter;

import com.amoylabs.sfe4j.spring.boot.configuration.Sfe4jConfiguration;
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
    public Sfe4jConfiguration sfe4jConfiguration(Sfe4jProperties sfe4jProperties) {
        Sfe4jConfiguration sfe4jConfiguration = new Sfe4jConfiguration();
        sfe4jConfiguration.setBaseDirPath(sfe4jProperties.getBaseDirPath());
        sfe4jConfiguration.setRestrictToBaseDir(sfe4jProperties.getRestrictToBaseDir());
        return sfe4jConfiguration;
    }
}
