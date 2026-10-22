package ewm.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ErrorHandler {
    private static final String VALIDATION_ERROR = "Ошибка валидации";
    private static final String LOG_VALIDATION_ERROR = "Ошибка валидации: {}";
    private static final String LOG_INTERNAL_SERVER_ERROR = "Internal server error";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> String.format("Поле %s %s", fieldError.getField(), fieldError.getDefaultMessage()))
                .orElse(VALIDATION_ERROR);

        log.warn(LOG_VALIDATION_ERROR, message);
        return new ErrorResponse(message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleInternalError(Exception ex) {
        log.error(LOG_INTERNAL_SERVER_ERROR, ex);
        return new ErrorResponse(LOG_INTERNAL_SERVER_ERROR);
    }
}
