package br.com.vega.armazem.domain.exception;

/** Valor de entrada (corpo ou query string) que não pode ser interpretado. A API responde com 400. */
public class ParametroInvalidoException extends ArmazemException {

    public ParametroInvalidoException(String mensagem) {
        super(CodigoErro.PARAMETRO_INVALIDO, mensagem);
    }
}
