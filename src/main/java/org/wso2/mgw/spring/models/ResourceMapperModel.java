package org.wso2.mgw.spring.models;

import org.springframework.web.bind.annotation.RequestMethod;

public class ResourceMapperModel {

    private final String name;
    private final String[] value;
    private final String[] path;
    private final RequestMethod[] method;
    private final String[] params;
    private final String[] headers;
    private final String[] consumes;
    private final String[] produces;

    public ResourceMapperModel(Builder builder) {
        this.name = builder.name;
        this.consumes = builder.consumes;
        this.produces = builder.produces;
        this.value = builder.value;
        this.params = builder.params;
        this.method = builder.method;
        this.path = builder.path;
        this.headers = builder.headers;
    }

    public static class Builder {
        private String name;
        private String[] value;
        private String[] path;
        private RequestMethod[] method;
        private String[] params;
        private String[] headers;
        private String[] consumes;
        private String[] produces;

        public Builder(String name) {
            this.name = name;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder value(String[] value) {
            this.value = value;
            return this;
        }

        public Builder path(String[] path) {
            this.path = path;
            return this;
        }

        public Builder method(RequestMethod[] method) {
            this.method = method;
            return this;
        }

        public Builder params(String[] params) {
            this.params = params;
            return this;
        }

        public Builder headers(String[] headers) {
            this.headers = headers;
            return this;
        }

        public Builder consumes(String[] consumes) {
            this.consumes = consumes;
            return this;
        }

        public Builder produces(String[] produces) {
            this.produces = produces;
            return this;
        }

        public ResourceMapperModel build() {
            return new ResourceMapperModel(this);
        }

    }

    public String getName() {
        return name;
    }

    public String[] getValue() {
        return value;
    }

    public String[] getPath() {
        return path;
    }

    public RequestMethod[] getMethod() {
        return method;
    }

    public String[] getParams() {
        return params;
    }

    public String[] getHeaders() {
        return headers;
    }

    public String[] getConsumes() {
        return consumes;
    }

    public String[] getProduces() {
        return produces;
    }
}


