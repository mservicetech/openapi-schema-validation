package com.mservicetech.openapi.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mservicetech.openapi.common.RequestEntity;

import com.mservicetech.openapi.common.Status;
import org.junit.Assert;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenApiValidatorTest {

    static OpenApiValidator openApiValidator;

    @BeforeClass
    public static void setUp() {
        openApiValidator = new OpenApiValidator("openapi.yaml");
    }

    @Test
    public void testFileLoad() {

        Assert.assertNotNull(openApiValidator.openApiHelper.openApi3);
        Assert.assertNotNull(openApiValidator.openApiHelper.basePath);
    }

    @Test
    public void testURLPath() {
        Status status1 = openApiValidator.validateRequestPath("/pets", "get", null);
        Assert.assertNull(status1);
        Status status2 = openApiValidator.validateRequestPath("/pets/1111", "get", null);
        Assert.assertNull(status2);
        Status status3 = openApiValidator.validateRequestPath("/pets1/v1/1111", "get", null);
        Assert.assertNotNull(status3);
        Assert.assertEquals( status3.getCode(), "ERR10007");
    }

    @Test
    public void testRequestBody() {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("json/req1.json");
        String req1 = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        RequestEntity requestEntity = new RequestEntity();
        requestEntity.setRequestBody(req1);
        requestEntity.setContentType("application/json");
        Status status = openApiValidator.validateRequestPath("/pets", "post", requestEntity);
        Assert.assertNull(status);
    }

    @Test
    public void testRequestBody2() {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("json/req2.json");
        String req = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        RequestEntity requestEntity = new RequestEntity();
        requestEntity.setRequestBody(req);
        requestEntity.setContentType("application/json");
        Status status = openApiValidator.validateRequestPath("/pets", "post", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11004");
        //{"statusCode":400,"code":"ERR11004","message":"VALIDATOR_SCHEMA","description":"Schema Validation Error - requestBody.id: is missing but it is required","severity":"ERROR"}
    }

    @Test
    public void testRequestBody3() {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("json/req3.json");
        String req = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        RequestEntity requestEntity = new RequestEntity();
        requestEntity.setRequestBody(req);
        requestEntity.setContentType("application/json");
        Status status = openApiValidator.validateRequestPath("/pets", "post", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11004");
        //{"statusCode":400,"code":"ERR11004","message":"VALIDATOR_SCHEMA","description":"Schema Validation Error - requestBody.id: string found, integer expected","severity":"ERROR"}
    }

    @Test
    public void testRequestBody4() throws JsonProcessingException {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("json/req3.json");
        String req = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        RequestEntity requestEntity = new RequestEntity();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(req);
        requestEntity.setRequestBody(jsonNode);
        requestEntity.setContentType("application/json");
        Status status = openApiValidator.validateRequestPath("/pets", "post", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11004");
        //{"statusCode":400,"code":"ERR11004","message":"VALIDATOR_SCHEMA","description":"Schema Validation Error - requestBody.id: string found, integer expected","severity":"ERROR"}
    }

    @Test
    public void testRequestBodyEmpty() {
        String req = "";
        RequestEntity requestEntity = new RequestEntity();
        requestEntity.setRequestBody(req);
        requestEntity.setContentType("application/json");
        Status status = openApiValidator.validateRequestPath("/pets", "post", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11013");
        System.out.println(status.getDescription());
        //{"statusCode":400,"code":"ERR11013","message":"VALIDATOR_REQUEST_BODY_UNEXPECTED","No request body is expected for %s on path %s.":"ERROR"}
    }

    @Test
    public void testRequestBodyNull() {
        String req = null;
        RequestEntity requestEntity = new RequestEntity();
        requestEntity.setRequestBody(req);
        requestEntity.setContentType("application/json");
        Status status = openApiValidator.validateRequestPath("/pets", "post", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11014");
        //{"statusCode":400,"code":"ERR11004","message":"VALIDATOR_REQUEST_BODY_MISSING","description":"Method post on path /pets requires a request body. None found.","severity":"ERROR"}
    }


    @Test
    public void testRequestPath() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> pathMap = new HashMap<>();
        pathMap.put("petId", "1122");
        requestEntity.setPathParameters(pathMap);
        Status status = openApiValidator.validateRequestPath("/pets/{petId}", "get", requestEntity);
        Assert.assertNull(status);
    }

    @Test
    public void testRequestPath2() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> pathMap = new HashMap<>();
        pathMap.put("petId", 1122);
        requestEntity.setPathParameters(pathMap);
        Status status = openApiValidator.validateRequestPath("/pets/{petId}", "get", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11004");
        //{"statusCode":400,"code":"ERR11004","message":"VALIDATOR_SCHEMA","description":"Schema Validation Error - petId: integer found, string expected","severity":"ERROR"}
    }

    @Test
    public void testRequestPath3() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> pathMap = new HashMap<>();
        pathMap.put("petId2", "1122");
        requestEntity.setPathParameters(pathMap);
        Status status = openApiValidator.validateRequestPath("/pets/{petId}", "get", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11108");
        //{"statusCode":400,"code":"ERR11001","message":"VALIDATOR_REQUEST_PARAMETER_MISSING","description":"Parameter petId is required but is missing.","severity":"ERROR"}
    }

    @Test
    public void testRequestPath4() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> pathMap = new HashMap<>();
        pathMap.put("petId", "112245");
        requestEntity.setPathParameters(pathMap);
        Status status = openApiValidator.validateRequestPath("/pets/{petId}", "get", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11004");
        //{"statusCode":400,"code":"ERR11004","message":"VALIDATOR_SCHEMA","description":"Schema Validation Error - petId: must have a maximum value of 5","severity":"ERROR"}
    }

    @Test
    public void testRequestQueryMissRequired() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> queryMap = new HashMap<>();
        requestEntity.setQueryParameters(queryMap);
        Status status = openApiValidator.validateRequestPath("/pets", "get", requestEntity);
        Assert.assertEquals( status.getCode(), "ERR11000");
        //{"statusCode":400,"code":"ERR11001","message":"VALIDATOR_REQUEST_PARAMETER_MISSING","description":"Parameter limit is required but is missing.","severity":"ERROR"}
        Assert.assertNotNull(status);
    }

    @Test
    public void testRequestQueryRequiredWithEmpty() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> queryMap = new HashMap<>();
        requestEntity.setQueryParameters(queryMap);
        queryMap.put("limit", null);
        Status status = openApiValidator.validateRequestPath("/pets", "get", requestEntity);
        Assert.assertEquals( status.getCode(), "ERR11000");
        //{"statusCode":400,"code":"ERR11001","message":"VALIDATOR_REQUEST_PARAMETER_MISSING","description":"Parameter limit is required but is missing.","severity":"ERROR"}
        Assert.assertNotNull(status);
    }

    @Test
    public void testRequestQueryWithQuery() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("limit", 12);
        requestEntity.setQueryParameters(queryMap);
        Status status = openApiValidator.validateRequestPath("/pets", "get", requestEntity);
        Assert.assertNull(status);
    }

    @Test
    public void testRequestQuery2() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("limit", "abbb");
        requestEntity.setQueryParameters(queryMap);
        Status status = openApiValidator.validateRequestPath("/pets", "get", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11004");
        //{"statusCode":400,"code":"ERR11004","message":"VALIDATOR_SCHEMA","description":"Schema Validation Error - limit: string found, integer expected","severity":"ERROR"}
    }

    @Test
    public void testRequestQueryBooleanType() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("includeCode", "true");
        queryMap.put("limit", 12);
        requestEntity.setQueryParameters(queryMap);
        Status status = openApiValidator.validateRequestPath("/pets", "get", requestEntity);
        Assert.assertNull(status);
    }

    @Test
    public void testRequestQueryBooleanTypeWithError() {

        RequestEntity requestEntity = new RequestEntity();
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("includeCode", "yes");
        queryMap.put("limit", 12);
        requestEntity.setQueryParameters(queryMap);
        Status status = openApiValidator.validateRequestPath("/pets", "get", requestEntity);
        Assert.assertNotNull(status);
        Assert.assertEquals( status.getCode(), "ERR11004");
        //{"statusCode":400,"code":"ERR11004","message":"VALIDATOR_SCHEMA","description":"Schema Validation Error - includeCode: string found, boolean expected","severity":"ERROR"}
    }
}
