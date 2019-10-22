package org.wso2.mgw.spring.builders;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wso2.mgw.spring.constants.PluginConstants;
import org.wso2.mgw.spring.exception.OpenAPIBuilderException;
import org.wso2.mgw.spring.mappers.OpenAPIServiceMapper;
import org.wso2.mgw.spring.models.ConfigModel;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class OpenAPIBuilder {
    private static final Logger log = LoggerFactory.getLogger(OpenAPIBuilder.class);

    private String packageName;
    private Reflections reflections;
    private MavenProject mavenProject;
    private Properties projectProperties = new Properties();
    private String openAPIFileName;
    private boolean processProject;

    public OpenAPIBuilder(MavenProject project, ConfigModel configModel) throws OpenAPIBuilderException {
        this.packageName = configModel.getPackageName();
        this.mavenProject = project;
        this.processProject = configModel.isProcessProject();
        this.openAPIFileName = configModel.getOpenAPIName();
        initReflections();
    }

    public List<OpenAPI> generate() throws OpenAPIBuilderException {
        List<OpenAPI> openAPIList = new ArrayList<>();
        if (this.openAPIFileName != null) {
            OpenAPIServiceMapper openAPIServiceMapper = new OpenAPIServiceMapper(getOpenAPIFromFile(), mavenProject,
                    projectProperties);
            openAPIList.add(openAPIServiceMapper.getOpenAPI());
            if (processProject) {
                addSpringServicesAsOpenAPIs(openAPIList);
            }
        } else {
            addSpringServicesAsOpenAPIs(openAPIList);
        }
        return openAPIList;
    }

    private void addSpringServicesAsOpenAPIs(List<OpenAPI> openAPIList) {
        Set<Class<?>> classes = getSpringServiceClasses();
        for (Class<?> cl : classes) {
            RequestMapping findable = cl.getAnnotation(RequestMapping.class);
            System.out.printf("Found class: %s, with meta name: %s%n", cl.getSimpleName(), findable.name());
            OpenAPIServiceMapper openAPIServiceMapper = new OpenAPIServiceMapper(reflections, mavenProject,
                    projectProperties, cl);
            System.out.println(openAPIServiceMapper.getOpenAPIAsString());
            openAPIList.add(openAPIServiceMapper.getOpenAPI());
        }
    }

    private ClassLoader getClassLoaderForProjectClasses() throws OpenAPIBuilderException {
        List<String> runtimeClasspathElements = null;
        try {
            runtimeClasspathElements = mavenProject.getRuntimeClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            String message = "Error while loading spring service classes to class path";
            log.error(message, e.getMessage());
            log.debug(message, e);
            throw new OpenAPIBuilderException(message, e);
        }
        URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];
        for (int i = 0; i < runtimeClasspathElements.size(); i++) {
            String element = runtimeClasspathElements.get(i);
            try {
                runtimeUrls[i] = new File(element).toURI().toURL();
            } catch (MalformedURLException e) {
                log.error("Error while reading class '" + element + "' to be added to the class path", e);
            }
        }
        return new URLClassLoader(runtimeUrls, Thread.currentThread().getContextClassLoader());

    }

    private void initReflections() throws OpenAPIBuilderException {
        ClassLoader newLoader = getClassLoaderForProjectClasses();
        Collection<URL> urls = ClasspathHelper.forPackage(packageName, newLoader);
        reflections = new Reflections(new ConfigurationBuilder().setUrls(urls).addClassLoader(newLoader)
                .setScanners(new MethodAnnotationsScanner(), new TypeAnnotationsScanner(), new SubTypesScanner()));
        try {
            projectProperties.load(newLoader.getResourceAsStream(PluginConstants.APPLICATION_PROPERTIES_FILE));
        } catch (IOException e) {
            String message =
                    "Error while reading the spring project " + PluginConstants.APPLICATION_PROPERTIES_FILE + " file";
            log.warn(message, e.getMessage());
            log.debug(message, e);
        }
    }

    private OpenAPI getOpenAPIFromFile() throws OpenAPIBuilderException {
        ClassLoader newLoader = getClassLoaderForProjectClasses();
        OpenAPI openAPI = new OpenAPIV3Parser().read(newLoader.getResource(openAPIFileName).getPath());
        return openAPI;
    }

    private Set<Class<?>> getSpringServiceClasses() {
        return reflections.getTypesAnnotatedWith(RestController.class);
    }

}
