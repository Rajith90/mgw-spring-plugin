package org.wso2.mgw.spring.mappers;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.wso2.mgw.spring.constants.PluginConstants;

import java.util.Properties;

class OpenAPIEndpointMapper {

    OpenAPIEndpointMapper(Properties projectProperties) {
        this.projectProperties = projectProperties;
    }

    private Properties projectProperties;

    void addServersToOpenAPI(OpenAPI openAPI, String basePath) {
        Server server = new Server();
        String port = projectProperties
                .getProperty(PluginConstants.SERVER_PORT, PluginConstants.DEFAULT_SERVER_PORT_VALUE);
        server.setUrl("http://localhost:" + port + basePath);
        openAPI.addServersItem(server);
    }
}
