package br.com.vega.armazem.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.ZoneId;

@TestConfiguration(proxyBeanMethods = false)
public class RelogioTestConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    public static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 17, 10, 0);

    @Bean
    @Primary
    public RelogioAjustavel relogioAjustavel() {
        return new RelogioAjustavel(INICIO, ZONA);
    }
}
