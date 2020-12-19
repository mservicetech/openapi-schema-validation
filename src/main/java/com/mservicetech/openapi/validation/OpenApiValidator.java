package com.mservicetech.openapi.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mservicetech.openapi.common.ParameterType;
import com.mservicetech.openapi.common.RequestEntity;
import com.mservicetech.openapi.common.Status;
import com.networknt.jsonoverlay.Overlay;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Path;
import com.networknt.oas.model.RequestBody;
import com.networknt.oas.model.impl.RequestBodyImpl;
import com.networknt.oas.model.impl.SchemaImpl;
import com.networknt.openapi.ApiNormalisedPath;
import com.networknt.openapi.NormalisedPath;
import com.networknt.openapi.OpenApiHelper;
import com.networknt.openapi.OpenApiOperation;
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
    final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";

    final String VALIDATOR_REQUEST_BODY_UNEXPECTED = "ERR11013";
    final String VALIDATOR_REQUEST_BODY_MISSING = "ERR11014";
    final String VALIDATOR_REQUEST_PARAMETER_MISSING = "ERR11001";


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
        try {
            InputStream in = this.getClass().getClassLoader().getResourceAsStream(OPENAPI_YML_CONFIG);
            if (in == null) {
                throw new IOException("cannot load openapi spec file");
            }
            spec = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            openApiHelper = OpenApiHelper.init(spec);
            schemaValidator = new SchemaValidator(openApiHelper.openApi3);
        } catch (Exception e) {
            logger.error("initial failed:" + e);
        }
    }

    /**
     * Construct a new request validator with the given schema validator.
     *
     * @param openapiPath The schema file name and path to use when validating request bodies
     */
    public OpenApiValidator(String openapiPath) {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(openapiPath);
        spec = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        openApiHelper = OpenApiHelper.init(spec);
        schemaValidator = new SchemaValidator(openApiHelper.openApi3);
    }

    /**
     * Construct a new request validator with the given schema validator.
     *
     * @param openapi The schema input stream to use when validating request bodies
     */
    public OpenApiValidator(InputStream openapi) {
        spec = new BufferedReader(new InputStreamReader(openapi, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        openApiHelper = OpenApiHelper.init(spec);
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
        final NormalisedPath requestPath = new ApiNormalisedPath(requestURI);
        final Optional<NormalisedPath> maybeApiPath = openApiHelper.findMatchingApiPath(requestPath);
        if (!maybeApiPath.isPresent()) {
            Status status = new Status( STATUS_INVALID_REQUEST_PATH, requestPath.normalised());
            return status;
        }

        final NormalisedPath openApiPathString = maybeApiPath.get();
        final Path path = openApiHelper.openApi3.getPath(openApiPathString.original());

        final Operation operation = path.getOperation(httpMethod.toLowerCase());
        OpenApiOperation openApiOperation = new OpenApiOperation(openApiPathString, path, httpMethod, operation);

        if (operation == null) {
            Status status = new Status( STATUS_METHOD_NOT_ALLOWED, requestPath.normalised());
            return status;
        }

        if (requestEntity!=null) {
            Status status = validateRequestParameters(requestEntity, requestPath, openApiOperation);
            if(status != null) return status;
            if ((requestEntity.getContentType()==null || requestEntity.getContentType().startsWith("application/json"))) {
                try {
                    Object body = attachJsonBody(requestEntity.getRequestBody());
                    if (body!=null) {
                        status = validateRequestBody(body, openApiOperation);
                    }
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

        return schemaValidator.validate(requestBody, Overlay.toJson((SchemaImpl)specBody.getContentMediaType("application/json").getSchema()), config, "requestBody");
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
            return new Status(VALIDATOR_REQUEST_PARAMETER_MISSING,  openApiOperation.getMethod(), openApiOperation.getPathString().original());
        }
        return null;
    }

    private ValidationResult validateDeserializedValues(final RequestEntity requestEntity, final Collection<Parameter> parameters, final ParameterType type) {
        ValidationResult validationResult = new ValidationResult();

        parameters.stream()
                .filter(p -> ParameterType.is(p.getIn(), type))
                .forEach(p->{
                    Object deserializedValue = getDeserializedValue(requestEntity, p.getName(), type);
                    if (null==deserializedValue ) {
                        if (p.getRequired()) {
                            validationResult.addSkipped(p);
                        }
                    }else {
                        Status s = schemaValidator.validate(deserializedValue, Overlay.toJson((SchemaImpl)(p.getSchema())), p.getName());
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
            return new Status(VALIDATOR_REQUEST_PARAMETER_MISSING,  openApiOperation.getMethod(), openApiOperation.getPathString().original());
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
            return Optional.ofNullable(new Status(VALIDATOR_REQUEST_PARAMETER_MISSING,  openApiOperation.getMethod(), openApiOperation.getPathString().original()));
        }
        return Optional.ofNullable(null);
    }



    private Optional<Status> validateOperationLevelHeaders(final RequestEntity requestEntity, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getOperation().getParameters(), ParameterType.HEADER);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return Optional.ofNullable(result.getStatus());
        }
        if  (result.skippedParameters!=null && !result.skippedParameters.isEmpty()) {
            return Optional.ofNullable(new Status(VALIDATOR_REQUEST_PARAMETER_MISSING,  openApiOperation.getMethod(), openApiOperation.getPathString().original()));
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
            return Optional.ofNullable(new Status(VALIDATOR_REQUEST_PARAMETER_MISSING,  openApiOperation.getMethod(), openApiOperation.getPathString().original()));
        }
        return Optional.ofNullable(null);
    }



    private Optional<Status> validateOperationLevelCookies(final RequestEntity requestEntity, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getOperation().getParameters(), ParameterType.COOKIE);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return Optional.ofNullable(result.getStatus());
        }
        if  (result.skippedParameters!=null && !result.skippedParameters.isEmpty()) {
            return Optional.ofNullable(new Status(VALIDATOR_REQUEST_PARAMETER_MISSING,  openApiOperation.getMethod(), openApiOperation.getPathString().original()));
        }
        return Optional.ofNullable(null);
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

        public List<Status> getAllStatueses(){
            return Collections.unmodifiableList(statuses);
        }
    }
}
