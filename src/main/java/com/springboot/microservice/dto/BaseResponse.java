package com.springboot.microservice.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class BaseResponse<T> implements Serializable {

    public static final String OK_CODE = "200";
    public static final String OK_MESSAGE = "Successfully";
    private String code;
    private String message;
    private T data;

    public static <T> BaseResponse<T> ofSucceeded(T data) {
        BaseResponse<T> response = new BaseResponse<>();
        response.code = OK_CODE;
        response.message = OK_MESSAGE;
        response.data = data;
        return response;
    }
}
