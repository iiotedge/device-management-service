package com.iotmining.services.dms.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal RFC-4180-style CSV support (quoted fields, embedded commas,
 * doubled quotes, CR/LF line endings). Kept dependency-free on purpose.
 */
public final class CsvUtil {

    private CsvUtil() {
    }

    /**
     * Parses CSV text using the first row as the header.
     * Returns one map per data row: header -> raw cell value (trimmed, may be empty).
     */
    public static List<Map<String, String>> parseWithHeader(String content) {
        List<List<String>> rows = parse(content);
        List<Map<String, String>> records = new ArrayList<>();
        if (rows.isEmpty()) {
            return records;
        }

        List<String> header = rows.get(0).stream().map(h -> h.trim().toLowerCase()).toList();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            // Skip fully blank lines
            if (row.stream().allMatch(c -> c == null || c.isBlank())) {
                continue;
            }
            Map<String, String> record = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                record.put(header.get(c), c < row.size() ? row.get(c).trim() : "");
            }
            records.add(record);
        }
        return records;
    }

    static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }

        List<String> current = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);

            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        cell.append('"'); // escaped quote
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else {
                switch (ch) {
                    case '"' -> inQuotes = true;
                    case ',' -> {
                        current.add(cell.toString());
                        cell.setLength(0);
                    }
                    case '\r' -> {
                        // swallow; handled by the following \n (or treated as line end)
                        if (i + 1 >= content.length() || content.charAt(i + 1) != '\n') {
                            current.add(cell.toString());
                            cell.setLength(0);
                            rows.add(current);
                            current = new ArrayList<>();
                        }
                    }
                    case '\n' -> {
                        current.add(cell.toString());
                        cell.setLength(0);
                        rows.add(current);
                        current = new ArrayList<>();
                    }
                    default -> cell.append(ch);
                }
            }
        }

        // trailing cell/row
        if (cell.length() > 0 || !current.isEmpty()) {
            current.add(cell.toString());
            rows.add(current);
        }
        return rows;
    }

    /** Escapes a value for CSV output. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
