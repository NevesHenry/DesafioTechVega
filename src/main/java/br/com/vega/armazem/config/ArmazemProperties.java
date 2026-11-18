package br.com.vega.armazem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * Configurações da aplicação (prefixo {@code armazem}).
 *
 * @param fusoHorario fuso usado para datar movimentações e definir "hoje" e "amanhã" na regra de troca de tipo
 */
@ConfigurationProperties("armazem")
public record ArmazemProperties(@DefaultValue("America/Sao_Paulo") ZoneId fusoHorario) {
}
