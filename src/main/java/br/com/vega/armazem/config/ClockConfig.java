package br.com.vega.armazem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * O relógio é injetado, não lido de {@code now()} estático. Assim os testes conseguem simular "amanhã" para
 * a regra de troca de tipo do compartimento.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock(ArmazemProperties properties) {
        return Clock.system(properties.fusoHorario());
    }
}
