package com.fund.stockProject.global.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
@Slf4j
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String remoteAddr = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        String fullUri = queryString != null ? uri + "?" + queryString : uri;
        
        log.info("HTTP Request - Method: {}, URI: {}, RemoteAddr: {}, UserAgent: {}", 
                method, fullUri, remoteAddr, userAgent != null ? truncateUserAgent(userAgent) : "N/A");

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, 
                          Object handler, ModelAndView modelAndView) {
        // 응답 상태는 afterCompletion에서 처리하는 것이 더 정확함
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();

            if (ex != null) {
                log.error("HTTP Request Error - Method: {}, URI: {}, Status: {}, Duration: {}ms", 
                        method, uri, status, duration, ex);
            } else if (status >= 400) {
                log.warn("HTTP Request Warning - Method: {}, URI: {}, Status: {}, Duration: {}ms", 
                        method, uri, status, duration);
            } else {
                log.info("HTTP Request Success - Method: {}, URI: {}, Status: {}, Duration: {}ms", 
                        method, uri, status, duration);
            }
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return "N/A";
        }
        // User-Agent가 너무 길면 잘라냄
        return userAgent.length() > 100 ? userAgent.substring(0, 100) + "..." : userAgent;
    }
}




