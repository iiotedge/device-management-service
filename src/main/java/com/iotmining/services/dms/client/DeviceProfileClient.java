package com.iotmining.services.dms.client;

import com.iotmining.services.dms.configuration.FeignClientInterceptor; // <-- IMPORT
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "device-profile-service",
        url = "${app.services.device-profile.url:'http://localhost:8087'}",
        configuration = FeignClientInterceptor.class
)
public interface DeviceProfileClient {

    @GetMapping("/api/v1/device-profile/{profileId}")
    ResponseEntity<Void> validateProfile(@PathVariable("profileId") UUID profileId);

    @GetMapping("/api/v1/device-profile/{profileId}/resolved")
    Map<String, Object> fetchResolvedProfile(@PathVariable("profileId") UUID profileId);

    @PostMapping("/api/v1/device-profile/batch-resolved")
    Map<UUID, Map<String, Object>> fetchBatchResolvedProfiles(@RequestBody List<UUID> profileIds);
}

//package com.iotmining.services.dms.client;
//
//import org.springframework.cloud.openfeign.FeignClient;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//// If using Eureka: name = "device-profile-service"
//// If testing locally without Eureka: url = "http://localhost:8087"
////@FeignClient(name = "device-profile-service", url = "http://localhost:8087")
//@FeignClient(
//        name = "device-profile-service",
//        url = "${app.services.device-profile.url:'http://localhost:8087'}"
//)
//public interface DeviceProfileClient {
//
//    @GetMapping("/api/v1/device-profile/{profileId}")
//    ResponseEntity<Void> validateProfile(@PathVariable("profileId") UUID profileId);
//
//    @GetMapping("/api/v1/device-profile/{profileId}/resolved")
//    Map<String, Object> fetchResolvedProfile(@PathVariable("profileId") UUID profileId);
//
//    @PostMapping("/api/v1/device-profile/batch-resolved")
//    Map<UUID, Map<String, Object>> fetchBatchResolvedProfiles(@RequestBody List<UUID> profileIds);
//}