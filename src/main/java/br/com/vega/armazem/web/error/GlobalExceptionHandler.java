package br.com.vega.armazem.web.error;

import br.com.vega.armazem.domain.exception.ArmazemException;
import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.ParametroInvalidoException;
import br.com.vega.armazem.domain.exception.RecursoNaoEncontradoException;
import br.com.vega.armazem.domain.exception.RegraNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converte exceções em respostas RFC 9457 ({@code application/problem+json}) com mensagens em português.
 * Além dos campos padrão ({@code type, title, status, detail, instance}), toda resposta traz {@code codigo}
 * (estável, para uso por clientes) e {@code timestamp}. Erros de validação trazem também {@code erros}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /** Item de erro de validação, referente a um campo do corpo ou a um parâmetro. */
    public record ErroCampo(String campo, String mensagem) {
    }

    // ------------------------------------------------------------------ exceções da aplicação

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ProblemDetail> regraDeNegocio(RegraNegocioException ex) {
        log.info("Regra de negócio violada [{}]: {}", ex.getCodigo(), ex.getMessage());
        return resposta(problema(HttpStatus.BAD_REQUEST, "Regra de negócio violada", ex.getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<ProblemDetail> parametroInvalido(ParametroInvalidoException ex) {
        return resposta(problema(HttpStatus.BAD_REQUEST, "Requisição inválida", ex.getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ProblemDetail> naoEncontrado(RecursoNaoEncontradoException ex) {
        return resposta(problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getCodigo(), ex.getMessage()));
    }

    /** Timeout de lock pessimista ou conflito de versão (lock otimista). O cliente pode repetir a operação. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ProblemDetail> concorrencia(ConcurrencyFailureException ex) {
        log.warn("Conflito de concorrência: {}", ex.getMessage());
        return resposta(problema(HttpStatus.CONFLICT, "Conflito de concorrência", CodigoErro.CONFLITO_DE_CONCORRENCIA,
                "Outra operação está movimentando o mesmo compartimento neste momento. Tente novamente."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> integridade(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return resposta(problema(HttpStatus.CONFLICT, "Conflito de dados", CodigoErro.VIOLACAO_DE_INTEGRIDADE,
                "A operação viola uma restrição de integridade dos dados (ex.: registro duplicado)."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(Exception ex) {
        log.error("Erro inesperado", ex);
        return resposta(problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", CodigoErro.ERRO_INTERNO,
                "Ocorreu um erro inesperado. Tente novamente ou contate o suporte."));
    }

    // ------------------------------------------------------------------ exceções do Spring MVC

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<ErroCampo> erros = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> erros.add(new ErroCampo(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> erros.add(new ErroCampo(e.getObjectName(), e.getDefaultMessage())));
        return handleExceptionInternal(ex, problemaDeValidacao(erros), headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<ErroCampo> erros = new ArrayList<>();
        ex.getParameterValidationResults().forEach(resultado -> resultado.getResolvableErrors().forEach(erro ->
                erros.add(new ErroCampo(resultado.getMethodParameter().getParameterName(),
                        erro.getDefaultMessage()))));
        return handleExceptionInternal(ex, problemaDeValidacao(erros), headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Optional<String> campo = causa(ex, JacksonException.class).map(GlobalExceptionHandler::caminho)
                .filter(c -> !c.isBlank());
        String detalhe = causa(ex, ArmazemException.class)
                .map(erro -> campo.map(c -> "Campo '" + c + "': ").orElse("") + erro.getMessage())
                .or(() -> campo.map(c -> "Valor inválido para o campo '" + c + "'."))
                .orElse("Corpo da requisição ausente ou com JSON malformado.");
        return handleExceptionInternal(ex,
                problema(HttpStatus.BAD_REQUEST, "Requisição inválida", CodigoErro.REQUISICAO_INVALIDA, detalhe),
                headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String detalhe = causa(ex, ArmazemException.class).map(Throwable::getMessage)
                .orElseGet(() -> "Valor '%s' inválido para o parâmetro '%s'.".formatted(ex.getValue(),
                        ex.getPropertyName()));
        return handleExceptionInternal(ex,
                problema(HttpStatus.BAD_REQUEST, "Requisição inválida", CodigoErro.PARAMETRO_INVALIDO, detalhe),
                headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        String detalhe = "Parâmetro obrigatório '%s' não informado.".formatted(ex.getParameterName());
        return handleExceptionInternal(ex,
                problema(HttpStatus.BAD_REQUEST, "Requisição inválida", CodigoErro.PARAMETRO_INVALIDO, detalhe),
                headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Garante {@code codigo} e {@code timestamp} também nas respostas montadas pelo próprio Spring
     * (405, 406, 415, 404 de rota...). Os textos dessas respostas estão traduzidos em {@code messages.properties}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> resposta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (resposta != null && resposta.getBody() instanceof ProblemDetail problema) {
            if (problema.getProperties() == null || !problema.getProperties().containsKey("codigo")) {
                problema.setProperty("codigo", codigoPorStatus(resposta.getStatusCode()).name());
            }
            if (problema.getProperties() == null || !problema.getProperties().containsKey("timestamp")) {
                problema.setProperty("timestamp", OffsetDateTime.now(clock));
            }
        }
        return resposta;
    }

    private static CodigoErro codigoPorStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> CodigoErro.RECURSO_NAO_ENCONTRADO;
            case 405 -> CodigoErro.METODO_NAO_SUPORTADO;
            case 406 -> CodigoErro.FORMATO_NAO_ACEITO;
            case 415 -> CodigoErro.TIPO_DE_CONTEUDO_NAO_SUPORTADO;
            default -> status.is5xxServerError() ? CodigoErro.ERRO_INTERNO : CodigoErro.REQUISICAO_INVALIDA;
        };
    }

    // ------------------------------------------------------------------ utilitários

    private ProblemDetail problemaDeValidacao(List<ErroCampo> erros) {
        erros.sort(Comparator.comparing(ErroCampo::campo).thenComparing(ErroCampo::mensagem));
        String resumo = erros.stream().map(e -> e.campo() + ": " + e.mensagem()).collect(Collectors.joining("; "));
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Dados inválidos", CodigoErro.VALIDACAO,
                "A requisição possui dados inválidos: " + resumo + ".");
        problema.setProperty("erros", erros);
        return problema;
    }

    private ProblemDetail problema(HttpStatus status, String titulo, CodigoErro codigo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("timestamp", OffsetDateTime.now(clock));
        return problema;
    }

    private static ResponseEntity<ProblemDetail> resposta(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus()).body(problema);
    }

    private static <T extends Throwable> Optional<T> causa(Throwable erro, Class<T> tipo) {
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            if (tipo.isInstance(atual)) {
                return Optional.of(tipo.cast(atual));
            }
            if (atual.getCause() == atual) {
                break;
            }
        }
        return Optional.empty();
    }

    private static String caminho(JacksonException erro) {
        return erro.getPath().stream()
                .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }
}
