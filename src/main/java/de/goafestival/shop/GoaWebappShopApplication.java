package de.goafestival.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GoaWebappShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoaWebappShopApplication.class, args);
    }
}
