//storage properties  is the instruction sheet for the node app 
package dev.mdfs.node;

import org.springframework.boot.context.properties.ConfigurationProperties;

//this line tells spring boot to look for properties in application,yml that start with mdfs and bind them into this class 
@ConfigurationProperties(prefix = "mdfs")
public class StorageProperties {
    //this is the base directory for chunk storage, can be overriden via application.yml
    private String dataDir = "./data";

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }
}
