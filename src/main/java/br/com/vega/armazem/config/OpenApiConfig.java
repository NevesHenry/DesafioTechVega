package br.com.vega.armazem.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI armazemOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Armazém de Ingredientes API")
                .version("1.0.0")
                .description("""
                        Gestão de estoque de ingredientes em 5 compartimentos (C1 a C5).
                        Capacidades: SECO 600 kg, LIQUIDO 500 L, REFRIGERADO 400 kg.
                        Um compartimento guarda um tipo por vez e, depois de usado por um tipo, só aceita outro \
                        tipo no dia seguinte. Toda entrada e saída gera histórico.
                        Erros seguem a RFC 9457 (application/problem+json) com o campo `codigo`."""));
    }
}
