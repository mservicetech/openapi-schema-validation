package com.mservicetech.openapi.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mservicetech.openapi.common.ParameterType;
import com.mservicetech.openapi.common.RequestEntity;
import com.mservicetech.openapi.common.ResponseEntity;
import com.mservicetech.openapi.common.Status;
import com.networknt.jsonoverlay.Overlay;
import com.networknt.oas.model.Header;
import com.networknt.oas.model.MediaType;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Path;
import com.networknt.oas.model.RequestBody;
import com.networknt.oas.model.Response;
import com.networknt.oas.model.Schema;
import com.networknt.oas.model.impl.RequestBodyImpl;
import com.networknt.oas.model.impl.SchemaImpl;
import com.networknt.openapi.ApiNormalisedPath;
import com.networknt.openapi.NormalisedPath;
import com.networknt.openapi.OpenApiHelper;
import com.networknt.openapi.OpenApiOperation;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.SchemaValidatorsConfig;

import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class OpenApiValidator {
    Logger logger = LoggerFactory.getLogger(OpenApiValidator.class);
    final String OPENAPI_YML_CONFIG = "openapi.yml";
    final String OPENAPI_YAML_CONFIG = "openapi.yaml";
    final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";

    final String VALIDATOR_REQUEST_BODY_UNEXPECTED = "ERR11013";
    final String VALIDATOR_REQUEST_BODY_MISSING = "ERR11014";
    final String VALIDATOR_REQUEST_PARAMETER_MISSING = "ERR11001";


    final String VALIDATOR_REQUEST_PARAMETER_PATH_MISSING = "ERR11108";
    final String VALIDATOR_REQUEST_PARAMETER_HEADER_MISSING = "ERR11017";
    final String VALIDATOR_REQUEST_PARAMETER_QUERY_MISSING = "ERR11000";

    final String VALIDATOR_RESPONSE_CONTENT_UNEXPECTED = "ERR11018";
    final String REQUIRED_RESPONSE_HEADER_MISSING = "ERR11019";

    final String DEFAULT_STATUS_CODE = "default";


    public String spec;
    public OpenApiHelper openApiHelper;
    public SchemaValidator schemaValidator;
    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Construct a new request validator with the given schema validator.
     *
     * The default construct will try to load openapi.yml file from classpath
     */
    public OpenApiValidator() {
        InputStream in  = this.getClass().getClassLoader().getResourceAsStream(OPENAPI_YAML_CONFIG);;
        try {
            if (in == null) {
                in = this.getClass().getClassLoader().getResourceAsStream(OPENAPI_YML_CONFIG);
                if (in==null) {
                    throw new IOException("Cannot load openapi spec file");
                }
            }
            spec = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            openApiHelper = new OpenApiHelper(spec);
            schemaValidator = new SchemaValidator(openApiHelper.openApi3);
        } catch (IOException e) {
            logger.error("Initial failed:" + e);
        }
        finally {
            try {
                if( in!=null ) {
                    in.close();
                }
            } catch(IOException e) {
                logger.error(" Failed to close input stream:" + e);
            }
        }
    }

    /**
     * Construct a new request validator with the given schema validator.
     *
     * @param openapiPath The schema file name and path to use when validating request bodies
     */
    public OpenApiValidator(String openapiPath) {
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(openapiPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            spec = reader.lines().collect(Collectors.joining("\n"));
            openApiHelper = new OpenApiHelper(spec);
            schemaValidator = new SchemaValidator(openApiHelper.openApi3);
        } catch (IOException e) {
            logger.error("initial failed:" + e);
        }
    }

    /**
     * Construct a new request validator with the given schema validator.
     *
     * @param openapi The schema input stream to use when validating request bodies
     */
    public OpenApiValidator(InputStream openapi) {
        spec = new BufferedReader(new InputStreamReader(openapi, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        openApiHelper = new OpenApiHelper(spec);
        schemaValidator = new SchemaValidator(openApiHelper.openApi3);
    }

    /**
     * Validate the request against the given API operation
     * @param requestURI normalised path
     * @param httpMethod Http method definition (get/post/put/delete)
     * @param requestEntity wrap object for request
     * @return A validation report containing validation errors
     */
    public Status validateRequestPath (String requestURI , String httpMethod, RequestEntity requestEntity ) {
        requireNonNull(openApiHelper, "openApiHelper object cannot be null");
        OpenApiOperation openApiOperation;
        try {
            openApiOperation = getOpenApiOperation(requestURI, httpMethod);
            if (openApiOperation == null) {
                return new Status(STATUS_INVALID_REQUEST_PATH, requestURI);
            }
        } catch (NullPointerException e) {
            // This exception is thrown from within the OpenApiOperation constructor on operation == null.
            return new Status(STATUS_METHOD_NOT_ALLOWED, requestURI);
        }

        if (requestEntity!=null) {
            NormalisedPath requestPath = openApiOperation.getPathString();
            Status status = validateRequestParameters(requestEntity, requestPath, openApiOperation);
            if(status != null) return status;
            if ((requestEntity.getContentType()==null || requestEntity.getContentType().startsWith("application/json"))) {
                try {
                    Object body = attachJsonBody(requestEntity.getRequestBody());
                    status = validateRequestBody(body, openApiOperation);
//                    if (body!=null) {
//                        status = validateRequestBody(body, openApiOperation);
//                    } 
                } catch (Exception e) {
                     status = new Status( VALIDATOR_REQUEST_BODY_UNEXPECTED, requestPath.normalised());
                }


            }
            return status;
        }

        return null;
    }

    protected Status validateRequestBody (Object requestBody, OpenApiOperation openApiOperation) {
        requireNonNull(openApiHelper, "openApiHelper object cannot be null");
        requireNonNull(schemaValidator, "schemaValidator object cannot be null");

        final RequestBody specBody = openApiOperation.getOperation().getRequestBody();

        if (requestBody != null && specBody == null) {
            return new Status(VALIDATOR_REQUEST_BODY_UNEXPECTED, openApiOperation.getMethod(), openApiOperation.getPathString().original());
        }

        if (specBody == null || !Overlay.isPresent((RequestBodyImpl)specBody)) {
            return null;
        }

        if (requestBody == null) {
            if (specBody.getRequired() != null && specBody.getRequired()) {
                return new Status(VALIDATOR_REQUEST_BODY_MISSING, openApiOperation.getMethod(), openApiOperation.getPathString().original());
            }
            return null;
        }
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setTypeLoose(false);
        config.setHandleNullableField(true);

        return schemaValidator.validate(requestBody, Overlay.toJson((SchemaImpl)specBody.getContentMediaType("application/json").getSchema()), config);
    }

    protected Status validateRequestParameters(final RequestEntity requestEntity, final NormalisedPath requestPath, final OpenApiOperation openApiOperation) {
        Status status = validatePathParameters(requestEntity, requestPath, openApiOperation);
        if(status != null) return status;

        status = validateQueryParameters(requestEntity, openApiOperation);
        if(status != null) return status;

        status = validateHeaderParameters(requestEntity, openApiOperation);
        if(status != null) return status;

        status = validateCookieParameters(requestEntity, openApiOperation);
        if(status != null) return status;

        return null;
    }

    private Status validatePathParameters(final RequestEntity requestEntity, final NormalisedPath requestPath, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getOperation().getParameters(), ParameterType.PATH);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return result.getStatus();
        }
        if  (result.skippedParameters!=null && !result.skippedParameters.isEmpty()) {
            return result.skippedParameters.stream().map(p-> new Status (VALIDATOR_REQUEST_PARAMETER_PATH_MISSING, p.getName(), openApiOperation.getPathString().original()))
                    .filter(s->s != null).findFirst().get();
        }
        return null;
    }

    private ValidationResult validateDeserializedValues(final RequestEntity requestEntity, final Collection<Parameter> parameters, final ParameterType type) {
        ValidationResult validationResult = new ValidationResult();
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setTypeLoose(true);

        parameters.stream()
                .filter(p -> ParameterType.is(p.getIn(), type))
                .forEach(p->{
                    Object deserializedValue = getDeserializedValue(requestEntity, p.getName(), type);
                    if (null==deserializedValue ) {
                        if (p.getRequired() != null && p.getRequired()) {
                            validationResult.addSkipped(p);
                        }
                    } else {
                        JsonNodePath instanceLocation = new JsonNodePath(config.getPathType()).append(p.getName());
                        Status s = schemaValidator.validate(deserializedValue, Overlay.toJson((SchemaImpl)(p.getSchema())), config, instanceLocation);
                        validationResult.addStatus(s);
                    }
                });

        return validationResult;
    }

    private Object getDeserializedValue(final RequestEntity requestEntity, final String name, final ParameterType type) {
        if (null!=type && StringUtils.isNotBlank(name)) {
            switch(type){
                case QUERY:
                    return requestEntity.getQueryParameters()==null? null:requestEntity.getQueryParameters().get(name);
                case PATH:
                    return requestEntity.getPathParameters()==null?null:requestEntity.getPathParameters().get(name);
                case HEADER:
                    return requestEntity.getHeaderParameters()==null?null:requestEntity.getHeaderParameters().get(name);
                case COOKIE:
                    return requestEntity.getCookieParameters()==null?null:requestEntity.getCookieParameters().get(name);
            }
        }

        return null;
    }

    private Status validateQueryParameters(final RequestEntity requestEntity, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getOperation().getParameters(), ParameterType.QUERY);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return result.getStatus();
        }
        if  (result.skippedParameters!=null && !result.skippedParameters.isEmpty()) {
            return result.skippedParameters.stream().map(p-> new Status (VALIDATOR_REQUEST_PARAMETER_QUERY_MISSING, p.getName(), openApiOperation.getPathString().original()))
                    .filter(s->s != null).findFirst().get();
        }
        return null;
    }

    private Status validateHeaderParameters(final RequestEntity requestEntity,
                                            final OpenApiOperation openApiOperation) {

        // validate path level parameters for headers first.
        Optional<Status> optional = validatePathLevelHeaders(requestEntity, openApiOperation);
        if(optional.isPresent()) {
            return optional.get();
        } else {
            // validate operation level parameter for headers second.
            optional = validateOperationLevelHeaders(requestEntity, openApiOperation);
            return optional.orElse(null);
        }

    }

    private Optional<Status> validatePathLevelHeaders(final RequestEntity requestEntity, final OpenApiOperation openApiOperation) {
       ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getPathObject().getParameters(), ParameterType.HEADER);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return Optional.ofNullable(result.getStatus());
        }
        if  (result.skippedParameters!=null && !result.skippedParameters.isEmpty()) {
            return result.skippedParameters.stream().map(p-> new Status (VALIDATOR_REQUEST_PARAMETER_HEADER_MISSING, p.getName(), openApiOperation.getPathString().original()))
                    .filter(s->s != null).findFirst();
        }
        return Optional.ofNullable(null);
    }



    private Optional<Status> validateOperationLevelHeaders(final RequestEntity requestEntity, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getOperation().getParameters(), ParameterType.HEADER);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return Optional.ofNullable(result.getStatus());
        }
        if  (result.skippedParameters!=null && !result.skippedParameters.isEmpty()) {
            return result.skippedParameters.stream().map(p-> new Status (VALIDATOR_REQUEST_PARAMETER_HEADER_MISSING, p.getName(), openApiOperation.getPathString().original()))
                    .filter(s->s != null).findFirst();
        }
        return Optional.ofNullable(null);
    }

    private Status validateCookieParameters(final RequestEntity requestEntity,
                                            final OpenApiOperation openApiOperation) {
        // validate path level parameters for cookies first.
        Optional<Status> optional = validatePathLevelCookies(requestEntity, openApiOperation);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            // validate operation level parameter for cookies second.
            optional = validateOperationLevelCookies(requestEntity, openApiOperation);
            return optional.orElse(null);
        }

    }

    private Optional<Status> validatePathLevelCookies(final RequestEntity requestEntity, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getPathObject().getParameters(), ParameterType.COOKIE);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return Optional.ofNullable(result.getStatus());
        }
        if  (result.skippedParameters!=null && !result.skippedParameters.isEmpty()) {
            return result.skippedParameters.stream().map(p-> new Status (VALIDATOR_REQUEST_PARAMETER_MISSING, p.getName(), openApiOperation.getPathString().original()))
                    .filter(s->s != null).findFirst();
        }
        return Optional.ofNullable(null);
    }



    private Optional<Status> validateOperationLevelCookies(final RequestEntity requestEntity, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getOperation().getParameters(), ParameterType.COOKIE);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return Optional.ofNullable(result.getStatus());
        }
        if  (result.skippedParameters!=null && !result.skippedParameters.isEmpty()) {
            return result.skippedParameters.stream().map(p-> new Status (VALIDATOR_REQUEST_PARAMETER_MISSING, p.getName(), openApiOperation.getPathString().original()))
                    .filter(s->s != null).findFirst();
        }
        return Optional.ofNullable(null);
    }

    /**
     * validate a given response content object
     * @param responseContent response content needs to be validated
     * @param openApiOperation OpenApi Operation which is located by uri and httpMethod
     * @param statusCode eg. 200, 400
     * @param mediaTypeName eg. "application/json"
     * @return Status return null if no validation errors
     */
    protected Status validateResponseContent(Object responseContent, OpenApiOperation openApiOperation, String statusCode, String mediaTypeName) {
        //try to convert json string to structured object
        if(responseContent instanceof String) {
            try {
                responseContent = attachJsonBody((String)responseContent);
            } catch (Exception e) {
                return new Status(VALIDATOR_RESPONSE_CONTENT_UNEXPECTED, openApiOperation.getMethod(), openApiOperation.getPathString().original());
            }
        }
        JsonNode schema = getContentSchema(openApiOperation, statusCode, mediaTypeName);
        //if cannot find schema based on status code, try to get from "default"
        if(schema == null || schema.isMissingNode()) {
            // if corresponding response exist but also does not contain any schema, pass validation
            if (openApiOperation.getOperation().getResponses().containsKey(String.valueOf(statusCode))) {
                return null;
            }
            schema = getContentSchema(openApiOperation, DEFAULT_STATUS_CODE, mediaTypeName);
            // if default also does not contain any schema, pass validation
            if (schema == null || schema.isMissingNode()) return null;
        }
        if ((responseContent != null && schema == null) ||
                (responseContent == null && schema != null)) {
            return new Status(VALIDATOR_RESPONSE_CONTENT_UNEXPECTED, openApiOperation.getMethod(), openApiOperation.getPathString().original());
        }
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setTypeLoose(false);
        config.setHandleNullableField(true);
        return schemaValidator.validate(responseContent, schema, config);
    }

    /**
     * Validate the response against the given API operation
     * @param requestURI normalised path
     * @param httpMethod Http method definition (get/post/put/delete)
     * @param statusCode Http status code
     * @param responseEntity wrapper object for response
     * @return A validation report containing validation errors
     */
    public Status validateResponsePath(String requestURI, String httpMethod, String statusCode, ResponseEntity responseEntity) {
        OpenApiOperation openApiOperation;
        try {
            openApiOperation = getOpenApiOperation(requestURI, httpMethod);
            if (openApiOperation == null) {
                return new Status(STATUS_INVALID_REQUEST_PATH, requestURI);
            }
        } catch (NullPointerException e) {
            // This exception is thrown from within the OpenApiOperation constructor on operation == null.
            return new Status(STATUS_METHOD_NOT_ALLOWED, requestURI);
        }
        Status status = validateHeaders(responseEntity.getHeaders(), openApiOperation, statusCode);
        if(status != null) return status;
        return validateResponseContent(responseEntity.getContent(), openApiOperation, statusCode, responseEntity.getContentType());
    }

    private Status validateHeaders(Map<String, ?> headers, OpenApiOperation operation, String statusCode) {
        Optional<Response> response = Optional.ofNullable(operation.getOperation().getResponse(statusCode));
        if(response.isPresent()) {
            Map<String, Header> headerMap = response.get().getHeaders();
            Optional<Status> optional = headerMap.entrySet()
                    .stream()
                    //based on OpenAPI specification, ignore "Content-Type" header
                    //If a response header is defined with the name "Content-Type", it SHALL be ignored. - https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#responseObject
                    .filter(entry -> !"Content-Type".equalsIgnoreCase(entry.getKey()))
                    .map(p -> validateHeader(headers, p.getKey(), p.getValue()))
                    .filter(s -> s != null)
                    .findFirst();
            if(optional.isPresent()) {
                return optional.get();
            }
        }
        return null;
    }

    private Status validateHeader(Map<String, ?> headers, String headerName, Header operationHeader) {
        // According to RFC7230, header field names are case-insensitive.
        Optional<Object> headerValue = Optional.ofNullable(headers).flatMap(opt -> opt.entrySet()
                .stream()
                .filter(entry -> headerName.equalsIgnoreCase(entry.getKey()))
                .map(entry -> entry.getValue())
                .findFirst()
        );
        if (headerValue.isEmpty()) {
            if (Boolean.TRUE.equals(operationHeader.getRequired())) {
                return new Status(REQUIRED_RESPONSE_HEADER_MISSING, headerName);
            }
        } else {
            SchemaValidatorsConfig config = new SchemaValidatorsConfig();
            //header won't tell if it's a real string or not. needs trying to convert.
            config.setTypeLoose(true);
            config.setHandleNullableField(true);
            return schemaValidator.validate(headerValue.get(), Overlay.toJson((SchemaImpl)operationHeader.getSchema()), config);
        }
        return null;
    }

    /**
     * Method used to parse the body into a Map or a List and attach it into exchange
     *
     * @param bodyString   unparsed request body
     * @throws IOException
     * @return body object
     */
    private Object attachJsonBody( String bodyString) throws Exception {
        Object body = null;
        if (bodyString != null) {
            bodyString = bodyString.trim();
            if (bodyString.startsWith("{")) {
                body = objectMapper.readValue(bodyString, new TypeReference<Map<String, Object>>() {
                });
            } else if (bodyString.startsWith("[")) {
                body = objectMapper.readValue(bodyString, new TypeReference<List<Object>>() {
                });
            } else {
                // error here. The content type in head doesn't match the body.
                throw new Exception("The content type in head doesn't match the body");
            }

        }
        return body;
    }

    /**
     * locate operation based on uri and httpMethod
     * @param requestURI normalised path
     * @param httpMethod http method of the request
     * @return OpenApiOperation the wrapper of an api operation
     */
    private OpenApiOperation getOpenApiOperation(String requestURI, String httpMethod) {
        NormalisedPath requestPath = new ApiNormalisedPath(requestURI, openApiHelper.basePath);
        Optional<NormalisedPath> maybeApiPath = openApiHelper.findMatchingApiPath(requestPath);
        if (!maybeApiPath.isPresent()) {
            return null;
        }

        final NormalisedPath openApiPathString = maybeApiPath.get();
        final Path path = openApiHelper.openApi3.getPath(openApiPathString.original());

        final Operation operation = path.getOperation(httpMethod);
        return new OpenApiOperation(openApiPathString, path, httpMethod, operation);
    }

    private JsonNode getContentSchema(OpenApiOperation operation, String statusCode, String mediaTypeStr) {
        Schema schema;
        Optional<Response> response = Optional.ofNullable(operation.getOperation().getResponse(String.valueOf(statusCode)));
        if(response.isPresent()) {
            Optional<MediaType> mediaType = Optional.ofNullable(response.get().getContentMediaType(mediaTypeStr));
            if(mediaType.isPresent()) {
                schema = mediaType.get().getSchema();
                JsonNode schemaNode = schema == null ? null : Overlay.toJson((SchemaImpl)schema);
                return schemaNode;
            }
        }
        return null;
    }

    class ValidationResult {
        private Set<Parameter> skippedParameters = new HashSet<>();;
        private List<Status> statuses = new ArrayList<>();

        public void addSkipped(Parameter p) {
            skippedParameters.add(p);
        }

        public void addStatus(Status s) {
            if (null!=s) {
                statuses.add(s);
            }
        }

        public Set<Parameter> getSkippedParameters(){
            return Collections.unmodifiableSet(skippedParameters);
        }

        public Status getStatus() {
            return statuses.isEmpty()?null:statuses.get(0);
        }

        public List<Status> getAllStatues(){
            return Collections.unmodifiableList(statuses);
        }
    }
}
