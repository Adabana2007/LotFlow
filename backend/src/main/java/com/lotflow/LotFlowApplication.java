package com.lotflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** LotFlow 后端应用入口。 */
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class LotFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(LotFlowApplication.class, args);
    }
}
