package org.weinian.model;

import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

/**
 * 方法的请求参数分类
 *
 * @author ZhangYinGang
 */
public class Request {


    /**
     * 请求体的参数
     */
    private VariableElement body;


    /**
     * url中的请求参数
     */
    private List<VariableElement> params;


    /**
     * 标记 @PathVariable 的参数, key是注解中的value. 用来把uri中的参数补全
     */
    private List<VariableElement> variables;


    /**
     * 标记 @RequestHeader 的参数
     */
    private List<VariableElement> headers;


    public Boolean checkBodyIsNull() {

        return this.body == null;
    }

    public void setBody(VariableElement body) {
        if (checkBodyIsNull()) {
            this.body = body;
            return;
        }
        throw new RuntimeException("一个方法不能同时拥有两个@RequestBody注解参数为" + body.getSimpleName());
    }

    public void addParam(VariableElement param) {
        if (params == null) {
            this.params = new ArrayList<>();
        }
        params.add(param);
    }


    public void addVariable(VariableElement variable) {
        if (this.variables == null) {
            this.variables = new ArrayList<>();
        }
        variables.add(variable);
    }

    public void addHeader(VariableElement header) {
        if (this.headers == null) {
            this.headers = new ArrayList<>();
        }
        headers.add(header);
    }

    public VariableElement getBody() {
        return body;
    }

    public List<VariableElement> getParams() {
        return params;
    }

    public void setParams(List<VariableElement> params) {
        this.params = params;
    }

    public List<VariableElement> getVariables() {
        return variables;
    }

    public void setVariables(List<VariableElement> variables) {
        this.variables = variables;
    }

    public List<VariableElement> getHeaders() {
        return headers;
    }

    public void setHeaders(List<VariableElement> headers) {
        this.headers = headers;
    }

    @Override
    public String toString() {
        return "Request{" +
                "body=" + body +
                ", params=" + params +
                ", variables=" + variables +
                ", headers=" + headers +
                '}';
    }
}
