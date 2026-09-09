package com.zenalyst.milkcollection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MilkCollectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MilkCollectionApplication.class, args);
    }
}
