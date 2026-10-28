package com.antshorttv.downloadcenter;

import com.antshorttv.storage.ObjectStorageService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Service;

@Service
public class DownloadArchiveService {
    private final ObjectStorageService storage;
    public DownloadArchiveService(ObjectStorageService storage){this.storage=storage;}
    public long generate(String key,List<DownloadSourceResolver.File> files,Runnable heartbeat) {
        Path temporary=null;
        try {
            temporary=temporary(key.substring(key.lastIndexOf('/')+1).replace(".zip",""));
            Files.createDirectories(temporary.getParent());
            try(var zip=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)),StandardCharsets.UTF_8)) {
                Set<String> used=new HashSet<>();byte[] buffer=new byte[64*1024];
                for(var file:files) {
                    heartbeat.run();zip.putNextEntry(new ZipEntry(uniqueName(file.name(),used)));
                    try(var input=file.content().open()) {
                        int length;long nextHeartbeat=System.nanoTime();
                        while((length=input.read(buffer))!=-1) {
                            if(Thread.currentThread().isInterrupted())throw new IOException("下载生成已中断");
                            zip.write(buffer,0,length);
                            if(System.nanoTime()>=nextHeartbeat){heartbeat.run();nextHeartbeat=System.nanoTime()+30_000_000_000L;}
                        }
                    }
                    zip.closeEntry();
                }
            }
            heartbeat.run();storage.uploadFile(key,temporary,"application/zip");heartbeat.run();
            return Files.size(temporary);
        }catch(IOException ex){throw new IllegalStateException("压缩包生成失败",ex);}
        finally {
            if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ex){
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Unable to remove temporary download archive {}",temporary,ex);
            }
        }
    }
    private static Path temporary(String id) {
        UUID.fromString(id);
        return Path.of(System.getProperty("java.io.tmpdir"),"antv-download-center",id+".zip.part");
    }
    public void deleteTemporary(String id) throws IOException {Files.deleteIfExists(temporary(id));}
    static String uniqueName(String requested,Set<String> used) {
        String base=requested==null?"文件":requested.replaceAll("[\\\\/\\p{Cntrl}:*?\"<>|]","_").strip();
        if(base.isBlank() || base.equals(".") || base.equals(".."))base="文件";
        if(base.length()>180)base=base.substring(0,180);
        String name=base;int number=2;int dot=base.lastIndexOf('.');
        while(!used.add(name.toLowerCase(Locale.ROOT))) {
            name=dot>0?base.substring(0,dot)+" ("+number+++")"+base.substring(dot):base+" ("+number+++")";
        }
        return name;
    }
}
