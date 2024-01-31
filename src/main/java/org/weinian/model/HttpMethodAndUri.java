package org.weinian.model;

import org.springframework.http.HttpMethod;

/**
 * 请求方式和uri
 *
 * @author ZhangYinGang
 */
public class HttpMethodAndUri {


    private HttpMethod httpMethod;


    private String path;


    public HttpMethodAndUri(HttpMethod httpMethod, String path) {
        this.httpMethod = httpMethod;
        this.path = path;
    }

    public HttpMethodAndUri() {


    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setUri(String path) {
        this.path = path;
    }
}
