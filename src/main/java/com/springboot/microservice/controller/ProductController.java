package com.springboot.microservice.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.microservice.component.PreventDuplicateValidator;
import com.springboot.microservice.dto.BaseResponse;
import com.springboot.microservice.dto.ProductDto;
import com.springboot.microservice.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreventDuplicateValidator(
        includeFieldKeys = {"productId", "transactionId"},
        optionalValues = {"CAFEINCODE"},
        expireTime = 40_000L)
    public BaseResponse<?> createProduct(@RequestBody ProductDto request) {
        return BaseResponse.ofSucceeded(productService.createProduct(request));
    }

}
