package com.asp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AspApplication {

    public static void main(String[] args) {
        SpringApplication.run(AspApplication.class, args);
    }
}


