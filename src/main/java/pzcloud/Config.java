package pzcloud;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class Config {
    public final Path home;
    private final Map<String,Object> data;
    public Config(Path home) throws IOException {
        this.home=home.toAbsolutePath().normalize(); Files.createDirectories(this.home);
        data=Files.exists(home.resolve("config.json")) ? FilesEx.readJson(home.resolve("config.json")) : new LinkedHashMap<>();
        String before=Json.encode(data); boolean publisherReady=false;
        try { var defaultClient=GoogleClientDefaults.load(); if(defaultClient.containsKey("installed")) publisherReady=Json.str(Json.object(defaultClient.get("installed")),"client_id").endsWith(".apps.googleusercontent.com"); } catch(IOException ignored) { /* The login window reports publisher setup, local backup still works. */ }
        data.putIfAbsent("cloudMode",Files.exists(home.resolve("google-client.json"))||publisherReady?"drive":"none");
        data.putIfAbsent("syncFolder","");
        data.putIfAbsent("setupComplete",Files.exists(home.resolve("google-client.json")));
        data.putIfAbsent("zomboid", Path.of(System.getProperty("user.home"),"Zomboid").toString());
        data.putIfAbsent("deviceId",UUID.randomUUID().toString());
        data.putIfAbsent("deviceName",System.getenv().getOrDefault("COMPUTERNAME",System.getenv().getOrDefault("HOSTNAME","Mi PC")));
        data.putIfAbsent("worlds",new LinkedHashMap<String,Object>());
        data.putIfAbsent("gameBuild", "42.22.1");
        if(!before.equals(Json.encode(data))) save();
    }
    public static Path defaultHome() {
        String override=System.getProperty("pzcloud.home");
        if(override!=null) return Path.of(override);
        String appdata=System.getenv("LOCALAPPDATA");
        return appdata==null ? Path.of(System.getProperty("user.home"),".pzcloud") : Path.of(appdata,"PZCloudManagement");
    }
    public synchronized String get(String key) { return Json.str(data,key); }
    public synchronized void set(String key,String value) throws IOException { update(Map.of(key,value)); }
    public synchronized void update(Map<String,Object> changes) throws IOException {
        Map<String,Object> next=new LinkedHashMap<>(data); next.putAll(changes);
        if(next.equals(data)) return;
        FilesEx.json(home.resolve("config.json"),next); data.putAll(changes);
    }
    public synchronized boolean automaticExits() {
        try { for(Object value:Json.object(data.get("worlds")).values()) {
            var w=Json.object(value); if(Json.str(w,"automatic").equals("true")&&!Json.str(w,"trigger").equals("before_enter")) return true;
        } } catch(IOException ignored) { return false; }
        return false;
    }
    public Path zomboid() { return Path.of(get("zomboid")).toAbsolutePath().normalize(); }
    public Path saves() { return zomboid().resolve("Saves"); }
    public Path archives() { return home.resolve("backups"); }
    public synchronized void save() throws IOException { FilesEx.json(home.resolve("config.json"),data); }
    public synchronized World world(String mode, String name) throws IOException {
        FilesEx.safeName(mode); FilesEx.safeName(name);
        Map<String,Object> worlds=Json.object(data.get("worlds"));
        String key=mode+"/"+name;
        if(!worlds.containsKey(key)) {
            Map<String,Object> w=new LinkedHashMap<>();
            w.put("id",UUID.randomUUID().toString()); w.put("automatic",false); w.put("trigger","after_exit");
            w.put("destination","both"); w.put("retention",5L); w.put("head","");
            worlds.put(key,w); save();
        }
        return new World(mode,name,Json.object(worlds.get(key)));
    }
    public final class World {
        public final String mode,name; private final Map<String,Object> info;
        World(String mode,String name,Map<String,Object> info) { this.mode=mode; this.name=name; this.info=info; }
        public Path path() { return saves().resolve(mode).resolve(name); }
        public String key() { return mode+"/"+name; }
        public String get(String k) { synchronized(Config.this) { return Json.str(info,k); } }
        public boolean automatic() { return get("automatic").equals("true"); }
        public String id() { return get("id"); }
        public int retention() { try { return Math.max(1,Integer.parseInt(get("retention"))); } catch(NumberFormatException e) { return 10; } }
        public void set(String k,Object v) throws IOException { update(Map.of(k,v)); }
        public void update(Map<String,Object> changes) throws IOException { synchronized(Config.this) {
            Map<String,Object> old=new LinkedHashMap<>(info); if(changes.entrySet().stream().allMatch(e->Objects.equals(info.get(e.getKey()),e.getValue()))) return;
            info.putAll(changes);
            try { save(); } catch(IOException ex) { info.clear(); info.putAll(old); throw ex; }
        } }
        public String toString() { return name+"  ·  "+mode; }
    }
}
