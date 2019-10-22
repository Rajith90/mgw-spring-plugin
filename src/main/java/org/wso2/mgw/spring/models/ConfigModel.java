package org.wso2.mgw.spring.models;

public class ConfigModel {
    String openAPIName;
    boolean processProject;
    String packageName;

    public String getOpenAPIName() {
        return openAPIName;
    }

    public void setOpenAPIName(String openAPIName) {
        this.openAPIName = openAPIName;
    }

    public boolean isProcessProject() {
        return processProject;
    }

    public void setProcessProject(boolean processProject) {
        this.processProject = processProject;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
}
