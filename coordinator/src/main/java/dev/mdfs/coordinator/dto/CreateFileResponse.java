package dev.mdfs.coordinator.dto;

    //these dtos are data transfer objects that define the json 
    //that our coordinator api sends/recieves, they are java records which are immutable 
    //data carriers, a record automatically gives u a constructor, getters and equals/
    //hashcode/tostring and its implicitly final hence immutable, spring boot via jackson
    //serializes/deserialzes them to and from json using component names
    //client will get this record back when coordinator returns 200 ok request 
    //after POST/fiels to create upload 
    public record CreateFileResponse(String uploadId, int chunkSize) {
        //in Json would look like this: 
        //{ "uploadId": "u-91f0...", "chunkSize": 8388608 }
    }


