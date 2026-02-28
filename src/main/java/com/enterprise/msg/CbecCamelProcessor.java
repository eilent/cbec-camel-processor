package com.enterprise.msg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
public class CbecCamelProcessor {
    public static void main(String[] args) {
        SpringApplication.run(CbecCamelProcessor.class, args);
    }
}
