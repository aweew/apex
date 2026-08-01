package com.awe.apex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApexApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApexApplication.class, args);
    }

}
