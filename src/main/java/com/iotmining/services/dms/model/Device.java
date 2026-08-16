package com.iotmining.services.dms.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import com.iotmining.common.data.devices.DeviceCategory;
import com.iotmining.common.data.devices.DeviceProtocol;
import com.iotmining.common.data.devices.DeviceState;
import com.iotmining.common.data.devices.DeviceType;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@Table(name = "devices")
// Prevent infinite recursion and lazy-loading crashes in toString/Equals
@ToString(exclude = {"customer", "firmware", "software", "assignedCustomers", "labelIds"})
@EqualsAndHashCode(of = "deviceId")
public class Device {

    @Id
    @Column(name = "device_id", updatable = false, nullable = false)
    private UUID deviceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_name")
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private DeviceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type")
    private DeviceType deviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private DeviceState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol")
    private DeviceProtocol protocol;

    @Column(name = "device_token")
    private String deviceToken;

    @Column(name = "location")
    private String location;

    @Column(name = "attributes", columnDefinition = "TEXT")
    @Convert(converter = com.iotmining.common.util.JsonMapConverter.class)
    private Map<String, Object> attributes;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "is_public")
    private boolean isPublic;

    @Column(name = "device_profile_id")
    private UUID deviceProfileId;

    // --- TRUE FOREIGN KEYS ---
//    @ManyToOne(fetch = FetchType.LAZY)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "firmware_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_device_firmware"))
    @NotFound(action = NotFoundAction.IGNORE)
    private Firmware firmware;

