package org.wso2.mgw.spring;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wso2.mgw.spring.builders.OpenAPIBuilder;
import org.wso2.mgw.spring.constants.CLIConstants;
import org.wso2.mgw.spring.exception.CLIExecutorException;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mojo( name = "add-gateway")
public class MGWSpringMojo extends AbstractMojo {

    private Log log = getLog();

    @Parameter( property = "packageName")
    private String packageName;

    @Parameter( property = "toolkitHome")
    private String toolkitHome;

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    private Reflections reflections;

    public void execute() throws MojoExecutionException
    {
        log.info(packageName);
        log.info( "####################" );
        System.setProperty(CLIConstants.SYSTEM_PROP_TOOLKIT, toolkitHome);
        initReflections();
        Set<Class<?>> classes =getSpringServiceClasses();
        List<OpenAPIBuilder> openAPIBuilders = new ArrayList<>();
        for (Class<?> cl : classes) {
            RequestMapping findable = cl.getAnnotation(RequestMapping.class);
            System.out.printf("Found class: %s, with meta name: %s%n",
                    cl.getSimpleName(), findable.name());
            OpenAPIBuilder openAPIBuilder = new OpenAPIBuilder(reflections, project, cl);
            System.out.println(openAPIBuilder.getOpenAPIAsString());
            openAPIBuilders.add(openAPIBuilder);
        }
        try {
            CLIExecutor cliExecutor = CLIExecutor.getInstance();
            cliExecutor.generateFromDefinition("Sample", openAPIBuilders);
        } catch (CLIExecutorException e) {
            e.printStackTrace();
        }
    }


    private ClassLoader getClassLoaderForProjectClasses() {
        List<String> runtimeClasspathElements = null;
        try {
            runtimeClasspathElements = project.getRuntimeClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            e.printStackTrace();
        }
        URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];
        for (int i = 0; i < runtimeClasspathElements.size(); i++) {
            String element = (String) runtimeClasspathElements.get(i);
            try {
                runtimeUrls[i] = new File(element).toURI().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        return new URLClassLoader(runtimeUrls,
                Thread.currentThread().getContextClassLoader());

    }

    private Set<Class<?>> getSpringServiceClasses() {
        return reflections.getTypesAnnotatedWith(RestController.class);
    }

    private void initReflections() {
        ClassLoader newLoader = getClassLoaderForProjectClasses();
        Collection<URL> urls = ClasspathHelper.forPackage(packageName, newLoader);
        reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(urls).addClassLoader(newLoader)
                .setScanners(new MethodAnnotationsScanner(), new TypeAnnotationsScanner(), new SubTypesScanner()));
    }
}
