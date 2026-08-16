package com.iotmining.services.dms.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Configuration
public class FeignClientInterceptor implements RequestInterceptor {

    @Value("${internal.api.key:default-internal-key}")
    private String internalApiKey;

    @Override
    public void apply(RequestTemplate requestTemplate) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null) {
                // 1. Forward the JWT Token
                requestTemplate.header("Authorization", authHeader);

                // 2. Explicitly forward the Tenant ID to help the receiving service
                String tenantId = request.getHeader("X-Tenant-ID");
                if (tenantId != null) {
                    requestTemplate.header("X-Tenant-ID", tenantId);
                }
                return; // Successfully attached user context
            }
        }

        // 3. FALLBACK: Background thread or lost context.
        // Use the internal key so microservices can still talk.
        log.warn("No active user HTTP context found. Using Internal API Key for Feign client.");
        requestTemplate.header("X-Internal-API-Key", internalApiKey);
    }
}


//package com.iotmining.services.dms.configuration;
//
//import feign.RequestInterceptor;
//import feign.RequestTemplate;
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.context.request.RequestContextHolder;
//import org.springframework.web.context.request.ServletRequestAttributes;
//
//@Slf4j
//@Configuration
//public class FeignClientInterceptor implements RequestInterceptor {
//
//    @Override
//    public void apply(RequestTemplate requestTemplate) {
//        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
//        if (attributes != null) {
//            HttpServletRequest request = attributes.getRequest();
//            String authHeader = request.getHeader("Authorization");
//            if (authHeader != null) {
//                // Automatically attach the JWT token to outgoing Feign requests
//                requestTemplate.header("Authorization", authHeader);
//            }
//        } else {
//            log.debug("No active HTTP context; cannot attach Authorization header to Feign client.");
//        }
//    }
//}