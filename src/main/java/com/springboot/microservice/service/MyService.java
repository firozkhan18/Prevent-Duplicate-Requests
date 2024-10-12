package com.springboot.microservice.service;

import org.springframework.stereotype.Service;

@Service
public class MyService {

    public String performAction() {
        System.out.println("Performing action in MyService.");
        return "Action Completed";
    }
}
