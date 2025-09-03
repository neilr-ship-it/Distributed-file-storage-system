package dev.mdfs.common;
//this class contains what the client tells a node before sending bytes (metadata in json)
//dont need boilerplate of constructors and fields with record, special class designed 
//for data carrier, defines immutable class with chunkid checksum and size fields
public record ChunkPutRequest(String chunkId, String checkSum, long size) {

}


