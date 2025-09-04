package dev.mdfs.coordinator.cluster;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component //makes this class a spring managed bean, so it can be auto detected and
//injected elsewhere, java.net.URI is a URI used to represent network adresses like 
//https://localhost:8080
public class NodeRegistry {
    private final List<URI> nodes; //immutable list of node urls (storage servers in my system)
    public NodeRegistry() {
        String csv = System.getenv().getOrDefault("NODES", "http://localhost:8081,http://localhost:8082,http://localhost:8083");
        //when we deploy across mutliple storage nodes i will set the NODES env vvar 
        //in my shell, or docker compose file 
        List<URI> list = new ArrayList<>();
        for(String s : csv.split(",")) {
            s = s.trim();
            if(!s.isEmpty()) {
                list.add(URI.create(s));
            }
        }
        //above lines of code splits csv string into parts trims whitespace 
        //and then for each non empty string converts it into a URI object and adds 
        //it to the list,  set nodes = to this list 
        this.nodes = Collections.unmodifiableList(list)
    }

    //now we want to provide read only access to all registered nodes, since its 
    //unmodifiable callers cant alter this list as that would be pretty catastrophic
    public List<URI> accessAll() {
        return nodes;
    }

    //use wrap around logic to deterministically pick r storage r nodes for storing 
    //r nodes for storing replicas of a chunk based on some key like a chunk id
    //an edge case could be if no nodes are registered then return an empty list
    //r being the replication factor, the r nodes we will pick will be returned 
    //in a URI list 
    public List<URI> chooseReplicas(int repFactor, String key) {
        if(nodes.isEmpty()) {
            return List.of();
        }
        //picking deterministic start index and doing mod by nodes.size guarantees
        //integer will be wrapped into valid range of 0 to nodes.size - 1
        int startIndex = Math.floorMod(key.hashCode(), nodes.size());
        List<URI> outputList = new ArrayList<>(repFactor); //output lsit of length r(# rep factors)
        //loop runs r times each time moving forward from the start index and modding by nodes.size
        //this helps because on if one of the increments we reach end of possible nodes in nodes list
        //we then wrap around to the front until we pick r nodes
        for(int i = 0; i < repFactor; i++) {
            outputList.add(nodes.get((startIndex + i) % nodes.size()));
        }
        return outputList;
    }
}
