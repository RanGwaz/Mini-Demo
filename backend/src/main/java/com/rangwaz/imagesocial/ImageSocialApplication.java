package com.rangwaz.imagesocial;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan("com.rangwaz.imagesocial.**.mapper")
@ConfigurationPropertiesScan
public class ImageSocialApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImageSocialApplication.class, args);
    }
}
