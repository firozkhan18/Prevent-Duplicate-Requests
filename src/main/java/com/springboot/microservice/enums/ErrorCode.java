package com.springboot.microservice.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode {

    ERROR_DUPLICATE("CF_275", "Duplicated data, please try again later"),
    ERROR_NOT_FOUND("CF_404", "Requested resource not found"),
    ERROR_INVALID_INPUT("CF_400", "Invalid input provided"),
    ERROR_SERVER("CF_500", "Internal server error, please try again later");
	
    private final String code;
    private final String message;
    
    public static String getMessageByCode(String code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode.getMessage();
            }
        }
        return "Unknown error code";
    }
}
