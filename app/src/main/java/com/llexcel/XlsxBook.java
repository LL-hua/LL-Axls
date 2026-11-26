package com.llexcel;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public class XlsxBook {
    public static class Sheet {
        public String name;
        public List<List<String>> rows = new ArrayList<>();
        public List<int[]> merges = new ArrayList<>();
        public Map<Long, String> cache = new HashMap<>(); // [r1, c1, r2, c2]
        public Sheet(String n) { name = n; }
        public void insertRows(int r, int count) {
            if (count <= 0) return;
            int pos = Math.max(0, Math.min(r, rows.size()));
            for (int k = 0; k < count; k++) rows.add(pos, new ArrayList<>());
            for (int[] m : merges) { if (m[0] >= pos) m[0] += count; if (m[2] >= pos) m[2] += count; }
            shiftFormulas(pos, count, -1, 0);
        }
        public void deleteRows(int r, int count) {
            if (count <= 0 || r < 0 || r >= rows.size()) return;
            int del = Math.min(count, rows.size() - r);
            for (int k = 0; k < del; k++) rows.remove(r);
            merges.removeIf(m -> m[0] >= r && m[2] < r + del);
            for (int[] m : merges) {
                if (m[0] >= r + del) m[0] -= del; else if (m[0] > r) m[0] = r;
                if (m[2] >= r + del) m[2] -= del; else if (m[2] >= r) m[2] = Math.max(r - 1, m[0]);
            }
            shiftFormulas(r, -del, -1, 0);
        }
        public void insertCols(int c, int count) {
            if (count <= 0) return;
            for (List<String> row : rows) {
                int pos = Math.max(0, Math.min(c, row.size()));
                for (int k = 0; k < count; k++) row.add(pos, "");
            }
            for (int[] m : merges) { if (m[1] >= c) m[1] += count; if (m[3] >= c) m[3] += count; }
            shiftFormulas(-1, 0, c, count);
        }
        public void deleteCols(int c, int count) {
            if (count <= 0 || c < 0) return;
            for (List<String> row : rows) {
                if (c < row.size()) {
                    int del = Math.min(count, row.size() - c);
                    for (int k = 0; k < del; k++) row.remove(c);
                }
            }
            merges.removeIf(m -> m[1] >= c && m[3] < c + count);
            for (int[] m : merges) {
                if (m[1] >= c + count) m[1] -= count; else if (m[1] > c) m[1] = c;
                if (m[3] >= c + count) m[3] -= count; else if (m[3] >= c) m[3] = Math.max(c - 1, m[1]);
            }
            shiftFormulas(-1, 0, c, -count);
        }
        private void shiftFormulas(int rowAt, int dRow, int colAt, int dCol) {
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile("\\b([A-Z]+)([0-9]+)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
            for (List<String> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    String v = row.get(i);
                    if (v != null && v.startsWith("=")) {
                        java.util.regex.Matcher m = pat.matcher(v);
                        StringBuffer sb = new StringBuffer();
                        while (m.find()) {
                            int cc = colToIndex(m.group(1)), rr = Integer.parseInt(m.group(2)) - 1;
                            if (dRow != 0 && rr >= rowAt) rr = Math.max(0, rr + dRow);
                            if (dCol != 0 && cc >= colAt) cc = Math.max(0, cc + dCol);
                            m.appendReplacement(sb, indexToCol(cc) + (rr + 1));
                        }
                        m.appendTail(sb);
                        row.set(i, sb.toString());
                    }
                }
            }
        }
        public void insertRow(int r) { insertRows(r, 1); }
        public void deleteRow(int r) { deleteRows(r, 1); }
        public void insertCol(int c) { insertCols(c, 1); }
        public void deleteCol(int c) { deleteCols(c, 1); }
        public String get(int r, int col) {
            if (r < 0 || r >= rows.size()) return "";
            List<String> row = rows.get(r);
            return (col < 0 || col >= row.size()) ? "" : row.get(col);
        }
        public void set(int r, int col, String v) {
            while (rows.size() <= r) rows.add(new ArrayList<>());
            List<String> row = rows.get(r);
            while (row.size() <= col) row.add("");
            row.set(col, v == null ? "" : v);
        }
    }
    public List<Sheet> sheets = new ArrayList<>();

    public static int colToIndex(String ref) {
        ref = ref.replace("$", "");
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') col = col * 26 + (ch - 'A' + 1);
            else if (ch >= 'a' && ch <= 'z') col = col * 26 + (ch - 'a' + 1);
            else break;
        }
        return col - 1;
    }
    public static int rowToIndex(String ref) {
        ref = ref.replace("$", "");
        int i = 0; while (i < ref.length() && Character.isLetter(ref.charAt(i))) i++;
        return i < ref.length() ? Integer.parseInt(ref.substring(i)) - 1 : 0;
    }
    public static String indexToCol(int c) {
        StringBuilder sb = new StringBuilder(); c++;
        while (c > 0) { int m = (c - 1) % 26; sb.insert(0, (char)('A' + m)); c = (c - m) / 26; }
        return sb.toString();
    }
    public static XlsxBook load(File file) throws Exception { try (InputStream in = new FileInputStream(file)) { return load(in); } }
    public static XlsxBook load(InputStream in) throws Exception {
        XlsxBook book = new XlsxBook();
        Map<String, byte[]> zipMap = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096]; int n;
                    while ((n = zis.read(buf)) != -1) bos.write(buf, 0, n);
                    zipMap.put(e.getName(), bos.toByteArray());
                }
            }
        }
        List<String> shared = new ArrayList<>();
        if (zipMap.containsKey("xl/sharedStrings.xml")) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new ByteArrayInputStream(zipMap.get("xl/sharedStrings.xml")), "UTF-8");
            StringBuilder sb = null;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG && "si".equals(p.getName())) sb = new StringBuilder();
                else if (ev == XmlPullParser.TEXT && sb != null) sb.append(p.getText());
                else if (ev == XmlPullParser.END_TAG && "si".equals(p.getName()) && sb != null) { shared.add(sb.toString()); sb = null; }
            }
        }
        Map<String, String> rels = new HashMap<>();
        if (zipMap.containsKey("xl/_rels/workbook.xml.rels")) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new ByteArrayInputStream(zipMap.get("xl/_rels/workbook.xml.rels")), "UTF-8");
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG && "Relationship".equals(p.getName())) {
                    String id = p.getAttributeValue(null, "Id"), tgt = p.getAttributeValue(null, "Target");
                    if (id != null && tgt != null) rels.put(id, tgt.startsWith("/") ? tgt.substring(1) : "xl/" + tgt);
                }
            }
        }
        List<String[]> sheetInfos = new ArrayList<>();
        if (zipMap.containsKey("xl/workbook.xml")) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new ByteArrayInputStream(zipMap.get("xl/workbook.xml")), "UTF-8");
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG && "sheet".equals(p.getName())) {
                    String nm = p.getAttributeValue(null, "name"), rid = p.getAttributeValue("http://openxmlformats.org", "id");
                    if (rid == null) rid = p.getAttributeValue(null, "r:id");
                    String path = rels.getOrDefault(rid, "xl/worksheets/sheet" + (sheetInfos.size() + 1) + ".xml");
                    sheetInfos.add(new String[]{nm, path});
                }
            }
        }
        for (String[] info : sheetInfos) {
            Sheet sh = new Sheet(info[0]);
            byte[] xml = zipMap.get(info[1]);
            if (xml != null) parseSheet(xml, shared, sh);
            book.sheets.add(sh);
        }
        if (book.sheets.isEmpty()) book.sheets.add(new Sheet("Sheet1"));
        return book;
    }
    private static void parseSheet(byte[] xml, List<String> shared, Sheet sh) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new ByteArrayInputStream(xml), "UTF-8");
        int r = 0, col = 0;
        String type = null, val = null, fml = null, tag = null;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                tag = p.getName();
                if ("c".equals(tag)) {
                    String ref = p.getAttributeValue(null, "r");
                    if (ref != null) { r = rowToIndex(ref); col = colToIndex(ref); }
                    type = p.getAttributeValue(null, "t");
                    val = null; fml = null;
                } else if ("mergeCell".equals(tag)) {
                    String ref = p.getAttributeValue(null, "ref");
                    if (ref != null && ref.contains(":")) {
                        String[] ab = ref.split(":");
                        sh.merges.add(new int[]{rowToIndex(ab[0]), colToIndex(ab[0]), rowToIndex(ab[1]), colToIndex(ab[1])});
                    }
                }
            } else if (ev == XmlPullParser.TEXT) {
                if ("v".equals(tag)) val = p.getText();
                else if ("f".equals(tag)) fml = p.getText();
                else if ("t".equals(tag) && "inlineStr".equals(type)) val = p.getText();
            } else if (ev == XmlPullParser.END_TAG) {
                if ("c".equals(p.getName())) {
                    String out = "";
                    if (fml != null && !fml.isEmpty()) out = "=" + fml;
                    else if (val != null) {
                        if ("s".equals(type)) {
                            int idx = Integer.parseInt(val.trim());
                            out = (idx >= 0 && idx < shared.size()) ? shared.get(idx) : val;
                        } else out = val;
                    }
                    if (!out.isEmpty()) sh.set(r, col, out);
                }
                tag = null;
            }
        }
    }
    public void save(File file) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            StringBuilder ct = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
            StringBuilder wb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            StringBuilder rels = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for (int i = 0; i < sheets.size(); i++) {
                int id = i + 1;
                ct.append("<Override PartName=\"/xl/worksheets/sheet" + id + ".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                wb.append("<sheet name=\"" + esc(sheets.get(i).name) + "\" sheetId=\"" + id + "\" r:id=\"rId" + id + "\"/>");
                rels.append("<Relationship Id=\"rId" + id + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet" + id + ".xml\"/>");
                putZ(zos, "xl/worksheets/sheet" + id + ".xml", sheetXml(sheets.get(i)));
            }
            ct.append("</Types>"); wb.append("</sheets></workbook>"); rels.append("</Relationships>");
            putZ(zos, "[Content_Types].xml", ct.toString());
            putZ(zos, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            putZ(zos, "xl/workbook.xml", wb.toString());
            putZ(zos, "xl/_rels/workbook.xml.rels", rels.toString());
        }
    }
    private static void putZ(ZipOutputStream zos, String name, String xml) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(xml.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private static String sheetXml(Sheet sh) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < sh.rows.size(); r++) {
            List<String> row = sh.rows.get(r);
            sb.append("<row r=\"" + (r + 1) + "\">");
            for (int c = 0; c < row.size(); c++) {
                String v = row.get(c);
                if (v == null || v.isEmpty()) continue;
                String ref = indexToCol(c) + (r + 1);
                if (v.startsWith("=")) sb.append("<c r=\"" + ref + "\"><f>" + esc(v.substring(1)) + "</f></c>");
                else try { Double.parseDouble(v); sb.append("<c r=\"" + ref + "\"><v>" + v + "</v></c>"); }
                catch (Exception e) { sb.append("<c r=\"" + ref + "\" t=\"inlineStr\"><is><t>" + esc(v) + "</t></is></c>"); }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        if (!sh.merges.isEmpty()) {
            sb.append("<mergeCells count=\"").append(sh.merges.size()).append("\">");
            for (int[] m : sh.merges) {
                sb.append("<mergeCell ref=\"").append(indexToCol(m[1])).append(m[0] + 1)
                  .append(":").append(indexToCol(m[3])).append(m[2] + 1).append("\"/>");
            }
            sb.append("</mergeCells>");
        }
        return sb.append("</worksheet>").toString();
    }
}
