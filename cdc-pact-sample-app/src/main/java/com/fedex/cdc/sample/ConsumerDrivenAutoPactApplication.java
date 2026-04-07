package com.fedex.cdc.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.fedex.cdc")
public class ConsumerDrivenAutoPactApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsumerDrivenAutoPactApplication.class, args);
    }
}



