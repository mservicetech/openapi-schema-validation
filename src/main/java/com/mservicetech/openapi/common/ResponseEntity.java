package com.mservicetech.openapi.common;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public class ResponseEntity {

    Map<String, ?> headers;
    String content;
    String contentType;

    public Map<String, ?> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, ?> headers) {
        this.headers = headers;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setContent(JsonNode content) {
        this.content = content.toPrettyString();
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
