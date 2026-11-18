package br.com.vega.armazem.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalização de textos usados como chave (nomes de ingredientes, valores de enums). */
public final class Textos {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    private Textos() {
    }

    /** Remove acentos, espaços extras e padroniza caixa: {@code "  Açúcar  Mascavo "} vira {@code "acucar mascavo"}. */
    public static String normalizar(String texto) {
        String semAcento = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return ESPACOS.matcher(semAcento.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /** Remove espaços nas pontas e colapsa espaços internos, preservando acentos e caixa. */
    public static String limpar(String texto) {
        return ESPACOS.matcher(texto.strip()).replaceAll(" ");
    }

    /** Forma usada para comparar com nomes de constantes de enum: {@code "líquidos"} vira {@code "LIQUIDOS"}. */
    static String chaveDeEnum(String texto) {
        return normalizar(texto).replace(' ', '_').toUpperCase(Locale.ROOT);
    }
}
