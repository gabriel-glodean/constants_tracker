package org.glodean.constants;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class Main {
    void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
