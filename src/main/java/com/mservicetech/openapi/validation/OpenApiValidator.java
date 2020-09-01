package com.mservicetech.openapi.validation;

import com.mservicetech.openapi.common.RequestEntity;
import com.networknt.jsonoverlay.Overlay;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Path;
import com.networknt.oas.model.RequestBody;
import com.networknt.oas.model.impl.RequestBodyImpl;
import com.networknt.oas.model.impl.SchemaImpl;
import com.networknt.openapi.ApiNormalisedPath;
import com.networknt.openapi.NormalisedPath;
import com.networknt.openapi.OpenApiOperation;
import com.networknt.openapi.parameter.ParameterType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.status.Status;

import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLDecoder;
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
    final String VALIDATOR_REQUEST_PARAMETER_HEADER_MISSING = "ERR11017";
    final String VALIDATOR_REQUEST_PARAMETER_QUERY_MISSING = "ERR11000";

    public String spec;
    public OpenApiHelper openApiHelper;
    public SchemaValidator schemaValidator;

    public OpenApiValidator() {
        try {
            InputStream in = this.getClass().getResourceAsStream(OPENAPI_YML_CONFIG);
            if (in == null) {
                throw new IOException("cannot load openapi spec file");
            }
            spec = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            openApiHelper = new OpenApiHelper(spec);
            schemaValidator = new SchemaValidator(openApiHelper.getOpenApi3());
        } catch (Exception e) {
            logger.error("initial failed:" + e);
        }
    }

    public OpenApiValidator(String openapiPath) {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(openapiPath);
        spec = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        openApiHelper = new OpenApiHelper(spec);
        schemaValidator = new SchemaValidator(openApiHelper.getOpenApi3());
    }

    public OpenApiValidator(InputStream openapi) {
        spec = new BufferedReader(new InputStreamReader(openapi, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        openApiHelper = new OpenApiHelper(spec);
        schemaValidator = new SchemaValidator(openApiHelper.getOpenApi3());
    }

    public Status validateRequestPath (String requestURI , String httpMethod, RequestEntity requestEntity ) {
        requireNonNull(openApiHelper, "openApiHelper object cannot be null");
        final NormalisedPath requestPath = new ApiNormalisedPath(requestURI);
        final Optional<NormalisedPath> maybeApiPath = openApiHelper.findMatchingApiPath(requestPath);
        if (!maybeApiPath.isPresent()) {
            Status status = new Status( STATUS_INVALID_REQUEST_PATH, requestPath.normalised());
            return status;
        }

        final NormalisedPath openApiPathString = maybeApiPath.get();
        final Path path = openApiHelper.getOpenApi3().getPath(openApiPathString.original());

        final Operation operation = path.getOperation(httpMethod.toLowerCase());
        OpenApiOperation openApiOperation = new OpenApiOperation(openApiPathString, path, httpMethod, operation);

        if (operation == null) {
            Status status = new Status( STATUS_METHOD_NOT_ALLOWED, requestPath.normalised());
            return status;
        }

        if (requestEntity!=null) {
            Status status = validateRequestParameters(requestEntity, requestPath, openApiOperation);
            if(status != null) return status;
            if (requestEntity.getContentType()==null || requestEntity.getContentType().startsWith("application/json")) {
                status = validateRequestBody(requestEntity.getRequestBody(), openApiOperation);
            }
            return status;
        }

        return null;
    }

    public Status validateRequestBody (String requestBody, OpenApiOperation openApiOperation) {
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

    private Status validateRequestParameters(final RequestEntity requestEntity, final NormalisedPath requestPath, final OpenApiOperation openApiOperation) {
        Status status = validatePathParameters(requestEntity, requestPath, openApiOperation);
        if(status != null) return status;

        status = validateQueryParameters(requestEntity, openApiOperation);
        if(status != null) return status;

        status = validateHeaderParameters(requestEntity, openApiOperation);
        if(status != null) return status;

        return null;
    }

    private Status validatePathParameters(final RequestEntity requestEntity, final NormalisedPath requestPath, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getOperation().getParameters(), ParameterType.PATH);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return result.getStatus();
        }

        // validate values that cannot be deserialized or do not need to be deserialized
        Status status = null;
        for (int i = 0; i < openApiOperation.getPathString().parts().size(); i++) {
            if (!openApiOperation.getPathString().isParam(i)) {
                continue;
            }

            final String paramName = openApiOperation.getPathString().paramName(i);
            final Optional<Parameter> parameter = result.getSkippedParameters()
                    .stream()
                    .filter(p -> p.getName().equalsIgnoreCase(paramName))
                    .findFirst();

            if (parameter.isPresent()) {
                String paramValue = requestPath.part(i); // If it can't be UTF-8 decoded, use directly.
                try {
                    paramValue = URLDecoder.decode(requestPath.part(i), "UTF-8");
                } catch (Exception e) {
                    logger.info("Path parameter cannot be decoded, it will be used directly");
                }

                return schemaValidator.validate(paramValue, Overlay.toJson((SchemaImpl)(parameter.get().getSchema())), paramName);
            }
        }
        return status;
    }

    private ValidationResult validateDeserializedValues(final RequestEntity requestEntity, final Collection<Parameter> parameters, final ParameterType type) {
        ValidationResult validationResult = new ValidationResult();

        parameters.stream()
                .filter(p -> ParameterType.is(p.getIn(), type))
                .forEach(p->{
                    Object deserializedValue = getDeserializedValue(requestEntity, p.getName(), type);
                    if (null==deserializedValue) {
                        validationResult.addSkipped(p);
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
                    return requestEntity.getQueryParameters().get(name);
                case PATH:
                    return requestEntity.getPathParameters().get(name);
                case HEADER:
                    return requestEntity.getHeaderParameters().get(name);
                case COOKIE:
                    return requestEntity.getCookieParameters().get(name);
            }
        }

        return null;
    }
    private Status validateQueryParameters(final RequestEntity requestEntity, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getOperation().getParameters(), ParameterType.QUERY);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return result.getStatus();
        }

        // validate values that cannot be deserialized or do not need to be deserialized
        Optional<Status> optional = result.getSkippedParameters()
                .stream()
                .map(p -> validateQueryParameter(requestEntity, openApiOperation, p))
                .filter(s -> s != null)
                .findFirst();

        return optional.orElse(null);
    }


    private Status validateQueryParameter(final RequestEntity requestEntity,
                                          final OpenApiOperation openApiOperation,
                                          final Parameter queryParameter) {

        final Collection<String> queryParameterValues = (Collection<String>)requestEntity.getQueryParameters().get(queryParameter.getName());

        if ((queryParameterValues == null || queryParameterValues.isEmpty())) {
            if(queryParameter.getRequired() != null && queryParameter.getRequired()) {
                return new Status(VALIDATOR_REQUEST_PARAMETER_QUERY_MISSING, queryParameter.getName(), openApiOperation.getPathString().original());
            }
            // Validate the value contains by queryParameterValue, if it is the only elements inside the array deque.
            // Since if the queryParameterValue's length smaller than 2, it means the query parameter is not an array,
            // thus not necessary to apply array validation to this value.
        } else if (queryParameterValues.size() < 2) {

            Optional<Status> optional = queryParameterValues
                    .stream()
                    .map((v) -> schemaValidator.validate(v, Overlay.toJson((SchemaImpl)queryParameter.getSchema()), queryParameter.getName()))
                    .filter(s -> s != null)
                    .findFirst();

            return optional.orElse(null);
            // Validate the queryParameterValue directly instead of validating its elements, if the length of this array deque larger than 2.
            // Since if the queryParameterValue's length larger than 2, it means the query parameter is an array.
            // thus array validation should be applied, for example, validate the length of the array.
        } else {
            return schemaValidator.validate(queryParameterValues, Overlay.toJson((SchemaImpl)queryParameter.getSchema()), queryParameter.getName());
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

        return result.getSkippedParameters().stream()
                .map(p -> validateHeader(requestEntity, openApiOperation, p))
                .filter(s -> s != null)
                .findFirst();
    }



    private Optional<Status> validateOperationLevelHeaders(final RequestEntity requestEntity, final OpenApiOperation openApiOperation) {
        ValidationResult result = validateDeserializedValues(requestEntity, openApiOperation.getOperation().getParameters(), ParameterType.HEADER);

        if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
            return Optional.ofNullable(result.getStatus());
        }

        return result.getSkippedParameters().stream()
                .map(p -> validateHeader(requestEntity, openApiOperation, p))
                .filter(s -> s != null)
                .findFirst();
    }

    private Status validateHeader(final RequestEntity requestEntity,
                                  final OpenApiOperation openApiOperation,
                                  final Parameter headerParameter) {
        final Object  headerValue = requestEntity.getHeaderParameters().get(headerParameter.getName());
        if (headerValue == null ) {
            if(headerParameter.getRequired()) {
                return new Status(VALIDATOR_REQUEST_PARAMETER_HEADER_MISSING, headerParameter.getName(), openApiOperation.getPathString().original());
            }
        } else {
           return schemaValidator.validate(headerValue, Overlay.toJson((SchemaImpl)headerParameter.getSchema()), headerParameter.getName());
//            Optional<Status> optional = headerValues
//                    .stream()
//                    .map((v) -> schemaValidator.validate(v, Overlay.toJson((SchemaImpl)headerParameter.getSchema()), headerParameter.getName()))
//                    .filter(s -> s != null)
//                    .findFirst();
//            return optional.orElse(null);
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

        public List<Status> getAllStatueses(){
            return Collections.unmodifiableList(statuses);
        }
    }
}
