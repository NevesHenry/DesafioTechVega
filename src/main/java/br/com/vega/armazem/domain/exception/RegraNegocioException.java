package br.com.vega.armazem.domain.exception;

/** Violação de uma regra de armazenamento. A API responde com 400 Bad Request. */
public class RegraNegocioException extends ArmazemException {

    public RegraNegocioException(CodigoErro codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
