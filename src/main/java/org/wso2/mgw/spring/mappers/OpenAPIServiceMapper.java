package org.wso2.mgw.spring.mappers;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.maven.project.MavenProject;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.wso2.mgw.spring.RequestMethodType;
import org.wso2.mgw.spring.constants.PluginConstants;
import org.wso2.mgw.spring.models.ResourceMapperModel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class OpenAPIServiceMapper {
    private static final Logger log = LoggerFactory.getLogger(OpenAPIServiceMapper.class);

    private OpenAPI openAPI;
    private Class<?> serviceClass;
    private Reflections reflections;
    private MavenProject mavenProject;
    private Properties projectProperties;
    private boolean isExtendedOpenAPI;

    public OpenAPIServiceMapper(Reflections reflections, MavenProject project, Properties projectProperties,
            Class<?> serviceClass, boolean isExtendedOpenAPI) {
        this.serviceClass = serviceClass;
        this.reflections = reflections;
        this.mavenProject = project;
        this.projectProperties = projectProperties;
        this.isExtendedOpenAPI = isExtendedOpenAPI;
        generateOpenAPI();
    }

    public OpenAPIServiceMapper(OpenAPI openAPI, MavenProject project, Properties projectProperties) {
        this.openAPI = openAPI;
        this.mavenProject = project;
        this.projectProperties = projectProperties;
    }

    private void generateOpenAPI() {
        String basePath = getServiceBasePath();
        openAPI = new OpenAPI();
        Info info = new Info();
        info.setTitle(serviceClass.getSimpleName());
        if (mavenProject != null) {
            info.setDescription(mavenProject.getDescription());
            info.setVersion(mavenProject.getVersion());
        }
        openAPI.setInfo(info);
        openAPI.addExtension(PluginConstants.BASE_PATH, basePath);
        setEndpointToOpenAPI(basePath);
        setServicePathsToOpenAPI();
    }

    private void setEndpointToOpenAPI(String basePath) {
        OpenAPIEndpointMapper openAPIEndpointMapper = new OpenAPIEndpointMapper(projectProperties);
        openAPIEndpointMapper.addServersToOpenAPI(openAPI, basePath);

    }

    private String getServiceBasePath() {
        RequestMapping requestMappingAnnotation = serviceClass.getAnnotation(RequestMapping.class);
        if (requestMappingAnnotation != null) {
            return requestMappingAnnotation.value()[0];
        }
        return "/";
    }

    private void setServicePathsToOpenAPI() {

        Map<RequestMethodType, Set<Method>> operationsMap = getMethodsWithResourceMappings();

        operationsMap.forEach((key, methods) -> methods.forEach(method -> {
            ResourceMapperModel resourceMapperModel = null;
            switch (key) {
            case DEFAULT:
                resourceMapperModel = buildRequestMapper(method.getAnnotation(RequestMapping.class));
                break;
            case GET:
                resourceMapperModel = buildRequestMapperFromGet(method.getAnnotation(GetMapping.class));
                break;
            case PUT:
                resourceMapperModel = buildRequestMapperFromPut(method.getAnnotation(PutMapping.class));
                break;
            case DELETE:
                resourceMapperModel = buildRequestMapperFromDelete(method.getAnnotation(DeleteMapping.class));
                break;
            case POST:
                resourceMapperModel = buildRequestMapperFromPost(method.getAnnotation(PostMapping.class));
                break;
            case PATCH:
                resourceMapperModel = buildRequestMapperFromPatch(method.getAnnotation(PatchMapping.class));
            }
            OpenAPIResourceMapper openAPIResourceMapper = new OpenAPIResourceMapper(resourceMapperModel);
            Schema schema = null;
            if(isExtendedOpenAPI) {
                schema = setSchemasToComponents(method);
            }
            openAPIResourceMapper.addOrUpdatePathToOpenAPI(openAPI, schema);

        }));

    }

    private Schema setSchemasToComponents(Method method) {
        Components components = openAPI.getComponents();
        if (components == null) {
            components = new Components();
            openAPI.setComponents(components);
        }
        Schema schema = new Schema();
        if (method.getReturnType().isPrimitive() || method.getReturnType().equals(String.class)) {
            schema.setType(method.getReturnType().getSimpleName());
            return schema;
        } else {
            components.addSchemas(method.getReturnType().getSimpleName(),
                    setComponentsToOpenAPI(schema, method.getReturnType().getDeclaredFields()));
            openAPI.setComponents(components);
            Schema refSchema = new Schema();
            refSchema.set$ref(method.getReturnType().getSimpleName());
            return refSchema;
        }
    }

    private Schema setComponentsToOpenAPI(Schema schema, Field[] fields) {

        for (Field field : fields) {
            if (Modifier.isPrivate(field.getModifiers())) {
                field.setAccessible(true);
            }
            Schema subSchema = new Schema();
            if (field.getType().isPrimitive() || field.getType().equals(String.class)) {
                subSchema.setType(resolveBasicDataType(field.getType().getSimpleName()));
                schema.addProperties(field.getName(), subSchema);
            } else if(field.getType().isArray()) {
                subSchema.setType(PluginConstants.ARRAY_TYPE);
                setComponentsToOpenAPI(subSchema, field.getType().getComponentType().getDeclaredFields());
                openAPI.getComponents().addSchemas(field.getType().getComponentType().getSimpleName(),subSchema);
                Schema refSchema = new Schema();
                refSchema.setType(field.getType().getComponentType().getSimpleName());
                refSchema.set$ref(field.getType().getComponentType().getSimpleName());
                schema.addProperties(field.getName(), refSchema);
            } else {
                subSchema.setType(PluginConstants.OBJECT_TYPE);
                setComponentsToOpenAPI(subSchema, field.getType().getDeclaredFields());
                openAPI.getComponents().addSchemas(field.getType().getSimpleName(),subSchema);
                Schema refSchema = new Schema();
                refSchema.setType(field.getType().getSimpleName());
                refSchema.set$ref(field.getType().getSimpleName());
                schema.addProperties(field.getName(), refSchema);
            }
        }
        return schema;

    }

    private Map<RequestMethodType, Set<Method>> getMethodsWithResourceMappings() {
        Map<RequestMethodType, Set<Method>> operationsMap = new HashMap<>();
        operationsMap.put(RequestMethodType.DEFAULT, reflections.getMethodsAnnotatedWith(RequestMapping.class));
        // Add all get operations
        operationsMap.put(RequestMethodType.GET, reflections.getMethodsAnnotatedWith(GetMapping.class));
        // Add all post operations
        operationsMap.put(RequestMethodType.POST, reflections.getMethodsAnnotatedWith(PostMapping.class));
        // Add all put operations
        operationsMap.put(RequestMethodType.PUT, reflections.getMethodsAnnotatedWith(PutMapping.class));
        // Add all delete operations
        operationsMap.put(RequestMethodType.DELETE, reflections.getMethodsAnnotatedWith(DeleteMapping.class));
        // Add all patch operations
        operationsMap.put(RequestMethodType.PATCH, reflections.getMethodsAnnotatedWith(PatchMapping.class));
        return operationsMap;
    }

    private String resolveBasicDataType(String dataType) {
        switch (dataType){
        case "boolean":
            return PluginConstants.BOOLEAN_TYPE;
        case "int":
        case "long":
            return PluginConstants.INTEGER_TYPE;
        case "float":
        case "double":
            return PluginConstants.NUMBER_TYPE;
        default:
            return PluginConstants.STRING_TYPE;
        }
    }

    private ResourceMapperModel buildRequestMapper(RequestMapping requestMappingAnnotation) {
        return new ResourceMapperModel.Builder(requestMappingAnnotation.name())
                .headers(requestMappingAnnotation.headers()).consumes(requestMappingAnnotation.consumes())
                .method(requestMappingAnnotation.method()).params(requestMappingAnnotation.params())
                .path(requestMappingAnnotation.path()).produces(requestMappingAnnotation.produces())
                .value(requestMappingAnnotation.value()).build();
    }

    private ResourceMapperModel buildRequestMapperFromGet(GetMapping getMappingAnnotation) {
        return new ResourceMapperModel.Builder(getMappingAnnotation.name()).headers(getMappingAnnotation.headers())
                .consumes(getMappingAnnotation.consumes()).method(new RequestMethod[] { RequestMethod.GET })
                .params(getMappingAnnotation.params()).path(getMappingAnnotation.path())
                .produces(getMappingAnnotation.produces()).value(getMappingAnnotation.value()).build();
    }

    private ResourceMapperModel buildRequestMapperFromPut(PutMapping putMappingAnnotation) {
        return new ResourceMapperModel.Builder(putMappingAnnotation.name()).headers(putMappingAnnotation.headers())
                .consumes(putMappingAnnotation.consumes()).method(new RequestMethod[] { RequestMethod.PUT })
                .params(putMappingAnnotation.params()).value(putMappingAnnotation.value())
                .path(putMappingAnnotation.path()).produces(putMappingAnnotation.produces()).build();
    }

    private ResourceMapperModel buildRequestMapperFromPost(PostMapping postMappingAnnotation) {
        return new ResourceMapperModel.Builder(postMappingAnnotation.name()).headers(postMappingAnnotation.headers())
                .consumes(postMappingAnnotation.consumes()).method(new RequestMethod[] { RequestMethod.POST })
                .params(postMappingAnnotation.params()).path(postMappingAnnotation.path())
                .produces(postMappingAnnotation.produces()).value(postMappingAnnotation.value()).build();
    }

    private ResourceMapperModel buildRequestMapperFromDelete(DeleteMapping deleteMappingAnnotation) {
        return new ResourceMapperModel.Builder(deleteMappingAnnotation.name()).headers(deleteMappingAnnotation.headers())
                .consumes(deleteMappingAnnotation.consumes()).method(new RequestMethod[] { RequestMethod.DELETE })
                .params(deleteMappingAnnotation.params()).path(deleteMappingAnnotation.path())
                .produces(deleteMappingAnnotation.produces()).value(deleteMappingAnnotation.value()).build();
    }

    private ResourceMapperModel buildRequestMapperFromPatch(PatchMapping patchMappingAnnotation) {
        return new ResourceMapperModel.Builder(patchMappingAnnotation.name()).headers(patchMappingAnnotation.headers())
                .consumes(patchMappingAnnotation.consumes()).method(new RequestMethod[] { RequestMethod.PATCH })
                .params(patchMappingAnnotation.params()).path(patchMappingAnnotation.path())
                .produces(patchMappingAnnotation.produces()).value(patchMappingAnnotation.value()).build();
    }

    public OpenAPI getOpenAPI() {
        return openAPI;
    }

    public void setOpenAPI(OpenAPI openAPI) {
        this.openAPI = openAPI;
    }

    public String getOpenAPIAsString() {
        return Yaml.pretty(openAPI);
    }
}

