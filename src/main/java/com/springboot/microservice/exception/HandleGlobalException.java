package com.springboot.microservice.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.springboot.microservice.enums.ErrorCode;
import com.springboot.microservice.util.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class HandleGlobalException extends ResponseEntityExceptionHandler {

//    @ExceptionHandler(DuplicationException.class)
//    private ResponseEntity<?> handleError(Exception ex) {
//
//        //TODO: you should custom more here
//
//        Map<String, String> body = new HashMap<>();
//        body.put("code", ((DuplicationException) ex).getCode());
//        body.put("message", ex.getMessage());
//        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
//    }
    
    @ExceptionHandler(DuplicationException.class)
    private ResponseEntity<ErrorResponse> handleError(DuplicationException ex) {
        log.error("Duplication error occurred: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse();
        response.setCode(ex.getCode());
        response.setMessage(ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    private ResponseEntity<?> handleResourceNotFoundException(ResourceNotFoundException ex) {
        Map<String, String> body = new HashMap<>();
        body.put("code", ErrorCode.ERROR_NOT_FOUND.getCode());
        body.put("message", ErrorCode.ERROR_NOT_FOUND.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

}
