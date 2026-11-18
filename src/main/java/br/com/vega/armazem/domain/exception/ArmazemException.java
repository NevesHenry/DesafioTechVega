package br.com.vega.armazem.domain.exception;

/** Base das exceções de negócio da aplicação; carrega um {@link CodigoErro} estável. */
public abstract class ArmazemException extends RuntimeException {

    private final CodigoErro codigo;

    protected ArmazemException(CodigoErro codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public CodigoErro getCodigo() {
        return codigo;
    }
}
