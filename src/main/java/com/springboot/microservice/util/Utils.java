package com.springboot.microservice.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.MessageDigest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;

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