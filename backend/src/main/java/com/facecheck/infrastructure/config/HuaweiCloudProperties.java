package com.facecheck.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "facecheck.huawei")
public class HuaweiCloudProperties {

    private boolean enabled;
    private boolean obsEnabled;
    private String ak;
    private String sk;
    private String projectId;
    private String region;
    private String frsEndpoint;
    private String obsEndpoint;
    private String obsRegion;
    private String obsBucket;
    private String faceSetName;
    private double similarityThreshold;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isObsEnabled() {
        return obsEnabled;
    }

    public void setObsEnabled(boolean obsEnabled) {
        this.obsEnabled = obsEnabled;
    }

    public String getAk() {
        return ak;
    }

    public void setAk(String ak) {
        this.ak = ak;
    }

    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getFrsEndpoint() {
        return frsEndpoint;
    }

    public void setFrsEndpoint(String frsEndpoint) {
        this.frsEndpoint = frsEndpoint;
    }

    public String getObsEndpoint() {
        return obsEndpoint;
    }

    public void setObsEndpoint(String obsEndpoint) {
        this.obsEndpoint = obsEndpoint;
    }

    public String getObsRegion() {
        return obsRegion;
    }

    public void setObsRegion(String obsRegion) {
        this.obsRegion = obsRegion;
    }

    public String getObsBucket() {
        return obsBucket;
    }

    public void setObsBucket(String obsBucket) {
        this.obsBucket = obsBucket;
    }

    public String getFaceSetName() {
        return faceSetName;
    }

    public void setFaceSetName(String faceSetName) {
        this.faceSetName = faceSetName;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
