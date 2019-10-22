package org.wso2.mgw.spring.utils;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;

public class ConverterUtils {

    public static String getOpenAPIAsString(OpenAPI openAPI) {
        return Yaml.pretty(openAPI);
    }
}
