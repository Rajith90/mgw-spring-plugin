package org.wso2.mgw.spring.mappers;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.web.bind.annotation.RequestMethod;
import org.wso2.mgw.spring.models.ResourceMapperModel;
import org.wso2.mgw.spring.utils.ConverterUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class OpenAPIResourceMapper {

    private static final Pattern CURLY_BRACES_PATTERN = Pattern.compile("(?<=\\{)(?!\\s*\\{)[^{}]+");
    private ResourceMapperModel resourceMapperModel;
    private Method method;

    OpenAPIResourceMapper(ResourceMapperModel resourceMapperModel, Method method) {
        this.resourceMapperModel = resourceMapperModel;
        this.method = method;
    }

    /**
     * Add a new path based on the provided URI template to swagger if it does not exists. If it exists,
     * adds the respective operation to the existing path
     *
     * @param openAPI  {@link OpenAPI} open API object of the service
     * @param schema swagger object schema
     */
     void addOrUpdatePathToOpenAPI(OpenAPI openAPI, Schema schema) {
        PathItem path;
        if (openAPI.getPaths() == null) {
            openAPI.setPaths(new Paths());
        }
        String[] servicePaths = resourceMapperModel.getValue().length > 0 ?
                resourceMapperModel.getValue() :
                resourceMapperModel.getPath();
        for (String servicePath : servicePaths) {
            if (openAPI.getPaths().get(servicePath) != null) {
                path = openAPI.getPaths().get(servicePath);
            } else {
                path = new PathItem();
            }

            Operation operation = createOperation(servicePath,schema);
            addOperationsToPath(path, operation);
            openAPI.getPaths().addPathItem(servicePath, path);
        }
    }

    private void addOperationsToPath(PathItem path, Operation operation) {
        RequestMethod[] requestMethods = resourceMapperModel.getMethod();
        if (requestMethods.length > 0) {
            for (RequestMethod requestMethod : requestMethods) {
                PathItem.HttpMethod httpMethod = PathItem.HttpMethod.valueOf(requestMethod.name());
                path.operation(httpMethod, operation);
            }
        } else {
            path.operation(PathItem.HttpMethod.GET, operation);
            path.operation(PathItem.HttpMethod.POST, operation);
            path.operation(PathItem.HttpMethod.PUT, operation);
            path.operation(PathItem.HttpMethod.DELETE, operation);
            path.operation(PathItem.HttpMethod.OPTIONS, operation);
        }
    }

    /**
     * Creates a new operation object using the RequestMapping object
     *
     * @param servicePath path mentioned as annotation in spring service
     * @return a new operation object using the URI template object
     */
    private Operation createOperation(String servicePath, Schema schema) {
        Operation operation = new Operation();
        operation.setOperationId(method.getName() + servicePath.replaceAll("/", "_"));
        populatePathParameters(operation, servicePath);

        ApiResponses apiResponses = new ApiResponses();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.description("OK");
        apiResponses.addApiResponse("200", apiResponse);
        if(schema != null) {
            Content content = new Content();
            if (resourceMapperModel.getProduces() != null && resourceMapperModel.getProduces().length > 0) {
                for (String produce : resourceMapperModel.getProduces()) {
                    MediaType mediaType = new MediaType().schema(schema);
                    content.addMediaType(produce, mediaType);
                }
                apiResponse.setContent(content);
            } else if(ConverterUtils.isPrimitive(schema.getType())) {
                MediaType mediaType = new MediaType().schema(schema);
                content.addMediaType("text/plain", mediaType);
                apiResponse.setContent(content);
            } else {
                apiResponse.set$ref(schema.get$ref());
            }
        }

        operation.setResponses(apiResponses);
        return operation;
    }

    /**
     * Construct path parameters to the Operation
     *
     * @param operation OpenAPI operation
     * @param pathName  pathname
     */
    private void populatePathParameters(Operation operation, String pathName) {
        List<String> pathParams = getPathParamNames(pathName);
        Parameter parameter;
        if (pathParams.size() > 0) {
            for (String pathParam : pathParams) {
                parameter = new Parameter();
                parameter.setName(pathParam);
                parameter.setRequired(true);
                parameter.setIn("path");
                Schema schema = new Schema();
                schema.setType("string");
                parameter.setSchema(schema);
                operation.addParametersItem(parameter);
            }
        }
    }

    /**
     * Extract and return path parameters in the given URI template
     *
     * @param uriTemplate URI Template value
     * @return path parameters in the given URI template
     */
    private List<String> getPathParamNames(String uriTemplate) {
        List<String> params = new ArrayList<String>();

        Matcher bracesMatcher = CURLY_BRACES_PATTERN.matcher(uriTemplate);
        while (bracesMatcher.find()) {
            params.add(bracesMatcher.group());
        }
        return params;
    }
}
