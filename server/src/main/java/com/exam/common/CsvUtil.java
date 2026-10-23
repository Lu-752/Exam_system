package com.exam.common;

import java.util.ArrayList;
import java.util.List;

/** 轻量 CSV 解析：支持双引号包裹、"" 转义、CRLF、UTF-8 BOM。 */
public final class CsvUtil {

    private CsvUtil() {
    }

    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        String src = text.startsWith("\uFEFF") ? text.substring(1) : text;

        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < src.length() && src.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> quoted = true;
                case ',' -> {
                    row.add(cell.toString().trim());
                    cell.setLength(0);
                }
                case '\r' -> {
                    // 忽略，交给 \n 断行
                }
                case '\n' -> {
                    row.add(cell.toString().trim());
                    cell.setLength(0);
                    addRow(rows, row);
                    row = new ArrayList<>();
                }
                default -> cell.append(c);
            }
        }
        row.add(cell.toString().trim());
        addRow(rows, row);
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        if (row.stream().allMatch(String::isEmpty)) {
            return;
        }
        rows.add(new ArrayList<>(row));
    }

    /** 是否为表头行（首个单元格包含关键字） */
    public static boolean isHeader(List<String> row, String... keys) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        String first = row.get(0) == null ? "" : row.get(0);
        for (String k : keys) {
            if (first.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public static String cell(List<String> row, int idx) {
        return idx < row.size() && row.get(idx) != null ? row.get(idx).trim() : "";
    }
}
