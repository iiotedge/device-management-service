package com.iotmining.services.dms.controller;

//import com.iotmining.services.dms.services.DeviceProvisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class DeviceProvisionController {

//    private final DeviceProvisionService provisionService;

    @PostMapping("/provision")
    public ResponseEntity<Void> provision(@RequestBody Map<String, String> payload) {
        String tenantId = payload.get("tenantId");
        String keyspace = payload.get("keyspace");
//        provisionService.createTenantKeyspaceAndTables(keyspace);
        return ResponseEntity.ok().build();
    }
}
