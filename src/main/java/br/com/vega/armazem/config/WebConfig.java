package br.com.vega.armazem.config;

import br.com.vega.armazem.domain.model.TipoIngrediente;
import br.com.vega.armazem.domain.model.TipoOperacao;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Converte parâmetros de query ({@code ?tipo=secos}) com a mesma leitura tolerante usada no JSON. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TipoIngrediente.class,
                valor -> valor.isBlank() ? null : TipoIngrediente.of(valor));
        registry.addConverter(String.class, TipoOperacao.class,
                valor -> valor.isBlank() ? null : TipoOperacao.of(valor));
    }
}
