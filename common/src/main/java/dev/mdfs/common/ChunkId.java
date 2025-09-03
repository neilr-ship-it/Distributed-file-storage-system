// essentially a tiny wrapper around a raw string that represents the unique id of 
    //a chunk (64 char SHA 256 hex string), instead of passing plains strings all over 
    //code base we are creating a domain specific type called chunkID, using string 
    //is risky as we may accidentally pass the wrong string type somewhere like filename
    //when chunk id was expected 

package dev.mdfs.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class ChunkID {
    private final String value; //64 char lowercase hex (sha256)

    @JsonCreator //lets jackson create a chunkID from a JSON string when deserialziing
    //requests and responses, json value tells jackson to serialize chunkID back to 
    //string value in JSON 
    public ChunkID(String value) {
        if(value == null || value.length() != 64) {
            throw new IllegalArgumentException();
        }
        this.value = value.toLowerCase();
        //validate hex chars
        for(int i = 0; i < 64; i++) {
            char currChar = this.value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new IllegalArgumentException("chunk id must be hex format");
            }
        }
    }

    // tells json serializer jackson that when converting chunkID object to json it 
    //should just be represented as the string inside 
    @JsonValue
    @Override
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    //makes sure chunkid objects work well in hash based collections
    //if 2 chunkids wrap rthe same srting they will compute the same hash code 
    //and end up in the same bucket
    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceOf ChunkID c) && c.value.equals(this.value);
    }
}
