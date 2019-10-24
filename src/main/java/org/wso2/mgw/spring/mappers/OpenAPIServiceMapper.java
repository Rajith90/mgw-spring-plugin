package org.wso2.mgw.spring.mappers;

import io.swagger.models.properties.Property;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
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
import org.wso2.mgw.spring.utils.ConverterUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenAPIServiceMapper {
    private static final Logger log = LoggerFactory.getLogger(OpenAPIServiceMapper.class);

    private OpenAPI openAPI;
    private Class<?> serviceClass;
    private Set<Class<?>> compositeServiceClasses;
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

    public OpenAPIServiceMapper(Reflections reflections, MavenProject project, Properties projectProperties,
            Set<Class<?>> serviceClasses, boolean isExtendedOpenAPI) {
        this.compositeServiceClasses = serviceClasses;
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
        if(serviceClass != null) {
            info.setTitle(serviceClass.getSimpleName());
        } else {
            info.setTitle(mavenProject.getName());
        }
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
        if (serviceClass != null) {
            RequestMapping requestMappingAnnotation = serviceClass.getAnnotation(RequestMapping.class);
            if (requestMappingAnnotation != null) {
                return requestMappingAnnotation.value()[0];
            }
        }
        return "/";
    }

    private void setServicePathsToOpenAPI() {

        Map<RequestMethodType, Set<Method>> operationsMap;
        if (compositeServiceClasses != null) {
            operationsMap = getMethodsWithResourceMappingsForCompositeService();
        } else {
            operationsMap = getMethodsWithResourceMappings();
        }

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
            OpenAPIResourceMapper openAPIResourceMapper = new OpenAPIResourceMapper(resourceMapperModel, method);
            Schema schema = null;
            if (isExtendedOpenAPI) {
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
        if (Collection.class.isAssignableFrom(method.getReturnType())) {
            ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                    .readAllAsResolvedSchema(method.getGenericReturnType());
            schema.setType(PluginConstants.ARRAY_TYPE);
            schema.setProperties(resolvedSchema.referencedSchemas);
            String refName = resolveCollectionGenericType(method);
            openAPI.getComponents().addSchemas(refName, schema);
            Schema refSchema = new Schema();
            refSchema.setType(refName);
            refSchema.set$ref(refName);
            return refSchema;
        } else if (ConverterUtils.isPrimitive(method.getReturnType())) {
            Property property = io.swagger.converter.ModelConverters.getInstance()
                    .readAsProperty(method.getReturnType());
            schema.setType(property.getType());
            return schema;
        } else {
            ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                    .readAllAsResolvedSchema(method.getReturnType());
            openAPI.getComponents().addSchemas(method.getReturnType().getSimpleName(), resolvedSchema.schema);
            resolvedSchema.referencedSchemas.forEach((key, value) -> {
                openAPI.getComponents().addSchemas(key, value);
            });
            Schema refSchema = new Schema();
            refSchema.set$ref(method.getReturnType().getSimpleName());
            return refSchema;
        }
    }

    private String resolveCollectionGenericType(Method method) {
        String genericTypeName = method.getGenericReturnType().getTypeName();
        String collectionDataHolderFullType = StringUtils.substringBetween(genericTypeName, "<", ">");
        String[] splittedFullTypeName = collectionDataHolderFullType.split("\\.");
        return splittedFullTypeName[splittedFullTypeName.length - 1] + method.getReturnType().getSimpleName();
    }


    private Map<RequestMethodType, Set<Method>> getMethodsWithResourceMappings() {
        Map<RequestMethodType, Set<Method>> operationsMap = new HashMap<>();
        operationsMap.put(RequestMethodType.DEFAULT, reflections.getMethodsAnnotatedWith(RequestMapping.class).stream()
                .filter(method -> serviceClass.getName().equals(method.getDeclaringClass().getName()))
                .collect(Collectors.toSet()));
        // Add all get operations
        operationsMap.put(RequestMethodType.GET, reflections.getMethodsAnnotatedWith(GetMapping.class).stream()
                .filter(method -> serviceClass.getName().equals(method.getDeclaringClass().getName()))
                .collect(Collectors.toSet()));
        // Add all post operations
        operationsMap.put(RequestMethodType.POST, reflections.getMethodsAnnotatedWith(PostMapping.class).stream()
                .filter(method -> serviceClass.getName().equals(method.getDeclaringClass().getName()))
                .collect(Collectors.toSet()));
        // Add all put operations
        operationsMap.put(RequestMethodType.PUT, reflections.getMethodsAnnotatedWith(PutMapping.class).stream()
                .filter(method -> serviceClass.getName().equals(method.getDeclaringClass().getName()))
                .collect(Collectors.toSet()));
        // Add all delete operations
        operationsMap.put(RequestMethodType.DELETE, reflections.getMethodsAnnotatedWith(DeleteMapping.class).stream()
                .filter(method -> serviceClass.getName().equals(method.getDeclaringClass().getName()))
                .collect(Collectors.toSet()));
        // Add all patch operations
        operationsMap.put(RequestMethodType.PATCH, reflections.getMethodsAnnotatedWith(PatchMapping.class).stream()
                .filter(method -> serviceClass.getName().equals(method.getDeclaringClass().getName()))
                .collect(Collectors.toSet()));
        return operationsMap;
    }

    private Map<RequestMethodType, Set<Method>> getMethodsWithResourceMappingsForCompositeService() {
        Map<RequestMethodType, Set<Method>> operationsMap = new HashMap<>();
        operationsMap.put(RequestMethodType.DEFAULT, reflections.getMethodsAnnotatedWith(RequestMapping.class).stream()
                .filter(method -> compositeServiceClasses.contains(method.getDeclaringClass()))
                .collect(Collectors.toSet()));
        // Add all get operations
        operationsMap.put(RequestMethodType.GET, reflections.getMethodsAnnotatedWith(GetMapping.class).stream()
                .filter(method -> compositeServiceClasses.contains(method.getDeclaringClass()))
                .collect(Collectors.toSet()));
        // Add all post operations
        operationsMap.put(RequestMethodType.POST, reflections.getMethodsAnnotatedWith(PostMapping.class).stream()
                .filter(method -> compositeServiceClasses.contains(method.getDeclaringClass()))
                .collect(Collectors.toSet()));
        // Add all put operations
        operationsMap.put(RequestMethodType.PUT, reflections.getMethodsAnnotatedWith(PutMapping.class).stream()
                .filter(method -> compositeServiceClasses.contains(method.getDeclaringClass()))
                .collect(Collectors.toSet()));
        // Add all delete operations
        operationsMap.put(RequestMethodType.DELETE, reflections.getMethodsAnnotatedWith(DeleteMapping.class).stream()
                .filter(method -> compositeServiceClasses.contains(method.getDeclaringClass()))
                .collect(Collectors.toSet()));
        // Add all patch operations
        operationsMap.put(RequestMethodType.PATCH, reflections.getMethodsAnnotatedWith(PatchMapping.class).stream()
                .filter(method -> compositeServiceClasses.contains(method.getDeclaringClass()))
                .collect(Collectors.toSet()));
        return operationsMap;
    }

    private String resolveBasicDataType(String dataType) {
        switch (dataType) {
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
        return new ResourceMapperModel.Builder(deleteMappingAnnotation.name())
                .headers(deleteMappingAnnotation.headers()).consumes(deleteMappingAnnotation.consumes())
                .method(new RequestMethod[] { RequestMethod.DELETE }).params(deleteMappingAnnotation.params())
                .path(deleteMappingAnnotation.path()).produces(deleteMappingAnnotation.produces())
                .value(deleteMappingAnnotation.value()).build();
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

