package dev.mdfs.coordinator.state;


import dev.mdfs.common.FileManifest;
import dev.mdfs.common.ChunkPutRequest;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


    //we also need to be able to have a in memory state holder for the coordinator as 
    //this is what will track ongoing file uploads and finalized manifests 
    //as i outlined in the spex, we will import file manifest, because it describes 
    //a complete file and also chunkputrequest as it gives metadata about a chunk being
    //uploaded like its size and checksum 
    //we use concurrent hashmap which is important since its thread safe as mutliple 
    //http requests can happen concurrently 
    @Component
    public class CoordinatorState {
        //this class is a spring bean that maintains global coordinator memory, 
        //active uploads plus commited manifests, it is not persisted to disk - when 
        //the process restarts - this data is lost 
        //we want to define the final chunk size 
        public static final int CHUNK_SIZE_BYTES = 8 * 1024 * 1024; //equal to 8MB
        //need upload session object that gets created whenever client does POST/FIle to 
        //coordinator 
        public static final class UploadSession {
            public final String uploadID;
            public final Instant createdAt = Instant.now();
            //other field is map of chunkid to chunkPutReq, it is a thread safe map that 
            //records all chunks uploaded so far for this upload session
            public final Map<String, ChunkPutRequest> chunks = new ConcurrentHashMap<>();
            public UploadSession(String uploadID) {
                this.uploadID = uploadID;
            }
        }
        public final Map<String, UploadSession> uploads = new ConcurrentHashMap<>();
        public final Map<String, FileManifest> manifests = new ConcurrentHashMap<>();
    }

