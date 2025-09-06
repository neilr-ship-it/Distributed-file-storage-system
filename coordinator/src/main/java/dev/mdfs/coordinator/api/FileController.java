package dev.mdfs.coordinator.api;

import dev.mdfs.common.ChunkId;
import dev.mdfs.common.ChunkPutRequest;
import dev.mdfs.common.ChunkRef;
import dev.mdfs.common.FileManifest;
import dev.mdfs.coordinator.cluster.NodeRegistry;
import dev.mdfs.coordinator.dto.CommitRequest;
import dev.mdfs.coordinator.dto.CommitResponse;
import dev.mdfs.coordinator.dto.CreateFileResponse;
import dev.mdfs.coordinator.dto.ChunkPlacementResponse;
import dev.mdfs.coordinator.state.CoordinatorState;
import dev.mdfs.coordinator.state.CoordinatorState.UploadSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/files")
public class FileController {

    private final CoordinatorState state;
    private final NodeRegistry registry;
    private final int replicas =
            Integer.parseInt(System.getenv().getOrDefault("REPLICAS", "1"));

    public FileController(CoordinatorState state, NodeRegistry registry) {
        this.state = state;
        this.registry = registry;
    }

    // POST /files -> { uploadId, chunkSize }
    @PostMapping
    public CreateFileResponse createFile() {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        state.uploads.put(uploadId, new UploadSession(uploadId));
        return new CreateFileResponse(uploadId, CoordinatorState.CHUNK_SIZE_BYTES);
    }

    // POST /files/{uploadId}/chunks  (body: ChunkPutRequest)
    @PostMapping("/{uploadId}/chunks")
    public ResponseEntity<?> placeChunk(@PathVariable String uploadId,
                                        @RequestBody ChunkPutRequest req) {
        UploadSession sess = state.uploads.get(uploadId);
        if (sess == null) return ResponseEntity.notFound().build();

        // remember metadata for commit
        sess.chunks.put(req.chunkId(), req);

        // choose replicas deterministically by chunkId
        List<URI> nodes = registry.chooseReplicas(Math.max(1, replicas), req.chunkId());
        List<String> urls = nodes.stream()
                .map(u -> u.resolve("/chunks/" + req.chunkId()).toString())
                .collect(Collectors.toList());

        return ResponseEntity.ok(new ChunkPlacementResponse(urls, urls.size()));
    }

    // POST /files/{uploadId}/commit  (body: optional CommitRequest)
    @PostMapping("/{uploadId}/commit")
    public ResponseEntity<?> commit(@PathVariable String uploadId,
                                    @RequestBody(required = false) CommitRequest body) {
        UploadSession sess = state.uploads.get(uploadId);
        if (sess == null) return ResponseEntity.notFound().build();

        // determine chunk order
        List<String> order;
        if (body != null && body.chunkOrder() != null && !body.chunkOrder().isEmpty()) {
            order = body.chunkOrder();
        } else {
            order = new ArrayList<>(sess.chunks.keySet());
            Collections.sort(order); // deterministic default
        }
        if (order.isEmpty()) {
            return ResponseEntity.badRequest().body("no chunks in session");
        }

        // build refs
        List<ChunkRef> refs = new ArrayList<>(order.size());
        for (int i = 0; i < order.size(); i++) {
            String cidHex = order.get(i);
            ChunkPutRequest meta = sess.chunks.get(cidHex);
            if (meta == null) {
                return ResponseEntity.badRequest().body("missing metadata for chunk " + cidHex);
            }
            List<String> repl = registry
                    .chooseReplicas(Math.max(1, replicas), cidHex)
                    .stream().map(URI::toString).toList();

            ChunkId cid = new ChunkId(cidHex); // or ChunkId.fromHex if you actually have it
            refs.add(new ChunkRef(cid, meta.checkSum(), meta.size(), repl, i));
        }

        long totalSize = refs.stream().mapToLong(ChunkRef::size).sum();
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String fileName = (body != null ? body.fileName() : null);
        long createdAtMillis = System.currentTimeMillis();

        FileManifest manifest = new FileManifest(
                fileId, fileName, totalSize,
                CoordinatorState.CHUNK_SIZE_BYTES, 1,
                refs, createdAtMillis
        );

        state.manifests.put(fileId, manifest);
        state.uploads.remove(uploadId);

        return ResponseEntity.ok(new CommitResponse(fileId, 1));
    }
}
