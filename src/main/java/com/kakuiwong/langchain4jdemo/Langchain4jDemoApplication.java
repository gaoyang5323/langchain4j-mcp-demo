package com.kakuiwong.langchain4jdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Langchain4jDemoApplication {

    public static void main(String[] args) {
        System.setProperty("langchain4j.http.clientBuilderFactory", "dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory");
        SpringApplication.run(Langchain4jDemoApplication.class, args);
    }

}
