package com.amoylabs.sfe4j.startertest;

import com.amoylabs.sfe4j.spring.boot.starter.Sfe4jProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@code sfe4j.*} properties without the component scan of the starter, so that a test can
 * convert them into the core configuration and observe the failure itself.
 *
 * <p>It deliberately lives outside {@code com.amoylabs.sfe4j.spring.boot}: the starter scans that
 * package recursively, and a test configuration nested inside it would be picked up as a bean.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(Sfe4jProperties.class)
public class PropertiesOnlyConfiguration {
}
