package org.wso2.mgw.spring.models;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OpenAPIData {
    /**
     * Maps to Swagger PathItem/Operation
     */
    public static class Resource {
        private String path;
        private String verb;
        private String authType;
        private String policy;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getVerb() {
            return verb;
        }

        public void setVerb(String verb) {
            this.verb = verb;
        }

        public String getAuthType() {
            return authType;
        }

        public void setAuthType(String authType) {
            this.authType = authType;
        }

        public String getPolicy() {
            return policy;
        }

        public void setPolicy(String policy) {
            this.policy = policy;
        }


    }

    private String title;
    private String description;
    private String version;
    private String contactName;
    private String contactEmail;
    private String transportType;
    private String security;
    private String apiLevelPolicy;
    private Set<Resource> resources = new HashSet<Resource>();



    public Set<Resource> getResources() {
        return resources;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getTransportType() {
        return transportType;
    }

    public String getSecurity() {
        return security;
    }

    public String getApiLevelPolicy() {
        return apiLevelPolicy;
    }
}
