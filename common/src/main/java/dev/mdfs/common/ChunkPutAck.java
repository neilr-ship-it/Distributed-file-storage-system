package dev.mdfs.common;
//what the node replies after a succesfful write, to let us know that the write was
//completed properly 
public record ChunkPutAck(boolean ok, long storedSize){

} //boolean flag and value of size of file once stored, this record is the 
//acknowledgement that a storage node sends back to the client after a chunk upload 
//attempt, ok, did the upload succeed or fail - true/false, storedSize, how many bytes
//the node actually wrote to the disk, this gives client immediae confirmation that the 
//chunks is safely written, if ok is false client/coord can retry and redirect to another node
//stores size should equal orginal size in the chunkPutRequest, its the receipt from 
//the storage node after put
    


