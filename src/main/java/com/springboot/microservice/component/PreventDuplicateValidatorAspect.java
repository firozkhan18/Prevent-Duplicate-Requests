package com.springboot.microservice.component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.microservice.enums.ErrorCode;
import com.springboot.microservice.exception.DuplicationException;
import com.springboot.microservice.util.Utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PreventDuplicateValidatorAspect {

    //private final RedisTemplate redisTemplate;
    private final RedisTemplate<Object, Object> redisTemplate; // Specify types

    private final ObjectMapper objectMapper;

    @Around(value = "@annotation(preventDuplicateValidator)", argNames = "pjp, preventDuplicateValidator")
    public Object aroundAdvice(ProceedingJoinPoint pjp, PreventDuplicateValidator preventDuplicateValidator)
        throws Throwable {

//        var includeKeys = preventDuplicateValidator.includeFieldKeys();
//        var optionalValues = preventDuplicateValidator.optionalValues();
//        var expiredTime = preventDuplicateValidator.expireTime();
        
        String[] includeKeys = preventDuplicateValidator.includeFieldKeys();
        String[] optionalValues = preventDuplicateValidator.optionalValues();
        long expiredTime = preventDuplicateValidator.expireTime();

        if (includeKeys == null || includeKeys.length == 0) {
            log.warn("[PreventDuplicateRequestAspect] ignore because includeKeys not found in annotation");
            return pjp.proceed();
        }

        // extract request body in request body
        // var requestBody = Utils.extractRequestBody(pjp);
        // Extract request body from request method arguments
        Object requestBody = Utils.extractRequestBody(pjp);
        if (requestBody == null) {
            log.warn(
                "[PreventDuplicateRequestAspect] ignore because request body object find not found in method arguments");
            return pjp.proceed();
        }

        //parse request body to map<String, Object>
        //var requestBodyMap = convertJsonToMap(requestBody);
        // Parse request body to Map<String, Object>
        Map<String, Object> requestBodyMap = convertJsonToMap(requestBody);
        //build key redis from: includeKeys, optionalValues, requestBodyMap
        //var keyRedis = buildKeyRedisByIncludeKeys(includeKeys, optionalValues, requestBodyMap);
        // Build Redis key from includeKeys, optionalValues, requestBodyMap
        String keyRedis = buildKeyRedisByIncludeKeys(includeKeys, optionalValues, requestBodyMap);
        //hash keyRedis to keyRedisMD5: this is Optional, should be using Fast MD5 hash to replace
        //var keyRedisMD5 = Utils.hashMD5(keyRedis);
        // Hash keyRedis to keyRedisMD5
        String keyRedisMD5 = Utils.hashMD5(keyRedis);
        
        log.info(String.format("[PreventDuplicateRequestAspect] rawKey: [%s] and generated keyRedisMD5: [%s]", keyRedis,
            keyRedisMD5));

        //handle logic check duplicate request by key in Redis
        deduplicateRequestByRedisKey(keyRedisMD5, expiredTime);

        return pjp.proceed();
    }

    private String buildKeyRedisByIncludeKeys(String[] includeKeys, String[] optionalValues, Map<String, Object> requestBodyMap) {

        //var keyWithIncludeKey = Arrays.stream(includeKeys)
        String keyWithIncludeKey = Arrays.stream(includeKeys)
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
        //var firstSet = (Boolean) redisTemplate.execute((RedisCallback<Boolean>) connection ->
        Boolean firstSet = redisTemplate.execute((RedisCallback<Boolean>) connection ->
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
        	 log.error("Error converting JSON to Map: ", ignored);
            return Collections.emptyMap();
        }
    }

}