package org.weinian.model;

import feign.RequestInterceptor;
import org.springframework.web.client.RestTemplate;

/**
 * @author ZhangYinGang
 */

public abstract class BasicClint {

    private final RestTemplate restTemplate;

    private final RequestInterceptor requestInterceptor;
    private final String url;


    public BasicClint(RestTemplate restTemplate, String url, RequestInterceptor requestInterceptor) {
        this.restTemplate = restTemplate;
        this.url = url;
        this.requestInterceptor = requestInterceptor;

    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public String getUrl() {
        return url;
    }

    public RequestInterceptor getRequestInterceptor() {
        return requestInterceptor;
    }
}
