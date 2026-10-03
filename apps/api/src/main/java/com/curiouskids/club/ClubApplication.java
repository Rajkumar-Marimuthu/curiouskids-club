package com.curiouskids.club;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClubApplication {

  public static void main(String[] args) {
    SpringApplication.run(ClubApplication.class, args);
  }
}
