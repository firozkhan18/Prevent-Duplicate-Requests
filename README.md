# REST API: How to Prevent Duplicate Requests Effectively

Preventing duplicate requests is crucial when users manipulate an API feed or any data source. Even if a user only intends to perform an action once, various reasons—intentional or unintentional—can lead to duplicate requests and cause data errors. 

## Overview

The approach involves the following steps:

1. Extract data fields from the Request Body to create a Redis key. The choice of fields depends on business needs and system architecture.
2. Build the key in an optional format and hash it using MD5 (using Fast MD5 for better performance is also an option).
3. Check if the Redis key exists on every API call. If it does, return a duplicate data error. If not, continue processing the logic.
4. Set an expiration time for the Redis key. In this Microservice we are using 40-second expiration.

## Project Structure

We will use:
- Spring Boot 3.3.4
- Java 17
- Spring AOP

### Implementation

Here is the detailed code implementation of each part

#### 1. PreventDuplicateValidator

```java
package com.springboot.microservice.component;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicateValidator {

    String[] includeFieldKeys() default {};

    String[] optionalValues() default {};

    long expireTime() default 10_000L;
}
```

`PreventDuplicateValidator` We declare this as an annotation with three data fields:
- **includeFieldKeys**: List of fields needed to generate the key based on the Request Body.
- **optionalValues**: Values that can be added to the key for flexibility.
- **expireTime**: Key expiration time value, default is 10 seconds.

### Purpose of the `PreventDuplicateValidator` Annotation

1. **Prevent Duplicate Requests**: This annotation is designed to be used on methods (usually in a Spring Boot application) to prevent the processing of duplicate requests. It allows you to specify which fields in the request body should be used to determine whether a request is a duplicate.

2. **Configuration Parameters**: The annotation includes several attributes that allow for customization of its behavior:
   - **`includeFieldKeys()`**: An array of field names that will be used to generate a unique key for each request. If two requests have the same values for these fields, they are considered duplicates.
   - **`optionalValues()`**: An array of additional values that can be included in the key. This allows for more granular control over what constitutes a duplicate request.
   - **`expireTime()`**: This specifies how long (in milliseconds) the key representing the request should remain in the cache (e.g., Redis) before it expires. The default value is set to 10 seconds.

### Annotations Explained

- **`@Target({ElementType.METHOD})`**: This indicates that the annotation can only be applied to methods. It specifies the scope of the annotation.

- **`@Retention(RetentionPolicy.RUNTIME)`**: This specifies that the annotation should be retained at runtime, making it available for reflection. This is essential for frameworks like Spring, which process annotations during runtime.

- **`@Documented`**: This indicates that whenever the annotation is used, it should be included in the Javadoc documentation. This is helpful for developers to understand what the annotation does when they view the documentation.

#### 2. PreventDuplicateValidatorAspect

This is an advice implementing logic for the `PreventDuplicateValidator` annotation. We use Around Advice for flexibility.

The logic implementation is as follows:

1. Extract the request body from the API.
2. Parse request body into `Map<K, V>` format.
3. Build raw keys from defined data fields.
4. Build MD5 key.
5. Check for duplicate requests by key.
6. If the key exists in Redis, throw an exception.
7. If not, insert the key into Redis with the expiration time and continue the main logic using `pjp.proceed()`.

