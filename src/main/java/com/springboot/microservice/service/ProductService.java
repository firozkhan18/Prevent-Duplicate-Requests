package com.springboot.microservice.service;

import org.springframework.stereotype.Component;

import com.springboot.microservice.dto.ProductDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductService implements IProductService {

    @Override
    public ProductDto createProduct(ProductDto dto) {
        //TODO: more logic here
        return null;
    }

}