package org.example.ui.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.file.Paths;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserConfig {

    private String username;
    private String password;
    private String nodeUsername;
    private String nodePassword;
    private String url;
    private String configPath;
    private String resourcesFolder;
    private String country;
    private String execute;
    private String orga;
    private String dist;

    public UserConfig() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNodeUsername() { return nodeUsername; }
    public void setNodeUsername(String nodeUsername) { this.nodeUsername = nodeUsername; }

    public String getNodePassword() { return nodePassword; }
    public void setNodePassword(String nodePassword) { this.nodePassword = nodePassword; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getConfigPath() { return configPath; }
    public void setConfigPath(String configPath) { this.configPath = configPath; }

    public String getResourcesFolder() { return resourcesFolder; }
    public void setResourcesFolder(String resourcesFolder) { this.resourcesFolder = resourcesFolder; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getExecute() { return execute; }
    public void setExecute(String execute) { this.execute = execute; }

    public String getOrga() { return orga; }
    public void setOrga(String orga) { this.orga = orga; }

    public String getDist() { return dist; }
    public void setDist(String dist) { this.dist = dist; }

    public boolean shouldExecute() {
        return !"no".equalsIgnoreCase(execute);
    }

    public String getResolvedConfigPath() {
        return Paths.get(resourcesFolder != null ? resourcesFolder : "", configPath != null ? configPath : "").toString();
    }
}