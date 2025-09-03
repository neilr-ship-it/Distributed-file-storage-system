package dev.mdfs.common;

import java.util.List;
//this record represents a single entry in a files manifest, the manifest is essentially
//a map of how to reconstruct the file from its chunks, the fields are chunkID which 
//is the unique id for this chunk, sha256, duplicate string copy for readability,
//size is the size of chunk in bytes, replicas is where the chunk lives in list of 
//node urls, orderIndex is the positon of this chunk in the original file, client 
//to node for chunkPutRequest then node back to client with the acknowledgemnet and 
//coordinator collects these acks o build file manifest, which we store in coordinator db
//each chunkREf is one chunk entry in that manifest, client later uses manifest to 
//redownload the file 

public record ChunkRef(ChunkId chunkId, String sha256, long size, List<String> replicas, int orderIndex) {

}
