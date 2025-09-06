package dev.mdfs.coordinator.dto;

import java.util.List;
//this is the request body which we send for POST files/uploadid/commit
public record CommitRequest(String fileName, List<String> chunkOrder) {

}

