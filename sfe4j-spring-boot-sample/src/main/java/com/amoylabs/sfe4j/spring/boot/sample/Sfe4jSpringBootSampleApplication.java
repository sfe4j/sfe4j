package com.amoylabs.sfe4j.spring.boot.sample;

import com.amoylabs.sfe4j.spring.boot.starter.EnableSfe4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableSfe4j
public class Sfe4jSpringBootSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(Sfe4jSpringBootSampleApplication.class, args);
    }

}
