package pl.example.importer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ImporterProperties.class)
public class ImporterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImporterApplication.class, args);
    }
}
