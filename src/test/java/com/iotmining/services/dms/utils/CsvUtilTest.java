package com.iotmining.services.dms.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUtilTest {

    @Test
    void parsesHeaderAndRows() {
        List<Map<String, String>> rows = CsvUtil.parseWithHeader(
                "deviceName,protocol\nsensor-1,MQTT\nsensor-2,HTTP\n");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("devicename", "sensor-1").containsEntry("protocol", "MQTT");
        assertThat(rows.get(1)).containsEntry("devicename", "sensor-2").containsEntry("protocol", "HTTP");
    }

    @Test
    void headerIsCaseInsensitive() {
        List<Map<String, String>> rows = CsvUtil.parseWithHeader("DeviceName\nx\n");
        assertThat(rows.get(0)).containsKey("devicename");
    }

    @Test
    void handlesQuotedFieldsWithCommasAndEscapedQuotes() {
        List<Map<String, String>> rows = CsvUtil.parseWithHeader(
                "name,attributes\n\"Pump, Main\",\"{\"\"floor\"\":1}\"\n");

        assertThat(rows.get(0)).containsEntry("name", "Pump, Main");
        assertThat(rows.get(0)).containsEntry("attributes", "{\"floor\":1}");
    }

    @Test
    void handlesCrLfLineEndings() {
        List<Map<String, String>> rows = CsvUtil.parseWithHeader("name\r\na\r\nb\r\n");
        assertThat(rows).extracting(r -> r.get("name")).containsExactly("a", "b");
    }

    @Test
    void skipsBlankLines() {
        List<Map<String, String>> rows = CsvUtil.parseWithHeader("name\na\n\n\nb\n");
        assertThat(rows).hasSize(2);
    }

    @Test
    void missingTrailingCellsBecomeEmptyStrings() {
        List<Map<String, String>> rows = CsvUtil.parseWithHeader("a,b,c\n1,2\n");
        assertThat(rows.get(0)).containsEntry("c", "");
    }

    @Test
    void emptyContentYieldsNoRecords() {
        assertThat(CsvUtil.parseWithHeader("")).isEmpty();
        assertThat(CsvUtil.parseWithHeader(null)).isEmpty();
    }

    @Test
    void escapeQuotesOnlyWhenNeeded() {
        assertThat(CsvUtil.escape("plain")).isEqualTo("plain");
        assertThat(CsvUtil.escape(null)).isEmpty();
        assertThat(CsvUtil.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvUtil.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvUtil.escape("line\nbreak")).isEqualTo("\"line\nbreak\"");
    }
}