```java
expireTime: is the key expiration time value, the default is 10 seconds.

package com.springboot.microservice.component;
import com.springboot.microservice.enums.ErrorCode;
import com.springboot.microservice.exception.DuplicationException;
import com.springboot.microservice.exception.HandleGlobalException;
import com.springboot.microservice.utils.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PreventDuplicateValidatorAspect {

    private final RedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Around(value = "@annotation(preventDuplicateValidator)", argNames = "pjp, preventDuplicateValidator")
    public Object aroundAdvice(ProceedingJoinPoint pjp, PreventDuplicateValidator preventDuplicateValidator)
        throws Throwable {

        var includeKeys = preventDuplicateValidator.includeFieldKeys();
        var optionalValues = preventDuplicateValidator.optionalValues();
        var expiredTime = preventDuplicateValidator.expireTime();

        if (includeKeys == null || includeKeys.length == 0) {
            log.warn("[PreventDuplicateRequestAspect] ignore because includeKeys not found in annotation");
            return pjp.proceed();
        }

        //extract request body in request body
        var requestBody = Utils.extractRequestBody(pjp);
        if (requestBody == null) {
            log.warn(
                "[PreventDuplicateRequestAspect] ignore because request body object find not found in method arguments");
            return pjp.proceed();
        }

        //parse request body to map<String, Object>
        var requestBodyMap = convertJsonToMap(requestBody);

        //build key redis from: includeKeys, optionalValues, requestBodyMap
        var keyRedis = buildKeyRedisByIncludeKeys(includeKeys, optionalValues, requestBodyMap);

        //hash keyRedis to keyRedisMD5: this is Optional, should be using Fast MD5 hash to replace
        var keyRedisMD5 = Utils.hashMD5(keyRedis);

        log.info(String.format("[PreventDuplicateRequestAspect] rawKey: [%s] and generated keyRedisMD5: [%s]", keyRedis,
            keyRedisMD5));

        //handle logic check duplicate request by key in Redis
        deduplicateRequestByRedisKey(keyRedisMD5, expiredTime);

        return pjp.proceed();
    }

    private String buildKeyRedisByIncludeKeys(String[] includeKeys, String[] optionalValues, Map<String, Object> requestBodyMap) {

        var keyWithIncludeKey = Arrays.stream(includeKeys)
            .map(requestBodyMap::get)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.joining(":"));

        if (optionalValues.length > 0) {
            return keyWithIncludeKey + ":" + String.join(":", optionalValues);
        }
        return keyWithIncludeKey;
    }


    public void deduplicateRequestByRedisKey(String key, long expiredTime) {
        var firstSet = (Boolean) redisTemplate.execute((RedisCallback<Boolean>) connection ->
            connection.set(key.getBytes(), key.getBytes(), Expiration.milliseconds(expiredTime),
                RedisStringCommands.SetOption.SET_IF_ABSENT));

        if (firstSet != null && firstSet) {
            log.info(String.format("[PreventDuplicateRequestAspect] key: %s has set successfully !!!", key));
            return;
        }
        log.warn(String.format("[PreventDuplicateRequestAspect] key: %s has already existed !!!", key));
        throw new DuplicationException(ErrorCode.ERROR_DUPLICATE.getCode(), ErrorCode.ERROR_DUPLICATE.getMessage());
    }

    public Map<String, Object> convertJsonToMap(Object jsonObject) {
        if (jsonObject == null) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.convertValue(jsonObject, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}

```

#### 3. Bean Configuration

Add bean configuration for `ObjectMapper` and Redis connection.

```java
package com.springboot.microservice.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class BeanConfig {

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

    @Bean(name = "objectMapper")
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        var config = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @Primary
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        var template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }
}
```

#### 4. BaseResponse

This is the response class that returns results via API, containing fields such as code, message, and data, request_id, etc.

```java
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
```

#### 5. HandleGlobalException

This class handles `DuplicationException`, which is triggered by `PreventDuplicateValidatorAspect`.

```java
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
package com.springboot.microservice.enums;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode {

    ERROR_DUPLICATE("CF_275", "Duplicated data, please try again later");

    private final String code;
    private final String message;
}
package com.springboot.microservice.exception;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class DuplicationException extends RuntimeException {

    private String code;
    private String message;
    private HttpStatus httpStatus;

    public DuplicationException(String code, String message) {
        this.code = code;
        this.message = message;
        httpStatus = HttpStatus.BAD_REQUEST;
    }

}
package com.springboot.microservice.exception;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class HandleGlobalException extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DuplicationException.class)
    private ResponseEntity<?> handleError(Exception ex) {

        //TODO: you should custom more here

        Map<String, String> body = new HashMap<>();
        body.put("code", ((DuplicationException) ex).getCode());
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }
}

```

#### 6. Utility Functions

This class includes logic functions to extract the request body and the MD5 hash function.

```java
package com.springboot.microservice.service;
import com.springboot.microservice.dto.ProductDto;

public interface IProductService {

    ProductDto createProduct(ProductDto dto);

}
package com.springboot.microservice.service;
import com.springboot.microservice.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

```
The class Utils includes logic functions to extract the request body from ProceedingJoinPoint and the MD5 hash function

```java
package com.springboot.microservice.utils;
import jakarta.xml.bind.DatatypeConverter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
public class Utils {

    private Utils() {
    }

    public static Object extractRequestBody(ProceedingJoinPoint pjp) {
        try {
            for (int i = 0; i < pjp.getArgs().length; i++) {
                Object arg = pjp.getArgs()[i];
                if (arg != null && isAnnotatedWithRequestBody(pjp, i)) {
                    return arg;
                }
            }
        } catch (Exception ex) {
            log.error("", ex);
        }
        return null;
    }

    private static boolean isAnnotatedWithRequestBody(ProceedingJoinPoint pjp, int paramIndex) {
        var method = getMethod(pjp);
        var parameterAnnotations = method.getParameterAnnotations();
        for (Annotation annotation : parameterAnnotations[paramIndex]) {
            if (RequestBody.class.isAssignableFrom(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private static Method getMethod(ProceedingJoinPoint pjp) {
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        return methodSignature.getMethod();
    }

    public static String hashMD5(String source) {
        String res = null;
        try {
            var messageDigest = MessageDigest.getInstance("MD5");
            var mdBytes = messageDigest.digest(source.getBytes());
            res = DatatypeConverter.printHexBinary(mdBytes);
        } catch (Exception e) {
            log.error("", e);
        }
        return res;
    }
}
```

