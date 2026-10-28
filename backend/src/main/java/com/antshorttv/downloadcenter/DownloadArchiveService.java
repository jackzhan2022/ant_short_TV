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
            if(key.endsWith(".file") && files.size()==1 && files.get(0).objectKey()!=null) {
                heartbeat.run();var copied=storage.copyForDownload(files.get(0).objectKey(),key);heartbeat.run();return copied.size();
            }
            String leaf=key.substring(key.lastIndexOf('/')+1);
            temporary=temporary(leaf.substring(0,leaf.lastIndexOf('.')));
            Files.createDirectories(temporary.getParent());
            if(key.endsWith(".file")) {
                if(files.size()!=1)throw new IllegalArgumentException("单文件任务必须恰好包含一个文件");
                try(var output=Files.newOutputStream(temporary);var input=files.get(0).content().open()) {input.transferTo(output);}
                heartbeat.run();storage.uploadFile(key,temporary,files.get(0).contentType());heartbeat.run();
                return Files.size(temporary);
            }
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
        }catch(IOException ex){throw new IllegalStateException("下载文件生成失败",ex);}
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
        String base=requested==null?"文件":requested.replaceAll("[\\\\/\\p{Cntrl}\\p{Cf}:*?\"<>|]","_").replaceAll("\\s+"," ").strip().replaceAll("^[. ]+|[. ]+$","");
        if(base.isBlank())base="文件";
        int dot=base.lastIndexOf('.');String ext=dot>0&&base.substring(dot).matches("[.][A-Za-z0-9]{1,10}")?base.substring(dot):"";
        String stem=ext.isEmpty()?base:base.substring(0,dot);
        if(stem.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])"))stem="_"+stem;
        for(int n=1;;n++) {
            String suffix=(n==1?"":" ("+n+")")+ext;
            String name=truncate(stem,240-suffix.getBytes(StandardCharsets.UTF_8).length)+suffix;
            if(used.add(name.toLowerCase(Locale.ROOT)))return name;
        }
    }
    private static String truncate(String value,int maxBytes) {
        StringBuilder result=new StringBuilder();int bytes=0;
        for(int cp:value.codePoints().toArray()) {
            String unit=new String(Character.toChars(cp));int length=unit.getBytes(StandardCharsets.UTF_8).length;
            if(bytes+length>maxBytes)break;result.append(unit);bytes+=length;
        }
        return result.toString();
    }
}
