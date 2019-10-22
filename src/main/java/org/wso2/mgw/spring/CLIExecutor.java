package org.wso2.mgw.spring;

import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.mgw.spring.constants.CLIConstants;
import org.wso2.mgw.spring.exception.CLIExecutorException;
import org.wso2.mgw.spring.loggers.CLILogReader;
import org.wso2.mgw.spring.utils.ConverterUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CLIExecutor {
    private static final Logger log = LoggerFactory.getLogger(CLIExecutor.class);
    private String homeDirectory;
    private String cliHome;
    private static CLIExecutor instance;

    public static CLIExecutor getInstance() {
        if (instance == null) {
            instance = new CLIExecutor();
        }
        return instance;
    }

    private CLIExecutor() {
        cliHome = System.getProperty(CLIConstants.SYSTEM_PROP_TOOLKIT);
    }

    /**
     * Generate the project using developer first approach (Using OpenAPI definitions).
     *
     * @param project          project name
     * @param apiBuilders relative paths of openAPI definitions stored in resources directory.
     * @throws CLIExecutorException
     */
    public void generateFromDefinition(String project, List<OpenAPI> apiBuilders)
            throws CLIExecutorException {

        createBackgroundEnv();
        String mgwCommand = this.cliHome + File.separator + CLIConstants.CLI_BIN + File.separator + "micro-gw";
        runInitCmd(mgwCommand, project);
        saveSwaggerDefinitions(project, apiBuilders);
        runBuildCmd(mgwCommand, project);
        copyJarToTarget(project);
    }

    private void createBackgroundEnv() throws CLIExecutorException {
        String baseDir = (System.getProperty(CLIConstants.SYSTEM_PROP_BASE_DIR, ".")) + File.separator + "target";
        Path path;
        try {
            path = Files.createTempDirectory(new File(baseDir).toPath(), CLIConstants.SAMPLE_PROJECT_NAME);
        } catch (IOException e) {
            throw new CLIExecutorException("The directory " + baseDir + " does not exist.", e);
        }
        log.info("CLI Project Home: " + path.toString());
        System.setProperty(CLIConstants.CLI_HOME, this.cliHome);
        log.info("CLI Home: " + this.cliHome);
        System.setProperty("user.dir", path.toString());
        homeDirectory = path.toString();
    }

    /**
     * Initialize the project.
     *
     * @param mgwCommand the path of microgateway executable
     * @param project    project name
     * @throws CLIExecutorException
     */
    private void runInitCmd(String mgwCommand, String project) throws CLIExecutorException {
        String[] initCmdArray = generateBasicCmdArgsBasedOnOS(mgwCommand, "init", project);
        String initErrorMsg = "Error occurred during initializing the project.";
        runProcess(initCmdArray, homeDirectory, initErrorMsg);
    }

    private String[] generateBasicCmdArgsBasedOnOS(String mgwCommand, String mainCommand, String project) {
        if (getOSName().toLowerCase().contains("windows")) {
            return new String[]{"cmd.exe", "/c", mgwCommand.trim() + ".bat", mainCommand, project};
        }
        return new String[]{"bash", mgwCommand, mainCommand, project};
    }

    /**
     * Save openAPI definition (developer first approach)
     *
     * @param projectName   project name
     * @param apiDefinitions api Definition array as String
     */
    private  void saveSwaggerDefinitions(String projectName, List<OpenAPI> apiDefinitions) throws CLIExecutorException{
        if (apiDefinitions.size() < 1) {
            throw new CLIExecutorException("No swagger definition is provided to generate API");
        }
        try {
            Path genPath = Paths.get(getProjectGenDirectoryPath(projectName));
            Path apiDefPath = Paths.get(getProjectGenAPIDefinitionPath(projectName));
            if (Files.notExists(genPath)) {
                Files.createDirectory(genPath);
                Files.createDirectory(apiDefPath);
            }

            for(OpenAPI apiDefinition:apiDefinitions) {
                File desPath = new File(
                        homeDirectory + File.separator + projectName + File.separator +
                                CLIConstants.PROJECT_API_DEFINITIONS_DIR + File.separator + apiDefinition.getInfo().getTitle()+ ".yaml");
                writeContent(ConverterUtils.getOpenAPIAsString(apiDefinition),  desPath);
            }
        } catch (IOException e) {
            throw new CLIExecutorException("Error while copying the swagger to the project directory");
        }
    }

    /**
     * Build the project.
     *
     * @param mgwCommand the path of microgateway executable
     * @param project    project name
     * @throws CLIExecutorException
     */
    private void runBuildCmd(String mgwCommand, String project) throws CLIExecutorException {
        String[] buildCmdArray = generateBasicCmdArgsBasedOnOS(mgwCommand, "build", project);
        String buildErrorMsg = "Error occurred when building the project.";
        runProcess(buildCmdArray, homeDirectory, buildErrorMsg);
    }

    /**
     * Run the process.
     *
     * @param cmdArray      array containing all the commandline arguments
     * @param homeDirectory home directory for the process
     * @param errorMessage  error message needs to be printed if any error is occurred
     * @throws CLIExecutorException
     */
    private void runProcess(String[] cmdArray, String homeDirectory, String errorMessage) throws CLIExecutorException {
        try {
            Process process = Runtime.getRuntime().exec(cmdArray, new String[] {"MICROGW_HOME=" + cliHome, "JAVA_HOME="
                    + System.getenv("JAVA_HOME")}, new File(homeDirectory));
            new CLILogReader("errorStream", process.getErrorStream()).start();
            new CLILogReader("inputStream", process.getInputStream()).start();
            boolean isCompleted = process.waitFor(2, TimeUnit.MINUTES);
            if (!isCompleted) {
                throw new RuntimeException(errorMessage);
            }
            int processExitCode = process.exitValue();
            if (processExitCode != 0) {
                throw new CLIExecutorException(errorMessage);
            }
        } catch (IOException | InterruptedException e) {
            throw new CLIExecutorException(errorMessage, e);
        }
    }

    /**
     * Return the system property value of os.name.
     * System.getProperty("os.name").
     *
     * @return Operating System name
     */
    private  String getOSName() {
        return System.getProperty("os.name");
    }

    /**
     * Write content to a specified file
     *
     * @param content content to be written
     * @param file    file object initialized with path
     * @throws IOException error while writing content to file
     */
    public static void writeContent(String content, File file) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
            writer.flush();
        }
    }

    private void copyJarToTarget(String projectName) throws CLIExecutorException {
        String jarName = projectName + CLIConstants.JAR_EXTENSION;
        Path jarPath = Paths.get(getProjectDirectoryPath(projectName) +
                File.separator + CLIConstants.PROJECT_TARGET_DIR + File.separator + jarName);
        Path destinationPath = Paths.get("./target" + File.separator + jarName);
        try {
            Files.copy(jarPath, destinationPath);
        } catch (IOException e) {
            throw new CLIExecutorException("Error while copying the jar to the target directory", e);
        }
    }


    /**
     * Returns path to the /gen of a given project in the current working directory
     *
     * @param projectName name of the project
     * @return path to the /src of a given project in the current working directory
     */
    private String getProjectGenDirectoryPath(String projectName) {
        return getProjectDirectoryPath(projectName) + File.separator
                + CLIConstants.PROJECT_GEN_DIR;
    }

    /**
     * Returns path to the given project in the current working directory
     *
     * @param projectName name of the project
     * @return path to the given project in the current working directory
     */
    private  String getProjectDirectoryPath(String projectName) {
        return getUserDir() + File.separator + projectName;
    }

    /**
     * Returns current user dir
     *
     * @return current user dir
     */
    public static String getUserDir() {
        String currentDirProp = System.getProperty(CLIConstants.SYS_PROP_CURRENT_DIR);
        if (currentDirProp != null) {
            return currentDirProp;
        } else {
            return System.getProperty(CLIConstants.SYS_PROP_USER_DIR);
        }
    }

    /**
     * Returns path to the /gen/api-definition of a given project in the current working directory
     *
     * @param projectName name of the project
     * @return path to the /gen/api-definition of a given project in the current working directory
     */
    private String getProjectGenAPIDefinitionPath(String projectName) {
        return getProjectDirectoryPath(projectName) + File.separator +
                CLIConstants.PROJECT_GEN_DIR + File.separator +
                CLIConstants.PROJECT_API_DEFINITIONS_DIR;
    }


}
