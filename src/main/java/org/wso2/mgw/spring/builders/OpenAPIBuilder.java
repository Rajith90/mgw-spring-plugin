package org.wso2.mgw.spring.builders;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.apache.maven.project.MavenProject;
import org.reflections.Reflections;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.wso2.apimgt.gateway.cli.constants.OpenAPIConstants;
import org.wso2.apimgt.gateway.cli.model.route.EndpointListRouteDTO;
import org.wso2.apimgt.gateway.cli.model.route.RouteEndpointConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenAPIBuilder {
    private static final Pattern CURLY_BRACES_PATTERN = Pattern.compile("(?<=\\{)(?!\\s*\\{)[^{}]+");
    private Class<?> serviceClass;
    private OpenAPI openAPI;
    private Reflections reflections;
    private MavenProject mavenProject;

    public OpenAPIBuilder(Reflections reflections,Class<?> serviceClass) {
        this.serviceClass = serviceClass;
        this.reflections = reflections;
        generateOpenAPI();

    }

    public OpenAPIBuilder(Reflections reflections,MavenProject project, Class<?> serviceClass) {
        this.serviceClass = serviceClass;
        this.reflections = reflections;
        this.mavenProject = project;
        generateOpenAPI();
    }

    public String getOpenAPIAsString() {
        return Yaml.pretty(openAPI);
    }

    public OpenAPI getOpenAPI() {
        return openAPI;
    }

    private void generateOpenAPI() {
        String basePath = getServiceBasePath();
        openAPI = new OpenAPI();
        Info info = new Info();
        info.setTitle(mavenProject != null? mavenProject.getArtifactId():serviceClass.getSimpleName());
        if(mavenProject != null) {
            info.setDescription(mavenProject.getDescription());
            info.setVersion(mavenProject.getVersion());
        }
        openAPI.setInfo(info);
        openAPI.addExtension(OpenAPIConstants.BASEPATH, basePath);
        setEndpointToOpenAPI(basePath);
        setServicePathsToOpenAPI();
    }

    private void setEndpointToOpenAPI(String basePath) {
        EndpointListRouteDTO prodEndpointListRouteDTO = new EndpointListRouteDTO();
        prodEndpointListRouteDTO.addEndpoint("http://localhost:8080" + basePath);
        openAPI.addExtension(OpenAPIConstants.PRODUCTION_ENDPOINTS, prodEndpointListRouteDTO);

    }

    private void setServicePathsToOpenAPI(){

        Set<Method> methods =reflections.getMethodsAnnotatedWith(RequestMapping.class);
        for(Method method: methods) {
            RequestMapping requestMappingAnnotation = method.getAnnotation(RequestMapping.class);
            System.out.printf("Found method: %s, with meta name: %s%n",
                    method.getName(), requestMappingAnnotation.value()[0]);
            addOrUpdatePathToOpenAPI(requestMappingAnnotation);
        }

    }

    /**
     * Add a new path based on the provided URI template to swagger if it does not exists. If it exists,
     * adds the respective operation to the existing path
     *
     * @param requestMappingAnnotation  swagger object
     */
    private void addOrUpdatePathToOpenAPI(RequestMapping requestMappingAnnotation) {
        PathItem path;
        if (openAPI.getPaths() == null) {
            openAPI.setPaths(new Paths());
        }
        if (openAPI.getPaths().get(requestMappingAnnotation.value()[0]) != null) {
            path = openAPI.getPaths().get(requestMappingAnnotation.value()[0]);
        } else {
            path = new PathItem();
        }

        Operation operation = createOperation(requestMappingAnnotation);
        addOperationsToPath(path, operation, requestMappingAnnotation);
        openAPI.getPaths().addPathItem(requestMappingAnnotation.value()[0], path);
    }

    private void addOperationsToPath(PathItem path, Operation operation, RequestMapping requestMappingAnnotation) {
        RequestMethod[] requestMethods = requestMappingAnnotation.method();
        if(requestMethods.length > 0) {
            for (RequestMethod requestMethod : requestMethods) {
                PathItem.HttpMethod httpMethod = PathItem.HttpMethod.valueOf(requestMethod.name());
                path.operation(httpMethod, operation);
            }
        } else {
            path.operation(PathItem.HttpMethod.GET,operation);
            path.operation(PathItem.HttpMethod.POST,operation);
            path.operation(PathItem.HttpMethod.PUT,operation);
            path.operation(PathItem.HttpMethod.DELETE,operation);
            path.operation(PathItem.HttpMethod.OPTIONS,operation);
        }
    }

    /**
     * Creates a new operation object using the RequestMapping object
     *
     * @param requestMappingAnnotation API resource data
     * @return a new operation object using the URI template object
     */
    private Operation createOperation(RequestMapping requestMappingAnnotation) {
        Operation operation = new Operation();
        populatePathParameters(operation, requestMappingAnnotation.value()[0]);

        ApiResponses apiResponses = new ApiResponses();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.description("OK");
        apiResponses.addApiResponse("200", apiResponse);
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

    private String getServiceBasePath() {
        RequestMapping requestMappingAnnotation = serviceClass.getAnnotation(RequestMapping.class);
        if(requestMappingAnnotation != null) {
            return requestMappingAnnotation.value()[0];
        }
        return "*";
    }
}
