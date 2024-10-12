package com.springboot.microservice.service;

import com.springboot.microservice.dto.ProductDto;

public interface IProductService {

    ProductDto createProduct(ProductDto dto);

}
