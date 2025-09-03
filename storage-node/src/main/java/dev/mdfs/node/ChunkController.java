//controller with put get wit checksunm and atomic movement - eg no half measures 
package dev.mdfs.node;

import dev.mdfs.common.ChunkId;
import dev.mdfs.common.ChunkPutAck;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;


@RestController
@RequestMapping("/chunks")
public class ChunkController {

    private final Path base;

    //need to grab the base directory ./data and ensure it exists as all chunk files will live underneath this folder
    //storage properties props makes it so spring boot injects a storage properties object with the datadir value populated from config 
    //or default ./data, props.getdatadir() returns that directory path, paths.get... makes a path bobject for that directory and the other 
    //ionbuilt methods clean it up, in short storage properties props is a spring managed configuraton bean that tells the chunkcontroller where 
    //chunks should be stored, this.base is a path object that represents the root directory on disk where all your chunks will be stored 
    //it is derived from whatever value is inside storageproperties.datadir  
    public ChunkController(StorageProperties props) throws IOException {
        this.base = Paths.get(props.getDataDir()).toAbsolutePath().normalize();
        Files.createDirectories(this.base);
    }

    //creating the put endpoint will be chunks/chunk id, body of the request is the chunk bytes we are streaming and 
    //request header is the checksum or in other words the sha 256 of the body requwst 
    @PutMapping("/{chunkId}")
    //put method is returning a response entity which is a spring wrapper for http responses, lets u set status code, 200 ok, 400 bad, 404 nf,
    //? means body could be of any type, in our code sometimes its string error message, chunkputack object for ok acknowledgement, 
    //we have different type return cases so we need this flexibility, method takes chunk id from the url and reads raw http request req 
    //to get both body (bytes of chunk) and headers for enforcing checksum and returns a response entity to tell the client whether uploaded 
    //succeeded or failed, httpservletrequest is the raw servlet request object from the underlying javsa EE layer, we use it because we want 
    //access to the body stream, req.getINputStream(), to read raw bytes of the chunk, access to headers (req.getHeader(x-checksum) for the sha 256
    //check  
    public ResponseEntity<?> put(@PathVariable String chunkId, HttpServletRequest req) throws Exception {
        //first of all lets validate chunk format to be 64 - hex
        try {
            new ChunkId(chunkId);
        } catch(IllegalArgumentException exc) {
            return ResponseEntity.badRequest().body("invalid chunk id");
        }

        //validate the checksum 
        String checkSum = req.getHeader("X-CheckSum");
        if(!StringUtils.hasText(checkSum) || checkSum.length() != 64) {
            return ResponseEntity.badRequest().body("missing and or invalid checksum!");
        }

        //pathfor helper method takes a chunks sha256 id which is a 64 char hex string
        //and uses the first 4 characters to decide which subdirectory to store the file in
        //this is what we can call the final path: base/{first2}/{next2}/{fullchunkid}
        //calls the helper to compute where this chunk should live on disk as finalpath
        //is the exact path of the file that will hold the chunk bytes, it doesnt create
        //the file yet just resolves the location
        Path finalPath = pathFor(chunkId);
        //this line gets the directory part of the path, and ensures that the directory
        //exists and if it doesnt we create it, this is important as when we first write 
        //a chunk the subfolders d4 73 may not exist 
        //we are not using full 64 char hash to make directories we are bucketing by prefix
        //to avoid single huge dirs, the 2 levels of folders a nd b are just buckets, any 
        //chunks whose hashes share the first 4 hex chars will live under the same folder
        Files.createDirectories(finalPath.getParent());

        //creating a new sha256 hasher - md will eat bytes and maintain the running digest
        //messagedigest is a java class used to generate cryptographic hash also called
        //message digest of some input data, telling java to give me a md object 
        //capable od doing sha256 hashing, this line is creating a hashing machine configured
        //to use the sha256 algorithm, later as i feed it bytes of data it will compute the
        //cryptographic fingerprint of that data 
        //following is code to stream body to temp file while hashing 
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        //create temp file on disk in the same folder as final path, file will start
        //with "upload" and end with ".part" this ensures we dont overrite the real 
        //final file until the upload completes successfully 
        //path object pointing to new temp file created by .createtempfile
        Path tmp = Files.createTempFile(finalPath.getParent(), "upload-", ".partial");

        long writtenFileSize; //will hold how many bytes were written, file size of uploaded file
        //req.getinputstream gets the raw byte strewam of http request body (uploaded file data)
        //the next line wraps the raw input stream in a digestinputstream which passes all the bytes
        //through the message digest (md) ensuring that as file bytes streamed in sha256 digest 
        //is being updated on the fly, then we also want to write those bytes to the temp file we created
        //files.newojtputstream tmp, etc opens a stream to write to temp file, truncate existing
        // ensres that if temp file alr exists we will clear it before writing, reads all bytes from 
        //din and writes them to out the temp file, out saves it to disk, md updates sha hash in real time,
        //transfer to out returns total nuimber of bytes copied stored in written 
        try(InputStream in = req.getInputStream();
            DigestInputStream din = new DigestInputStream(in, md);
            OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.TRUNCATE_EXISTING)) {
                writtenFileSize = din.transferTo(out);
        }

