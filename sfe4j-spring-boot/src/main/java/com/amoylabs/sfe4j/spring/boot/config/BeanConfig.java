package com.amoylabs.sfe4j.spring.boot.config;

import com.amoylabs.sfe4j.core.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.core.service.FileExplorerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public FileExplorerService initFileExplorerService(Sfe4jConfiguration fileExplorerProperties) {
        FileExplorerService fileExplorerService = new FileExplorerService();
        fileExplorerService.setSfe4jConfiguration(fileExplorerProperties);
        return fileExplorerService;
    }
}