### Main Controller

In the main controller, we use the `PreventDuplicateValidator` annotation with the following parameters:
- **includeFieldKeys**: Using `productId` and `transactionId` from the request body to generate the key.
- **optionalValues**: Declaring an optional value, "PINCODE".
- **expireTime**: Setting the Redis cache lifetime to 40 seconds.

The class Utils includes logic functions to extract the request body from ProceedingJoinPoint and the MD5 hash function

```yaml
redis:
  host: localhost
  port: 6379
spring:
  application:
    name: product-service
server:
  port: 8888
```
configure application-local.yml

```yaml
version: "3.2"
services:
  redis:
    container_name: demo-service-redis
    image: redis:6.2.5
    ports:
      - '6379:6379'
```
In this main controller section, declare to use annotation with the parameter values above: PreventDuplicateValidator

**includeFieldKeys**: markup will take two fields productIdand transactionId in the request body as input to generate key
**optionalValues**: option value, I declare here PINCODE
**expireTime**: data lifetime in Redis cache, I set it to 40 seconds.

```java
package com.springboot.microservice.controller;
import com.springboot.microservice.component.PreventDuplicateValidator;
import com.springboot.microservice.dto.BaseResponse;
import com.springboot.microservice.dto.ProductDto;
import com.springboot.microservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreventDuplicateValidator(
        includeFieldKeys = {"productId", "transactionId"},
        optionalValues = {"PINCODE"},
        expireTime = 40_000L)
    public BaseResponse<?> createProduct(@RequestBody ProductDto request) {
        return BaseResponse.ofSucceeded(productService.createProduct(request));
    }

}
```
### Running the Project

1. For MacOS and Windows, ensure Docker Desktop is running and execute:
   ```bash
   docker-compose up -d
   ```
   
2. For Ubuntu, install Docker first, then run the above command.

3. Use Postman to test the API with the following request body:

   ```json
   {
       "productId": "test-001",
       "productName": "PINCODE",
       "productDescription": "Threat identify buy war manage little friend south really chair",
       "transactionId": "cd076846-ff28-4307-8524-3eb6e1809838",
       "requestTime": 1696069378367,
       "requestId": "{{$randomUUID}}"
   }
   ```

### Testing the API

**First Call:**
- Validate success, initialize the key in Redis.
- Console log: "MD5 key: 6C518A2B1666005572EDFC8240A130F2 does not exist in Redis".

**Second Call:**
- Console log: Check if the key exists. If it does, return error `CF_275` to the client.

### Conclusion

In summary, the implementation of duplicate prevention using Redis and Spring AOP involves careful selection of fields for key generation, setting appropriate expiration times, and considering the use of MD5 hashing. 

After implementing the complete logic, you only need to declare the annotation on the controllers that require it, ensuring flexibility with minimal modification needed.

The warning you received indicates that the `version` field in your `docker-compose.yml` file is considered obsolete in the latest versions of Docker Compose. This is a common message with newer versions, as they aim for more flexibility in configuration.

### Updated `docker-compose.yml`

You can safely remove the `version` line if you're using a recent version of Docker Compose. Here’s the updated configuration:

```yaml
services:
  redis:
    container_name: demo-service-redis
    image: redis:6.2.5
    ports:
      - '6379:6379'
```

### Running the Updated Configuration

1. **Stop the Current Containers**: Since you already have the Redis container running, stop it with:

   ```bash
   docker-compose down
   ```

2. **Update the File**: Edit your `docker-compose.yml` file to remove the `version` line as shown above.

3. **Start the Service Again**: Run the following command to start Redis again:

   ```bash
   docker-compose up -d
   ```

### Verify the Service

You can check if Redis is running by executing:

```bash
docker ps
```

You should see the `demo-service-redis` container running without warnings.

To verify that your Redis container is running correctly, you can follow these steps:

### 1. Check Running Containers

Run the following command to list all running Docker containers:

```bash
docker ps
```

You should see a list of containers, including your `demo-service-redis`. Look for an entry like this:

