package com.antshorttv.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class OperationsPublicHealthController {
    private final OperationsDatabase database;
    private final String build;
    public OperationsPublicHealthController(OperationsDatabase database,@Value("${operations.build:operations-v1}") String build){this.database=database;this.build=build;}
    @GetMapping("/api/public/operations-health")
    public ResponseEntity<Map<String,String>> health(){
        try{database.jdbc().queryForObject("select 1",Integer.class);return ResponseEntity.ok(Map.of("status","UP","build",build));}
        catch(org.springframework.dao.DataAccessException e){return ResponseEntity.status(503).body(Map.of("status","DOWN","build",build));}
    }
}
