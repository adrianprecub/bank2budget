package com.bank2budget;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentProcessorApp {

    public static void main(String[] args) {
        SpringApplication.run(PaymentProcessorApp.class, args);
    }
}
