package com.example.Lumi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.Lumi")
@EntityScan("com.example.Lumi.model")
@EnableJpaRepositories("com.example.Lumi.repository")
public class LumiApplication {

	public static void main(String[] args) {
		SpringApplication.run(LumiApplication.class, args);
	}

}
