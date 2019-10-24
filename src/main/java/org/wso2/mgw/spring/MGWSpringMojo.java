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
import org.wso2.mgw.spring.models.ConfigModel;
import org.wso2.mgw.spring.utils.ConverterUtils;

import java.util.List;

@Mojo( name = "add-gateway")
public class MGWSpringMojo extends AbstractMojo {

    private Log log = getLog();

    @Parameter( property = "toolkitHome")
    private String toolkitHome;

    @Parameter( property = "buildProject")
    private ConfigModel buildProject;

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
        if (buildProject.getOpenAPIName() == null && buildProject.getPackageName() == null) {
            throw new MojoExecutionException(
                    "Either packageName or openAPIName should present in the plugin configurations");
        }
        try {
            OpenAPIBuilder openAPIBuilder = new OpenAPIBuilder(project, buildProject);
            List<OpenAPI> openAPIList = openAPIBuilder.generate();
            CLIExecutor cliExecutor = CLIExecutor.getInstance();
            cliExecutor.generateFromDefinition(project.getName() != null ? project.getName(): project.getArtifactId(), openAPIList);
        } catch (CLIExecutorException | OpenAPIBuilderException e) {
            String message = "Error while building micro gateway for the spring service";
            log.error(message + " : " + e.getMessage());
            log.debug(message, e);
            throw new MojoExecutionException(message, e);
        }
    }


}
