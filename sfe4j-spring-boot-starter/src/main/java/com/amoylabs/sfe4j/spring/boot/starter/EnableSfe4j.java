package com.amoylabs.sfe4j.spring.boot.starter;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(Sfe4jAutoConfiguration.class)
public @interface EnableSfe4j {
}
