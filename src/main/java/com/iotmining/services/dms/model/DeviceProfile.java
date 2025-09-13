//package com.iotmining.services.dms.model;
//
//import jakarta.persistence.*;
//import lombok.Data;
//
//import java.util.UUID;
//
//@Data
//@Entity
//@Table(name = "device_profiles")
//public class DeviceProfile {
//
//    @Id
//    @Column(name = "id", nullable = false, updatable = false)
//    private UUID id;
//
//    @Column(name = "tenant_id", nullable = false)
//    private UUID tenantId;
//
//    @Column(name = "profile_name")
//    private String profileName;
//
//    @Column(name = "transport_type")
//    private String transportType;
//
//    @Column(name = "default_rule_chain_id")
//    private String defaultRuleChainId;
//
//    @Column(name = "alarm_rules_json", columnDefinition = "TEXT")
//    private String alarmRulesJson;
//
//    @Column(name = "provisioning_config_json", columnDefinition = "TEXT")
//    private String provisioningConfigJson;
//}
