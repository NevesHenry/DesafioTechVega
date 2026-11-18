package br.com.vega.armazem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ArmazemIngredientesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArmazemIngredientesApplication.class, args);
    }
}
