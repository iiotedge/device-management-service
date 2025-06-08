//package com.iotmining.services.dms.ddl;
//
//
//import org.springframework.data.cassandra.core.mapping.PrimaryKey;
//import org.springframework.data.cassandra.core.mapping.Table;
//
//import java.lang.reflect.Field;
//import java.lang.reflect.ParameterizedType;
//import java.lang.reflect.Type;
//import java.util.Set;
//import java.util.UUID;
//
//public class DDLGenerator {
//
//    public static String generateCreateTableCQL(Class<?> clazz, String keyspace, String tableName) {
//        if (!clazz.isAnnotationPresent(Table.class)) {
//            throw new IllegalArgumentException("Class " + clazz.getSimpleName() + " must be annotated with @Table");
//        }
//
//        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
//                .append(keyspace).append(".").append(tableName).append(" (");
//
//        String primaryKey = null;
//
//        for (Field field : clazz.getDeclaredFields()) {
//            String columnName = field.getName();
//            String cassandraType = mapJavaTypeToCassandra(field);
//
//            if (field.isAnnotationPresent(PrimaryKey.class)) {
//                primaryKey = columnName;
//            }
//
//            ddl.append(columnName).append(" ").append(cassandraType).append(", ");
//        }
//
//        if (primaryKey == null) {
//            throw new IllegalStateException("Primary key not found in class " + clazz.getSimpleName());
//        }
//
//        ddl.append("PRIMARY KEY (").append(primaryKey).append("));");
//        return ddl.toString();
//    }
//
//    private static String mapJavaTypeToCassandra(Field field) {
//        Class<?> type = field.getType();
//
//        if (UUID.class.isAssignableFrom(type)) return "uuid";
//        if (String.class.isAssignableFrom(type)) return "text";
//        if (Boolean.class.isAssignableFrom(type) || type.equals(boolean.class)) return "boolean";
//        if (Integer.class.isAssignableFrom(type) || type.equals(int.class)) return "int";
//        if (Long.class.isAssignableFrom(type) || type.equals(long.class)) return "bigint";
//        if (Double.class.isAssignableFrom(type) || type.equals(double.class)) return "double";
//        if (Float.class.isAssignableFrom(type) || type.equals(float.class)) return "float";
//        if (Set.class.isAssignableFrom(type)) {
//            return detectSetType(field);
//        }
//        if (Enum.class.isAssignableFrom(type) || type.isEnum()) return "text";
//        if (type.getName().equals("java.time.Instant")) return "timestamp";
//
//        // Default fallback
//        return "text";
//    }
//
//    private static String detectSetType(Field field) {
//        Type genericType = field.getGenericType();
//        if (genericType instanceof ParameterizedType) {
//            ParameterizedType pt = (ParameterizedType) genericType;
//            Type[] fieldArgTypes = pt.getActualTypeArguments();
//            if (fieldArgTypes.length > 0) {
//                String typeName = fieldArgTypes[0].getTypeName();
//                if (typeName.equals(UUID.class.getName())) {
//                    return "set<uuid>";
//                } else if (typeName.equals(String.class.getName())) {
//                    return "set<text>";
//                } else {
//                    return "set<text>"; // default inside set
//                }
//            }
//        }
//        return "set<text>"; // fallback
//    }
//}
