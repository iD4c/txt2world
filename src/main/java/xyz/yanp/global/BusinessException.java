package xyz.yanp.global;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = -3286433140006922463L;

    private Integer code;

    private String message;

    private transient Map<String, Object> params;

    public BusinessException() {
    }

    public BusinessException(String message) {
        super(message);
        this.code = 500;
        this.message = message;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(int code, String message, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.message = message;
        this.params = params;
    }
}
