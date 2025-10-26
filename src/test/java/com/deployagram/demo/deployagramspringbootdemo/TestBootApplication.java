package com.deployagram.demo.deployagramspringbootdemo;

import org.springframework.boot.SpringApplication;

public class TestBootApplication {

    public static void main(String[] args) {
        SpringApplication.from(TestBootApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
