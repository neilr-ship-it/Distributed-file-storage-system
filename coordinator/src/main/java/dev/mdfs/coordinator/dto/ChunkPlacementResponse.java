package dev.mdfs.coordinator.dto;

import java.util.List;

//this record is the response the client gets back for the POST /files{uploadid}/chunks
    //returns put urls list which is urls of all different storage nodes to PUT
    //the bytes, one per replica, ie if r factor was one only one url if 10 then 10 urls
public record ChunkPlacementResponse(List<String> putUrls, int replicas) {
    //json shape:
    //{ "putUrls": ["http://n1/chunks/<id>", "http://n2/chunks/<id>"], "replicas": 2 }
}

