package org.weinian;


import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMapAdapter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.weinian.model.BasicClint;
import org.weinian.model.HttpMethodAndUri;
import org.weinian.model.Request;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;

/**
 * fei 接口生成工具
 *
 * @author ZhangYinGang
 */


@SupportedAnnotationTypes("org.springframework.cloud.openfeign.FeignClient")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class FeignClintProcessor extends AbstractProcessor {


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //annotations 这个就是上面的 SupportedAnnotationTypes 中的注解
        for (TypeElement annotation : annotations) {
            //获取所有添加 FeignClient 的类
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : annotatedElements) {
                this.generateImplementation(element);
            }
        }
        return true;
    }

    private void generateImplementation(Element interfaceElement) {

        String className = interfaceElement.getSimpleName().toString();
        String packageName = processingEnv.getElementUtils().getPackageOf(interfaceElement).toString();
        //添加构造方法
        MethodSpec superConstructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).addParameter(RestTemplate.class, "restTemplate").addParameter(String.class, "url").addParameter(RequestInterceptor.class, "requestInterceptor").addStatement("super(restTemplate, url,requestInterceptor)") // 调用父类的构造函数
                .build();
        // 写入到生成类中
        ClassName interfaceName = ClassName.get(packageName, className);
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(interfaceName.simpleName() + "Impl").addModifiers(Modifier.PUBLIC).superclass(BasicClint.class).addMethod(superConstructor).addSuperinterface(interfaceName);
        //获取当前类中的所有方法并实现方法
        for (Element enclosedElement : interfaceElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD) {
                ExecutableElement methodElement = (ExecutableElement) enclosedElement;

                //校验不支持的参数. @Headers
                HttpMethodAndUri httpMethodAndUri = getRequestTypeAndUriForMapping(methodElement);
                //方法名
                String methodName = methodElement.getSimpleName().toString();
                //返回类型
                String returnType = methodElement.getReturnType().toString();
                //方法参数
                List<ParameterSpec> annotationSpecs = generateParameters(methodElement);
                //获取请求参数和请求类型
                Request request = getRequestParameters(methodElement);
                //拼接方法体呢
                CodeBlock.Builder codeBlockBuilder = setRequestHeaderAndRequestBody(httpMethodAndUri, request);
                //设置参数
                setRequestParametersAndUrlParame(httpMethodAndUri, request, codeBlockBuilder);
                //设置执行方法
                setRequestHeaderObject(request, codeBlockBuilder);
                executionMethod(httpMethodAndUri, returnType, codeBlockBuilder);
                //拼接方法

                MethodSpec returnNull = MethodSpec.methodBuilder(methodName)
                        .addModifiers(Modifier.PUBLIC).
                        returns(ClassName.get(packageName, returnType))
                        .addParameters(annotationSpecs)
                        .addCode(codeBlockBuilder.build()).build();
                classBuilder.addMethod(returnNull);
            }
        }

        try {
            JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
            javaFile.writeTo(processingEnv.getFiler());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 设置执行方法和
     *
     * @param httpMethodAndUri url
     * @param returnType       返回参数
     * @param codeBlockBuilder 便曾轶对象
     */
    private static void executionMethod(HttpMethodAndUri httpMethodAndUri, String returnType, CodeBlock.Builder codeBlockBuilder) {
        //设置请求方法
        codeBlockBuilder.addStatement("$T parameterizedTypeReference  = new $T<$L>() {}", ParameterizedTypeReference.class, ParameterizedTypeReference.class, returnType);
        codeBlockBuilder.addStatement("$T<$L> responseEntity = this.getRestTemplate().exchange(url, $T.$L, httpEntity,parameterizedTypeReference)", ResponseEntity.class, returnType, HttpMethod.class, httpMethodAndUri.getHttpMethod());
        //获取请求体的值
        codeBlockBuilder.addStatement("return responseEntity.getBody()");
    }

    /**
     * 设置参数
     *
     * @param httpMethodAndUri 请求体url和请求方式
     * @param request          请求
     * @param codeBlockBuilder 编译对象
     */
    private void setRequestParametersAndUrlParame(HttpMethodAndUri httpMethodAndUri, Request request, CodeBlock.Builder codeBlockBuilder) {
        codeBlockBuilder.addStatement("$T<String, Object> uriVariablesMap = new $T<>()", Map.class, HashMap.class);
        //生成一个替换参数的map
        //如果参数部位空
        for (VariableElement variableElement : Optional.of(request).map(Request::getVariables).orElse(new ArrayList<>())) {
            PathVariable annotation = variableElement.getAnnotation(PathVariable.class);
            String fieldName = variableElement.getSimpleName().toString();
            String key = annotation.value().isEmpty() ? fieldName : annotation.value();
            codeBlockBuilder.addStatement("uriVariablesMap.put($L,$L)", key, fieldName);
        }

        codeBlockBuilder.addStatement("$T<String,String> paramMap = new $T()", LinkedMultiValueMap.class, LinkedMultiValueMap.class);
        for (VariableElement variableElement : Optional.of(request).map(Request::getParams).orElse(new ArrayList<>())) {
            RequestParam annotation = variableElement.getAnnotation(RequestParam.class);
            String fieldName = variableElement.getSimpleName().toString();
            String key = annotation.value().isEmpty() ? fieldName : annotation.value();
            codeBlockBuilder.addStatement("paramMap.set($S,String.valueOf($L))", key, fieldName);
        }

        codeBlockBuilder.addStatement("String url=  $T.fromHttpUrl(this.getUrl()+$S).uriVariables(uriVariablesMap).queryParams(paramMap).toUriString()", UriComponentsBuilder.class, httpMethodAndUri.getPath());
    }

    /**
     * 设置请求头和请求体
     *
     * @param httpMethodAndUri 请求类型
     * @param request          参数
     * @return 编译对象
     */
    private CodeBlock.Builder setRequestHeaderAndRequestBody(HttpMethodAndUri httpMethodAndUri, Request request) {
        CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();
        //添加第一行代码请求头
        codeBlockBuilder.addStatement("$T headers = new $T()", HttpHeaders.class, HttpHeaders.class);
        //如果是post添加请求头json
        if (HttpMethod.POST.equals(httpMethodAndUri.getHttpMethod())) {
            codeBlockBuilder.addStatement("headers.set(\"Content-Type\", \"application/json\")");
        }
        //设置请求头
        if (request.getHeaders() != null) {
            for (VariableElement header : request.getHeaders()) {
                RequestHeader requestHeader = header.getAnnotation(RequestHeader.class);
                Name simpleName = header.getSimpleName();
                //如果没有注解则为参数的值
                String headerName = requestHeader.value().isEmpty() ? simpleName.toString() : requestHeader.value();

                codeBlockBuilder.addStatement("String defaultValue = $S", requestHeader.defaultValue());
                //如果simpleName 为空返回默认值
                codeBlockBuilder.addStatement("$L = $L == null?defaultValue:$L", simpleName, simpleName, simpleName);
                codeBlockBuilder.addStatement("headers.set($S,$L )", headerName, simpleName);

            }
        }

        //通过拦截器扩展请求头

        codeBlockBuilder.addStatement("$T requestTemplate = new RequestTemplate()", RequestTemplate.class);
        codeBlockBuilder.addStatement("requestTemplate.headers(new $T(headers))", MultiValueMapAdapter.class);
        codeBlockBuilder.addStatement("$T requestInterceptor = this.getRequestInterceptor()", RequestInterceptor.class);
        codeBlockBuilder.addStatement(" requestInterceptor.apply(requestTemplate)");
        return codeBlockBuilder;
    }

    private static void setRequestHeaderObject(Request request, CodeBlock.Builder codeBlockBuilder) {
        //设置请求头和请求体
        VariableElement body = request.getBody();
        if (body != null) {
            codeBlockBuilder.addStatement("$T<$L> httpEntity = new HttpEntity<>($N, new $T(requestTemplate.headers()))", HttpEntity.class, body.asType().toString(), body.getSimpleName().toString(), MultiValueMapAdapter.class);
        }
        if (body == null) {
            codeBlockBuilder.addStatement("$T<$T> httpEntity = new HttpEntity<>(new $T(requestTemplate.headers()))", HttpEntity.class, String.class, MultiValueMapAdapter.class);
        }
    }

    private List<ParameterSpec> generateParameters(ExecutableElement methodElement) {
        List<ParameterSpec> parameters = new ArrayList<>();
        for (VariableElement parameter : methodElement.getParameters()) {
            TypeMirror type = parameter.asType();
            ParameterSpec parameterSpec = ParameterSpec.builder(TypeName.get(type), parameter.getSimpleName().toString()).build();
            parameters.add(parameterSpec);
        }
        return parameters;
    }


    /**
     * 获取请求方式和类型
     *
     * @param methodElement 方法类型
     * @return 请求方式
     */
    private Request getRequestParameters(ExecutableElement methodElement) {
        Request requestParameters = new Request();
        for (VariableElement parameter : methodElement.getParameters()) {
            //如果有body注解参数添加到body中
            RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
            if (requestBody != null) {

                requestParameters.setBody(parameter);
            }
            //如果有param注解添加到param中
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                requestParameters.addParam(parameter);
            }
            //如果有Variable注解添加到Variable中
            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            if (pathVariable != null) {
                requestParameters.addVariable(parameter);
            }
            //如果有requestHeader注解添加到requestHeader中
            RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
            if (requestHeader != null) {
                requestParameters.addHeader(parameter);
            }
        }
        return requestParameters;
    }


    /**
     * 根据文档返回这个接口的请求方式和请求的uri
     *
     * @param methodElement 方法文档
     * @return 请求方式和uri
     */
    private HttpMethodAndUri getRequestTypeAndUriForMapping(ExecutableElement methodElement) {

        GetMapping getMapping = methodElement.getAnnotation(GetMapping.class);
        if (getMapping != null) {
            String url = Arrays.stream(getMapping.value()).findFirst().orElseThrow(() -> new RuntimeException("注解中没有url参数"));
            return new HttpMethodAndUri(HttpMethod.GET, url);
        }

        PostMapping postMapping = methodElement.getAnnotation(PostMapping.class);
        if (postMapping != null) {
            String url = Arrays.stream(postMapping.value()).findFirst().orElseThrow(() -> new RuntimeException("注解中没有url参数"));
            return new HttpMethodAndUri(HttpMethod.POST, url);
        }

        RequestMapping requestMapping = methodElement.getAnnotation(RequestMapping.class);
        if (requestMapping != null) {
            String url = Arrays.stream(requestMapping.value()).findFirst().orElseThrow(() -> new RuntimeException("注解中没有url参数"));
            RequestMethod requestMethod = Arrays.stream(requestMapping.method()).findFirst().orElse(RequestMethod.GET);
            return new HttpMethodAndUri(HttpMethod.valueOf(requestMethod.name()), url);
        }

        PutMapping putMapping = methodElement.getAnnotation(PutMapping.class);
        if (putMapping != null) {
            String url = Arrays.stream(putMapping.value()).findFirst().orElseThrow(() -> new RuntimeException("注解中没有url参数"));
            return new HttpMethodAndUri(HttpMethod.PUT, url);

        }
        DeleteMapping deleteMapping = methodElement.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null) {
            String url = Arrays.stream(deleteMapping.value()).findFirst().orElseThrow(() -> new RuntimeException("注解中没有url参数"));
            return new HttpMethodAndUri(HttpMethod.DELETE, url);
        }
        PatchMapping patchMapping = methodElement.getAnnotation(PatchMapping.class);
        if (patchMapping != null) {
            String url = Arrays.stream(patchMapping.value()).findFirst().orElseThrow(() -> new RuntimeException("注解中没有url参数"));
            return new HttpMethodAndUri(HttpMethod.PATCH, url);
        }
        return new HttpMethodAndUri();
    }


}
