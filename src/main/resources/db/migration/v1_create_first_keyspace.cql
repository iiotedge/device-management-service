CREATE KEYSPACE IF NOT EXISTS dms_data
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

DESCRIBE KEYSPACES;
