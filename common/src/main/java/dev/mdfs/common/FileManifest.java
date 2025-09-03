package dev.mdfs.common;

import java.util.List;
//file manifest serves as top level description of the file coordinator returns to this
//for downloads, created by coordinator after file has been uploaded and all chunks 
//successfully stored, returned to client when asked to download file, stored by system as metadata
//
public record FileManifest(String fileID, String fileName, long sizeBytes, int chunkSize, int version, List<ChunkRef> chunks, long createdAtEpochMillis) {

}
//list of chunkref objects is what will tell u which chunk, what hash where it lives and 
//its order in the file and this is what allows reconstruction of the file 
//heres how it may look serialize
/*
 * {
  "fileID": "file-2025-08-31-001",
  "fileName": "file.txt",
  "sizeBytes": 1680688,
  "chunkSize": 1048576,
  "version": 1,
  "chunks": [
    {
      "chunkId": "d4735e3a...ab35",
      "sha256":  "d4735e3a...ab35",
      "size":    1048576,
      "replicas": ["https://node-a.example.com/chunks/d4735e3a...ab35"],
      "orderIndex": 0
    },
    {
      "chunkId": "9b74c989...e6f8",
      "sha256":  "9b74c989...e6f8",
      "size":    632112,
      "replicas": ["https://node-b.example.com/chunks/9b74c989...e6f8"],
      "orderIndex": 1
    }
  ],
  "createdAtEpochMillis": 1756691452000
}//chunks is where the list of chunkref objects we can see in this example there are 2,
good visual to help understand whats going on in the background 
 */