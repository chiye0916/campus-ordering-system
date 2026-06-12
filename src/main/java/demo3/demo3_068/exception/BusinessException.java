package demo3.demo3_068.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        this(409, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