//    @ManyToOne(fetch = FetchType.LAZY)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "software_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_device_software"))
    @NotFound(action = NotFoundAction.IGNORE)
    private Software software;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_device_customer"))
    @NotFound(action = NotFoundAction.IGNORE)
    private Customer customer;

    // --- THE ACCESSORS (Shared Sub-Tenants/Users) ---
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "device_assigned_customers", joinColumns = @JoinColumn(name = "device_id"))
    @Column(name = "assigned_customer_id")
    private Set<String> assignedCustomers = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "device_labels", joinColumns = @JoinColumn(name = "device_id"))
    @Column(name = "label_id")
    private Set<UUID> labelIds;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void assignIdIfNull() {
        if (deviceId == null) {
            this.deviceId = UUID.randomUUID();
        }
        if (createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    @PreUpdate
    public void setUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}

//package com.iotmining.services.dms.model;
//
//import jakarta.persistence.*;
//import lombok.Data;
//import com.iotmining.common.data.devices.DeviceCategory;
//import com.iotmining.common.data.devices.DeviceProtocol;
//import com.iotmining.common.data.devices.DeviceState;
//import com.iotmining.common.data.devices.DeviceType;
//
//import java.time.Instant;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;
//import java.util.UUID;
//
//@Data
//@Entity
//@Table(name = "devices")
//public class Device {
//
//    @Id
//    @Column(name = "device_id", updatable = false, nullable = false)
//    private UUID deviceId;
//
//    @Column(name = "tenant_id", nullable = false)
//    private UUID tenantId;
//
//    @Column(name = "device_name")
//    private String deviceName;
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "category")
//    private DeviceCategory category;
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "device_type")
//    private DeviceType deviceType;
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "state")
//    private DeviceState state;
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "protocol")
//    private DeviceProtocol protocol;
//
//    @Column(name = "device_token")
//    private String deviceToken;
//
//    @Column(name = "location")
//    private String location;
//
//    @Column(name = "attributes", columnDefinition = "TEXT")
//    @Convert(converter = com.iotmining.common.util.JsonMapConverter.class)
//    private Map<String, Object> attributes;
//
//    @Column(name = "access_token")
//    private String accessToken;
//
//    @Column(name = "is_public")
//    private boolean isPublic;
//
//    @Column(name = "device_profile_id")
//    private UUID deviceProfileId;
//
//    @Column(name = "firmware_id")
//    private UUID firmwareId;
//
//    @Column(name = "software_id")
//    private UUID softwareId;
//
//    // --- REPLACED customerId WITH assignedCustomers ---
//    // This allows sharing one device across multiple sub-tenants/users
//    @ElementCollection(fetch = FetchType.EAGER)
//    @CollectionTable(name = "device_assigned_customers", joinColumns = @JoinColumn(name = "device_id"))
//    @Column(name = "customer_id")
//    private Set<String> assignedCustomers = new HashSet<>();
//
//    @ElementCollection(fetch = FetchType.EAGER)
//    @CollectionTable(name = "device_labels", joinColumns = @JoinColumn(name = "device_id"))
//    @Column(name = "label_id")
//    private Set<UUID> labelIds;
//
//    @Column(name = "created_at")
//    private Instant createdAt;
//
//    @Column(name = "updated_at")
//    private Instant updatedAt;
//
//    @PrePersist
//    public void assignIdIfNull() {
//        if (deviceId == null) {
//            this.deviceId = UUID.randomUUID();
//        }
//        if (createdAt == null) {
//            this.createdAt = Instant.now();
//        }
//    }
//
//    @PreUpdate
//    public void setUpdatedAt() {
//        this.updatedAt = Instant.now();
//    }
//}
//
////package com.iotmining.services.dms.model;
////
////import jakarta.persistence.*;
////import lombok.Data;
////import com.iotmining.common.data.devices.DeviceCategory;
////import com.iotmining.common.data.devices.DeviceProtocol;
////import com.iotmining.common.data.devices.DeviceState;
////import com.iotmining.common.data.devices.DeviceType;
////import org.hibernate.annotations.Type;
////
////import java.time.Instant;
////import java.util.Map;
////import java.util.Set;
////import java.util.UUID;
////
////@Data
////@Entity
////@Table(name = "devices")
////public class Device {
////
////    @Id
////    @Column(name = "device_id", updatable = false, nullable = false)
////    private UUID deviceId;
////
////    @Column(name = "tenant_id", nullable = false)
////    private UUID tenantId;
////
////    @Column(name = "device_name")
////    private String deviceName;
////
////    @Enumerated(EnumType.STRING)
////    @Column(name = "category")
////    private DeviceCategory category;
////
////    @Enumerated(EnumType.STRING)
////    @Column(name = "device_type")
////    private DeviceType deviceType;
////
////    @Enumerated(EnumType.STRING)
////    @Column(name = "state")
////    private DeviceState state;
////
////    @Enumerated(EnumType.STRING)
////    @Column(name = "protocol")
////    private DeviceProtocol protocol;
////
////    @Column(name = "device_token")
////    private String deviceToken;
////
////    @Column(name = "location")
////    private String location;
////
////    @Column(name = "attributes", columnDefinition = "TEXT")
////    @Convert(converter = com.iotmining.common.util.JsonMapConverter.class)
////    private Map<String, Object> attributes;
////
////    @Column(name = "access_token")
////    private String accessToken;
////
////    @Column(name = "is_public")
////    private boolean isPublic;
////
////    @Column(name = "device_profile_id")
////    private UUID deviceProfileId;
////
////    @Column(name = "firmware_id")
////    private UUID firmwareId;
////
////    @Column(name = "software_id")
////    private UUID softwareId;
////
////    @Column(name = "customer_id")
////    private UUID customerId;
////
////    @ElementCollection(fetch = FetchType.EAGER)
////    @CollectionTable(name = "device_labels", joinColumns = @JoinColumn(name = "device_id"))
////    @Column(name = "label_id")
////    private Set<UUID> labelIds;
////
////    @Column(name = "created_at")
////    private Instant createdAt;
////
////    @Column(name = "updated_at")
////    private Instant updatedAt;
////
////    @PrePersist
////    public void assignIdIfNull() {
////        if (deviceId == null) {
////            this.deviceId = UUID.randomUUID(); // or custom UUID generator
////        }
////    }
////}
