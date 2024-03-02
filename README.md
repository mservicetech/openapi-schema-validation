## Openapi-schema-validation

[![Build Status](https://travis-ci.org/mservicetech/openapi-schema-validation.svg?branch=master)](https://travis-ci.org/github/mservicetech/openapi-schema-validation) 

Openapi-schema-validation provide a component /library which can be used in restful microservice API to validate request payload by openapi specification.

Most API development framework (like spring boot), use the swagger codegen to generate annotation on bean model and validate it on bean load time (bean validation). But for bean validation, there are several limitation:

1. It happen on bean convert time, at this time, the request has been processed. So it is too late to throw the validation error.

2. The bean validation error normally is 500 internal error, and user cannot control error message. If request doesn't march openapi specification definition, it should 400 bad request error and user may want to know the detail error.

3. For wrong URL, it will rely on the server return error which increase server side workload. With schema validation, if the URL doesn't march the endpoint's path defined in the openapi spec, it will throw error directly.


## Summary

Different as release version 1.*, the release version 2.0.* reduced the dependency to the light-4j and undertow library. It only depends on the following light-4j library:

 <artifactId>openapi-helper</artifactId>
 <artifactId>json-overlay</artifactId>
 <artifactId>openapi-parser</artifactId>



#### Performance



#### Parser

It uses Jackson that is the most popular JSON parser in Java. If you are using Jackson parser already in your project, it is natural to choose this library over others for schema validation. 

#### Dependency

 The library has minimum dependencies to ensure there are no dependency conflicts when using it. 

Here are the dependencies. 

```
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${version.jackson}</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>${version.slf4j}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>${version.common-lang3}</version>
        </dependency>
        <dependency>
           <groupId>com.networknt</groupId>
           <artifactId>json-schema-validator</artifactId>
           <version>1.0.44</version>
        </dependency>
```

For detail of json-schema-validator, please refer to: https://github.com/networknt/json-schema-validator 
 
## Prerequisite

The library supports Java 11 and up. If you want to build from the source code, you need to install JDK 11 locally. To support multiple version of JDK, you can use [SDKMAN](https://www.networknt.com/tool/sdk/)

The schema validation is validate openapi 3.0 +. The openapi schema should build with openapi3.0 +


## Usage

Include the dependency in the project pom:

   
   ```xml

  <dependency>
    <groupId>com.mservicetech</groupId>
    <artifactId>openapi-schema-validation</artifactId>
    <version>2.0.9</version>
  </dependency>
   ```

### Initial the OpenApiValidator

1. default construct:

OpenApiValidator openApiValidator = new OpenApiValidator()

it will try load file "openapi.yml" from classpath

2. construct with openapi file path:

OpenApiValidator openApiValidator = new OpenApiValidator("spec/openapi.yaml")

It will try load specified openapi specification from input parameter

3. construct with openapi inputStream:

OpenApiValidator openApiValidator = new OpenApiValidator(InputStream openapi)

It will use the input stream as openapi specification 

### Build request entity based on you request object:

   RequestEntity requestEntity = new RequestEntity();
   
   ```java
       Map<String, ?> pathParameters;
       Map<String, ?> queryParameters;
       Map<String, ?> headerParameters;
       Map<String, ?> cookieParameters;
       String requestBody;
       String contentType;
   ```

 For the request body, the component will validate it only if the contentType = "application/json"
 
 ### Implement validation:
 
  Status status = openApiValidator.validateRequestPath("/pets", "post", requestEntity);
  
  If the request is valid against the openapi spec, the result will be null. Otherwise, the result will be Status object, which include error code and error message

### Validate response:

If so desired, responses can be validated in a similar fashion:

ResponseEntity responseEntity = new ResponseEntity();

```java
    Map<String, ?> headers;
    String content;
    String contentType;
```

Status status = openApiValidator.validateResponsePath("/pets", "post", "200", responseEntity);
