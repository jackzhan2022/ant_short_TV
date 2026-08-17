package com.antshorttv;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.antshorttv")
@EnableScheduling
@SpringBootApplication
public class AntShortTvApplication {

    public static void main(String[] args) {
        SpringApplication.run(AntShortTvApplication.class, args);
    }
}