```
CONTAINER ID   IMAGE          COMMAND                  CREATED        STATUS        PORTS                   NAMES
<container_id> redis:6.2.5    "docker-entrypoint.s…"   <time> ago    Up <time>    0.0.0.0:6379->6379/tcp demo-service-redis
```

### 2. Connect to Redis

You can use the Redis CLI (command-line interface) to connect to your Redis instance and verify it's working:

1. **Access the Redis CLI**:

   If you have Redis CLI installed locally, you can run:

   ```bash
   redis-cli -h localhost -p 6379
   ```

   If you don't have Redis CLI installed locally, you can run it from the Docker container:

   ```bash
   docker exec -it demo-service-redis redis-cli
   ```

2. **Test Redis Commands**:

   Once you're in the Redis CLI, you can run a few commands to check its functionality:

   - Set a key:

     ```bash
     set test_key "Hello, Redis!"
     ```

   - Get the key:

     ```bash
     get test_key
     ```

   You should see the output:

   ```
   "Hello, Redis!"
   ```

### 3. Check Redis Logs

To check the logs for the Redis container, you can run:

```bash
docker logs demo-service-redis
```

This will show you any startup messages or errors that Redis may have logged.

### 4. Use a GUI Tool

You can also use a GUI tool like **RedisInsight** or **Redis Desktop Manager** to connect to your Redis instance and perform operations visually.

If everything checks out, your Redis service is running and ready for use! If you encounter any issues, let me know!

Your Spring Boot application setup for preventing duplicate requests using Redis looks good overall. However, let's ensure everything is clear and functioning correctly.

### Key Components Explained

1. **Aspect for Duplicate Validation**:
   - The `PreventDuplicateValidatorAspect` uses Aspect-Oriented Programming (AOP) to intercept method calls annotated with `@PreventDuplicateValidator`.
   - It builds a unique Redis key based on specific fields in the request and checks if a similar request has already been processed by attempting to set the key in Redis with an expiration time.

2. **Redis Configuration**:
   - The `BeanConfig` class configures the Redis connection and sets up an `ObjectMapper` bean for JSON processing.

### Common Issues and Solutions

1. **Redis Security Warning**:
   - The warning about possible security attacks may occur if invalid commands are sent to Redis. Ensure that the Redis commands used in your application are valid.
   - Make sure the Redis client (like `RedisTemplate`) is correctly set up and not sending HTTP commands to the Redis server.

2. **Configuration Verification**:
   - Check that the Redis service is up and running on the specified host and port.
   - Use the correct method to extract and process the request body in your aspect.

### Testing the Setup

1. **Verify Redis Connectivity**:
   - Before running your application, check that you can connect to Redis using a Redis client or CLI:
     ```bash
     redis-cli -h localhost -p 6379 ping
     ```
   - It should respond with `PONG`.

2. **Testing Duplicate Prevention**:
   - You can use a tool like Postman or cURL to send POST requests to your endpoint that uses the `@PreventDuplicateValidator`.
   - For example:
     ```bash
     curl -X POST http://localhost:8888/your-endpoint -H "Content-Type: application/json" -d '{
         "productId": "test-001",
         "productName": "PINCODE",
         "productDescription": "Sample Description",
         "transactionId": "your-transaction-id",
         "requestTime": 1696069378367,
         "requestId": "{{$randomUUID}}"
     }'
     ```

3. **Monitor Logs**:
   - Pay attention to the application logs for any errors or warnings.
   - Check Redis logs as well for any suspicious activities or errors.

### Code Improvement Suggestions

- **Type Safety in RedisTemplate**:
  Change your `RedisTemplate` definition to be type-safe:
  ```java
  @Bean
  @Primary
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
      RedisTemplate<String, Object> template = new RedisTemplate<>();
      template.setConnectionFactory(redisConnectionFactory);
      return template;
  }
  ```

- **Error Handling**:
  Ensure that you have proper error handling in your AOP aspect to capture and log exceptions that might occur during Redis operations.

- **Using in an API Request**
If you want to send this JSON as part of an API request, you can use tools like Postman or cURL. Here’s an example using cURL:

```
curl -X POST http://localhost:8888/api/products \
-H "Content-Type: application/json" \
-d '{
    "productId": "test-001",
    "productName": "PINCODE",
    "productDescription": "Threat identify buy war manage little friend south really chair",
    "transactionId": "cd076846-ff28-4307-8524-3eb6e1809838",
    "requestTime": 1696069378367,
    "requestId": "{{$randomUUID}}"
}'
```
### Conclusion

Once you ensure that the Redis setup is correct and the application code handles requests properly, the duplicate request prevention mechanism should work as intended. If issues persist, providing specific error messages or behavior will help troubleshoot further!
