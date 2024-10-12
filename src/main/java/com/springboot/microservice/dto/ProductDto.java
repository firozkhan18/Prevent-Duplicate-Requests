package com.springboot.microservice.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class ProductDto {

    private String productId;
    private String productName;
    private String productDescription;
    private String transactionId;
    private Instant requestTime;
    private String requestId;

}