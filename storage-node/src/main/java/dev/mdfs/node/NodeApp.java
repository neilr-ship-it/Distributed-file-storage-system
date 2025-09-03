package dev.mdfs.node;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

//phase 2 goal is a single node that can serve http get put requests for chunks stream to disk and verify checksums, for this we need a long running process that listens on a port and a http stack to
//recieve rfequests and stream bodies, along withi this lifecycle plus dependency wiring and config loading this is all acomplished with node app plus spring boot, if we didnt create app we would have to manually
//set up a servlet container router lifecycle etc 

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
//the above line tells spring boot to bind values from application.yal into a class named storage properties 
public class NodeApp {
    public static void main(String[] args) {
        SpringApplication.run(NodeApp.class, args);
    }
}
