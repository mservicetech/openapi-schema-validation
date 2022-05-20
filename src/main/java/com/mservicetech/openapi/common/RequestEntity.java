package com.mservicetech.openapi.common;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public class RequestEntity {

    Map<String, ?> pathParameters;
    Map<String, ?> queryParameters;
    Map<String, ?> headerParameters;
    Map<String, ?> cookieParameters;
    String requestBody;
    String contentType;

    public Map<String, ?> getPathParameters() {
        return pathParameters;
    }

    public void setPathParameters(Map<String, ?> pathParameters) {
        this.pathParameters = pathParameters;
    }


    public Map<String, ?> getQueryParameters() {
        return queryParameters;
    }

    public void setQueryParameters(Map<String, ?> queryParameters) {
        this.queryParameters = queryParameters;
    }

    public Map<String, ?> getHeaderParameters() {
        return headerParameters;
    }

    public void setHeaderParameters(Map<String, ?> headerParameters) {
        this.headerParameters = headerParameters;
    }

    public Map<String, ?> getCookieParameters() {
        return cookieParameters;
    }

    public void setCookieParameters(Map<String, ?> cookieParameters) {
        this.cookieParameters = cookieParameters;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public void setRequestBody(JsonNode requestBody) {
        this.requestBody = requestBody.toPrettyString();
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
