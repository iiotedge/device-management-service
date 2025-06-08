//package com.iotmining.services.dms.services;
//
//import com.datastax.oss.driver.api.core.CqlSession;
//
//import com.iotmining.services.dms.ddl.DDLGenerator;
//import com.iotmining.services.dms.model.Device;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class DeviceProvisionService {
//
//    private final CqlSession session;
//
//    private boolean keyspaceExists(String keyspace) {
//        return session.getMetadata()
//                .getKeyspace(keyspace)
//                .isPresent();
//    }
//
//    /**
//     * Creates the tenant-specific keyspace and necessary tables.
//     * This method is triggered when a new tenant is onboarded.
//     *
//     * @param keyspace The keyspace name to create (e.g., companyx_ks)
//     */
//    public void createTenantKeyspaceAndTables(String keyspace) {
//        try {
//
//            if (keyspaceExists(keyspace)) {
//                log.warn("⚠️ Keyspace '{}' already exists. Skipping provisioning to avoid overwriting existing tenant data.", keyspace);
//                return;
//            }
//
//            // Step 1: Create keyspace
//            String keyspaceCql = String.format("""
//                CREATE KEYSPACE IF NOT EXISTS %s
//                WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
//            """, keyspace);
//            session.execute(keyspaceCql);
//            log.info("✅ Created keyspace: {}", keyspace);
//
//            // Step 2: Create devices table dynamically from model
//            String devicesTableCql = DDLGenerator.generateCreateTableCQL(Device.class, keyspace, "devices");
//            session.execute(devicesTableCql);
//            log.info("✅ Created devices table in keyspace: {}", keyspace);
//
//            // Step 3: Add more tables (e.g., telemetry) if needed
//            // String telemetryTableCql = DDLGenerator.generateCreateTableCQL(...);
//            // session.execute(telemetryTableCql);
//            // log.info("✅ Created telemetry table for keyspace: {}", keyspace);
//
//            log.info("🎯 Tenant schema provisioning completed successfully for keyspace: {}", keyspace);
//
//        } catch (Exception e) {
//            log.error("❌ Failed to provision schema for tenant keyspace '{}'", keyspace, e);
//            throw new RuntimeException("Schema provisioning failed for keyspace: " + keyspace, e);
//        }
//    }
//}
