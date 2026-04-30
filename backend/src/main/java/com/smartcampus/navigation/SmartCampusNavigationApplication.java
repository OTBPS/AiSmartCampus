package com.smartcampus.navigation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.smartcampus.navigation")
@SpringBootApplication
public class SmartCampusNavigationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartCampusNavigationApplication.class, args);
    }
}

