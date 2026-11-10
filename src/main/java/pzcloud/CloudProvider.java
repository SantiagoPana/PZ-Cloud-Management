package pzcloud;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public interface CloudProvider {
    record Remote(String fileId,String name,long bytes,String md5,Map<String,Object> properties) {
        public String get(String key) { return Json.str(properties,key); }
        public String id() { return get("snapshot"); }
        public String toString() { return get("saveName")+" · "+get("mode")+" · "+get("created")+" · "+get("device")+" · "+FilesEx.human(bytes); }
    }
    List<Remote> list() throws IOException;
    Remote upload(ArchiveEngine.Snapshot snapshot) throws IOException;
    Path download(Remote remote,Path directory) throws IOException;
    void delete(Remote remote) throws IOException;
}
