package org.wso2.mgw.spring.utils;

import io.swagger.converter.ModelConverters;
import io.swagger.models.properties.Property;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import org.wso2.mgw.spring.constants.PluginConstants;

import java.lang.reflect.Type;

public class ConverterUtils {

    public static String getOpenAPIAsString(OpenAPI openAPI) {
        return Yaml.pretty(openAPI);
    }

    public static boolean isPrimitive(Type cls) {
        boolean isPrimitive = false;

        Property property = ModelConverters.getInstance().readAsProperty(cls);
        if (property == null) {
            isPrimitive = false;
        } else if (PluginConstants.INTEGER_TYPE.equals(property.getType())) {
            isPrimitive = true;
        } else if (PluginConstants.STRING_TYPE.equals(property.getType())) {
            isPrimitive = true;
        } else if (PluginConstants.NUMBER_TYPE.equals(property.getType())) {
            isPrimitive = true;
        } else if (PluginConstants.BOOLEAN_TYPE.equals(property.getType())) {
            isPrimitive = true;
        } else if (PluginConstants.ARRAY_TYPE.equals(property.getType())) {
            isPrimitive = true;
        } else if (PluginConstants.FILE_TYPE.equals(property.getType())) {
            isPrimitive = true;
        }
        return isPrimitive;
    }

    public static boolean isPrimitive(String type) {
        boolean isPrimitive = false;
        if (PluginConstants.INTEGER_TYPE.equals(type)) {
            isPrimitive = true;
        } else if (PluginConstants.STRING_TYPE.equals(type)) {
            isPrimitive = true;
        } else if (PluginConstants.NUMBER_TYPE.equals(type)) {
            isPrimitive = true;
        } else if (PluginConstants.BOOLEAN_TYPE.equals(type)) {
            isPrimitive = true;
        } else if (PluginConstants.ARRAY_TYPE.equals(type)) {
            isPrimitive = true;
        } else if (PluginConstants.FILE_TYPE.equals(type)) {
            isPrimitive = true;
        }
        return isPrimitive;
    }

}
