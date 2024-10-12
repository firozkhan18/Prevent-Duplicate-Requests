package com.springboot.microservice;

import java.util.UUID;

public class TransactionId {

	public static void main(String[] args) {
		String transactionId = UUID.randomUUID().toString();
		System.out.println("transactionId: "+transactionId);


	}

}
