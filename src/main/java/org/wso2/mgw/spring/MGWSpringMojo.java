package org.wso2.mgw.spring;

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.wso2.mgw.spring.builders.OpenAPIBuilder;
import org.wso2.mgw.spring.constants.CLIConstants;
import org.wso2.mgw.spring.exception.CLIExecutorException;
import org.wso2.mgw.spring.exception.OpenAPIBuilderException;

import java.util.List;

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

    public void execute() throws MojoExecutionException
    {
        System.setProperty(CLIConstants.SYSTEM_PROP_TOOLKIT, toolkitHome);
        try {
            OpenAPIBuilder openAPIBuilder = new OpenAPIBuilder(packageName, project);
            List<OpenAPI> openAPIList = openAPIBuilder.generate();
            CLIExecutor cliExecutor = CLIExecutor.getInstance();
            cliExecutor.generateFromDefinition("Sample", openAPIList);
        } catch (CLIExecutorException | OpenAPIBuilderException e) {
            String message = "Error while building micro gateway for the spring service";
            log.error(message + " : " + e.getMessage());
            log.debug(message, e);
            throw new MojoExecutionException(message, e);
        }
    }


}
