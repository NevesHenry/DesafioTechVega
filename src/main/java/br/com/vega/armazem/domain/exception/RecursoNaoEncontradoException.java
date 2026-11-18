package br.com.vega.armazem.domain.exception;

/** Recurso referenciado na URL não existe. A API responde com 404 Not Found. */
public class RecursoNaoEncontradoException extends ArmazemException {

    public RecursoNaoEncontradoException(CodigoErro codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public static RecursoNaoEncontradoException ingrediente(Long id) {
        return new RecursoNaoEncontradoException(CodigoErro.INGREDIENTE_NAO_ENCONTRADO,
                "Ingrediente %d não encontrado.".formatted(id));
    }

    public static RecursoNaoEncontradoException compartimento(Long id) {
        return new RecursoNaoEncontradoException(CodigoErro.COMPARTIMENTO_NAO_ENCONTRADO,
                "Compartimento %d não encontrado.".formatted(id));
    }
}
