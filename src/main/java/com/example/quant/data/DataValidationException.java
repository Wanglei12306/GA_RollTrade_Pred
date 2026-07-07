package com.example.quant.data;

/**
 * 数据校验异常，承载面向用户的明确提示（对应用例文档 UC-14）。
 */
public class DataValidationException extends RuntimeException {

    public DataValidationException(String message) {
        super(message);
    }

    public DataValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