        //now verify checksum 
        //md.digest finalizes the computation and returns the raw hash value which is a 
        //byte array, our hex helper that converts each byte into a 2 digit hexadecimal
        //string, the entire 32 byte sha256 becomes a 64 char lowercase hex string 
        String actual = toHex(md.digest());
        if(!actual.equalsIgnoreCase(checkSum)) {
            Files.deleteIfExists(tmp);
            return ResponseEntity.badRequest().body("checksum mismatch");
        }

        //nnow that all checks have been done it is time to fsync temp then atomic move into place
        //fsync is an os level call that flushes all buffered file data and metadata 
        //from memory to disk, calling fsync forces os to push everything down to disk guaraneeing
        //durability, atomic move means all or nothing, either fully succeeds or it fails 
        //without leaving partial stable state, if supported by filesystem it ensures 
        //there will never be half moved file, goes instantly from tmp to finalpath, 
        //if there is a crash we will either have tmp file or fully moved final file but never
        //corruption in between, 

        //analogy for fsync of tmp is like dafting an essay, we work in a temp doc 
        //essay draft.docx only once we are fully done we rename it to essay-final.docx
        //this way no one ever confuses the half written draft with the final version, 
        //even though the files live in the same directory the name matters, tmp means
        //work in progress dont trust me yet and final path means "official, safe to read"
        //writing to tmp file then renaming into place is classic crash safe publishing pattern
        //if we write straight to final path any reader that opens file mid write can see
        //mixed state, also can compute sha256 while streaming into tmp compare to clients 
        //checksum and only publish if it matches, 
        //recommended sequence: create a unique tmp file in same dir as final Path, stream
        //bytes from request to temp file updating messageDigest so sha256 being recomputed,
        //verify size and checksum md.digest vs header then fsync the temp file and atomically
        //rename tmp - final path 
        //File channel is new java class that integrates with os system for better 
        //help with operations like random access, locking, forcing changes to disk
        //the 2 parameters are the path object representing file on disk, and 
        //enum constant telling java i want to openfile for writing 
        //.force ensures that all modified file data and meta data are flushed from
        //memory buffers to physical disk 
        try(FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        //now do the atomic move 
        try{
            Files.move(tmp, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            //replace existing is so if file exists at final path overwrite it and atomic move 
            //is just requesting all or nothing "move from filesystem "
        } catch (AtomicMoveNotSupportedException exception) {
            //fallback if filesytem doesnt support atomic move
            Files.move(tmp, finalPath, StandardCopyOption.REPLACE_EXISTING); 
            //if atomic move doesnt work we try a normal move which is less safe as we 
            //could see transient states but still we are ensuring that the final file is 
            //moved 
        }
        //after put operation we want to return the chunk put acknowledgemnt 
        return ResponseEntity.ok(new ChunkPutAck(true, writtenFileSize));
}

//finished put operation now lets do get 
//get /chunks/{chunkId} - return raw bytes (404 if missing)
//the produces = MediaType.APPLICATION_OCTET_STREAM_VALUE tells spring what content 
//type the method will produce in its response, when someone does a get request to 
//chunks/chunkid call this method and the response will be binary bytes (file content)
//effectively this is the endpoint to retrieve a strored chunk from the dfs node 

@GetMapping(value = "/{chunkId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
//Resource is a spring abstraction form it can represent a file on disk etc and it gives 
//flexibility, the controller doesnt have to hardcode the file, its just says here is a 
//resource, responseENtity<Resource> lets you return both the binary content and http 
//metadata which are headers like content length, content type etc, when return 
//responseEntitty<resource> we are returning a full http response that contains raw 
//bytes which is the http response body, headers like content type, content length etc 
//and a status code like 200 for ok or 404 not found 
public ResponseEntity<Resource> get(String chunkId) throws IOException {
    //confirm the chunkId is valid 
    try {
        new ChunkId(chunkId);
    } catch(IllegalArgumentException exc) {
        return ResponseEntity.badRequest().build();
    }
    Path fileToGetPath = pathFor(chunkId);
    Resource file = new FileSystemResource(fileToGetPath);
    return ResponseEntity.ok(file);
}

//pathfor and tohex helper methods 
//first subfolder is the first 2 characters of chunk id and second is the 3 and 4 chars 
//we want to return a path 
private Path pathFor(String chunkId) {
    String a = chunkId.substring(0, 2);
    String b = chunkId. substring(2, 4);
    //append child path onto parent path, it needs to be a continutation of the base path
    //like .data/eg/b4/chunkid......
    return base.resolve(Paths.get(a, b, chunkId));
}

//tohex helper method
//this method converts each byte into 2 hex characters using bitwise operations and a lookup table, turning a byte aray which is a sha 256 hash into a readable hex string
//input is a narray of bytes from the hash function, each byte is represneted by 2 hex characters so for n bytes we need 2n characters  
private static String toHex(byte[] bytes) {
    char[] hexArr = new char[bytes.length * 2];
    //define the possible hex characters in another char[]
    char[] digits = "0123456789abcdef".toCharArray();
    //dont understand bitwise operations yet so am taking the rest of this method from chat gpt, will read up on them soon 
    for (int i = 0, j = 0; i < bytes.length; i++) {
    int v = bytes[i] & 0xFF;
    hexArr[j++] = digits[v >>> 4];
    hexArr[j++] = digits[v & 0x0F];
    }
    return new String(hexArr);
}
}
