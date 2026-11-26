package com.llexcel;
import java.util.*;
import java.util.regex.*;

public class FormulaEngine {
    private static final Pattern RANGE_P = Pattern.compile("(SUM|AVERAGE|AVG|MIN|MAX|COUNT)\\(([A-Z0-9:,]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL_P = Pattern.compile("\\b([A-Z]{1,3})([0-9]{1,5})\\b", Pattern.CASE_INSENSITIVE);

    public static String evaluate(String raw, XlsxBook.Sheet sheet) { return evaluate(raw, sheet, null); }
    public static String evaluate(String raw, XlsxBook.Sheet sheet, XlsxBook book) { return evalInternal(raw, sheet, book, new HashSet<>()); }

    private static XlsxBook.Sheet findSheet(String name, XlsxBook.Sheet cur, XlsxBook book) {
        if (name == null || name.isEmpty() || book == null) return cur;
        name = name.replace("'", "").trim();
        for (XlsxBook.Sheet s : book.sheets) if (s.name != null && s.name.equalsIgnoreCase(name)) return s;
        return cur;
    }

    private static List<String> splitArgs(String s) {
        List<String> res = new ArrayList<>();
        int d = 0, last = 0; boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') q = !q;
            else if (!q) {
                if (ch == '(') d++; else if (ch == ')') d--;
                else if (ch == ',' && d == 0) { res.add(s.substring(last, i).trim()); last = i + 1; }
            }
        }
        res.add(s.substring(last).trim());
        return res;
    }

    private static String getCellStr(XlsxBook.Sheet sh, int r, int col, XlsxBook book, Set<String> vis) {
        if (sh == null || r < 0 || col < 0) return "";
        long ck = (((long) r) << 32) | (col & 0xffffffffL);
        if (sh.cache.containsKey(ck)) return sh.cache.get(ck);
        return evalInternal(sh.get(r, col), sh, book, vis);
    }
    private static String evalVlookup(String inner, XlsxBook.Sheet sheet, XlsxBook book, Set<String> vis) {
        List<String> a = splitArgs(inner);
        if (a.size() < 3) return "#ERR";
        String key = evalInternal("=" + a.get(0), sheet, book, vis);
        String rng = a.get(1).replace("$", "").trim();
        int colIdx = (int) Double.parseDouble(evalInternal("=" + a.get(2), sheet, book, vis));
        boolean exact = a.size() < 4 || !a.get(3).equalsIgnoreCase("TRUE") && !a.get(3).equals("1");
        int bang = rng.indexOf('!');
        XlsxBook.Sheet tSh = (bang >= 0) ? findSheet(rng.substring(0, bang), sheet, book) : sheet;
        String area = (bang >= 0) ? rng.substring(bang + 1) : rng;
        String[] ab = area.split(":");
        int c1 = XlsxBook.colToIndex(ab[0]);
        int r1 = ab[0].matches(".*[0-9]+") ? XlsxBook.rowToIndex(ab[0]) : 0;
        int r2 = (ab.length > 1 && ab[1].matches(".*[0-9]+")) ? XlsxBook.rowToIndex(ab[1]) : (tSh.rows.size() - 1);
        r2 = Math.min(r2, tSh.rows.size() - 1);
        Double kNum = null; try { kNum = Double.parseDouble(key); } catch (Exception ignored) {}
        String best = "#N/A";
        for (int r = r1; r <= r2; r++) {
            String cv = getCellStr(tSh, r, c1, book, vis);
            if (cv.isEmpty()) continue;
            if (cv.equalsIgnoreCase(key)) return getCellStr(tSh, r, c1 + colIdx - 1, book, vis);
            if (kNum != null) {
                try {
                    double d = Double.parseDouble(cv);
                    if (Math.abs(d - kNum) < 1e-6) return getCellStr(tSh, r, c1 + colIdx - 1, book, vis);
                    if (!exact && d <= kNum) best = getCellStr(tSh, r, c1 + colIdx - 1, book, vis);
                } catch (Exception ignored) {}
            }
        }
        return best;
    }

    private static String evalIf(String inner, XlsxBook.Sheet sheet, XlsxBook book, Set<String> vis) {
        List<String> a = splitArgs(inner);
        if (a.size() < 2) return "#ERR";
        String cond = a.get(0); boolean ok = false;
        for (String op : new String[]{"<>", "!=", ">=", "<=", "=", ">", "<"}) {
            int idx = cond.indexOf(op);
            if (idx > 0) {
                String ls = cond.substring(0, idx).trim(), rs = cond.substring(idx + op.length()).trim();
                String lv = ls.equals("\"\"") ? "" : (ls.startsWith("\"") ? ls.replace("\"", "") : evalInternal("=" + ls, sheet, book, vis));
                String rv = rs.equals("\"\"") ? "" : (rs.startsWith("\"") ? rs.replace("\"", "") : evalInternal("=" + rs, sheet, book, vis));
                if (op.equals("=")) ok = lv.equalsIgnoreCase(rv);
                else if (op.equals("<>") || op.equals("!=")) ok = !lv.equalsIgnoreCase(rv);
                else {
                    double ld = Double.parseDouble(lv), rd = Double.parseDouble(rv);
                    if (op.equals(">=")) ok = ld >= rd; else if (op.equals("<=")) ok = ld <= rd;
                    else if (op.equals(">")) ok = ld > rd; else if (op.equals("<")) ok = ld < rd;
                }
                break;
            }
        }
        String br = ok ? a.get(1) : (a.size() >= 3 ? a.get(2) : "0");
        return br.equals("\"\"") ? "" : (br.startsWith("\"") ? br.replace("\"", "") : evalInternal("=" + br, sheet, book, vis));
    }
    private static String replaceCall(String expr, String fnName, boolean isVlookup, XlsxBook.Sheet sh, XlsxBook book, Set<String> vis) {
        int from = 0;
        while (true) {
            int idx = expr.toUpperCase(Locale.US).indexOf(fnName + "(", from);
            if (idx < 0) break;
            if (idx > 0 && Character.isLetterOrDigit(expr.charAt(idx - 1))) { from = idx + 1; continue; }
            int d = 0, end = -1; boolean q = false;
            for (int i = idx + fnName.length(); i < expr.length(); i++) {
                char ch = expr.charAt(i);
                if (ch == '"') q = !q;
                else if (!q) { if (ch == '(') d++; else if (ch == ')') { d--; if (d == 0) { end = i; break; } } }
            }
            if (end < 0) break;
            String val = "#ERR";
            try { String in = expr.substring(idx + fnName.length() + 1, end); val = isVlookup ? evalVlookup(in, sh, book, vis) : evalIf(in, sh, book, vis); } catch (Exception ignored) {}
            expr = expr.substring(0, idx) + val + expr.substring(end + 1);
            from = idx + val.length();
        }
        return expr;
    }

    private static String evalInternal(String raw, XlsxBook.Sheet sheet, XlsxBook book, Set<String> vis) {
        if (raw == null) return "";
        String s = raw.trim();
        if (!s.startsWith("=")) return s;
        if (vis.size() > 25) return "#LOOP";
        String expr = s.substring(1).trim().replace("$", "");
        expr = replaceCall(expr, "IF", false, sheet, book, vis);
        expr = replaceCall(expr, "VLOOKUP", true, sheet, book, vis);
        if (!expr.matches(".*[+\\-*/^()].*") && !expr.toUpperCase(Locale.US).matches("^[A-Z]{1,3}[0-9]{1,5}$")) return expr;
        expr = expr.toUpperCase(Locale.US).replace("PI()", String.valueOf(Math.PI));
        if (expr.startsWith("NUM2K(") && expr.endsWith(")")) {
            try { return SurveyMath.num2K(Double.parseDouble(evalInternal("=" + expr.substring(6, expr.length() - 1), sheet, book, vis))); } catch (Exception e) { return "#ERR"; }
        }
        Matcher mr = RANGE_P.matcher(expr); StringBuffer sb1 = new StringBuffer();
        while (mr.find()) {
            String fn = mr.group(1); List<Double> vals = collectRange(mr.group(2), sheet, book, vis); double r = 0;
            if (!vals.isEmpty()) { for (double v : vals) r += v; if (fn.equals("AVERAGE") || fn.equals("AVG")) r /= vals.size(); else if (fn.equals("MIN")) r = Collections.min(vals); else if (fn.equals("MAX")) r = Collections.max(vals); else if (fn.equals("COUNT")) r = vals.size(); }
            mr.appendReplacement(sb1, String.valueOf(r));
        }
        mr.appendTail(sb1); expr = sb1.toString();
        Matcher mc = CELL_P.matcher(expr); StringBuffer sb2 = new StringBuffer();
        while (mc.find()) {
            String ref = mc.group(0); double v = 0;
            if (!vis.contains(ref)) {
                Set<String> nv = new HashSet<>(vis); nv.add(ref);
                try { v = Double.parseDouble(getCellStr(sheet, XlsxBook.rowToIndex(ref), XlsxBook.colToIndex(ref), book, nv)); } catch (Exception ignored) {}
            }
            mc.appendReplacement(sb2, String.valueOf(v));
        }
        mc.appendTail(sb2); expr = sb2.toString();
        try {
            double ans = evalMath(expr, book);
            if (!Double.isFinite(ans)) return "#ERR";
            if (Math.abs(ans - Math.round(ans)) < 1e-9) return String.valueOf(Math.round(ans));
            return String.format(Locale.US, "%.6f", ans).replaceAll("0+$", "").replaceAll("\\.$", "");
        } catch (Exception e) { return "#ERR"; }
    }
    private static List<Double> collectRange(String spec, XlsxBook.Sheet sheet, XlsxBook book, Set<String> vis) {
        List<Double> out = new ArrayList<>();
        for (String part : spec.split(",")) {
            part = part.trim();
            if (part.contains(":")) {
                String[] ab = part.split(":");
                int r1 = XlsxBook.rowToIndex(ab[0]), c1 = XlsxBook.colToIndex(ab[0]), r2 = XlsxBook.rowToIndex(ab[1]), c2 = XlsxBook.colToIndex(ab[1]);
                for (int r = Math.min(r1, r2); r <= Math.max(r1, r2); r++)
                    for (int col = Math.min(c1, c2); col <= Math.max(c1, c2); col++) { try { out.add(Double.parseDouble(getCellStr(sheet, r, col, book, vis))); } catch (Exception ignored) {} }
            } else { try { out.add(Double.parseDouble(getCellStr(sheet, XlsxBook.rowToIndex(part), XlsxBook.colToIndex(part), book, vis))); } catch (Exception ignored) {} }
        }
        return out;
    }

    private static double[][] readTable(XlsxBook book, String keyName, int cols) {
        if (book == null) return new double[0][cols];
        for (XlsxBook.Sheet sh : book.sheets) {
            if (sh.name != null && sh.name.contains(keyName)) {
                List<double[]> rows = new ArrayList<>();
                for (List<String> r : sh.rows) {
                    if (r.size() < 2) continue;
                    try { double[] arr = new double[cols]; arr[0] = Double.parseDouble(r.get(0).trim()); for (int col = 1; col < cols; col++) arr[col] = (col < r.size() && !r.get(col).trim().isEmpty()) ? Double.parseDouble(r.get(col).trim()) : 0; rows.add(arr); } catch (Exception ignored) {}
                }
                return rows.toArray(new double[0][cols]);
            }
        }
        return new double[0][cols];
    }

    private static double evalMath(final String str, final XlsxBook book) {
        return new Object() {
            int pos = -1, ch;
            void next() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
            boolean eat(int ch2) { while (ch == ' ') next(); if (ch == ch2) { next(); return true; } return false; }
            double parse() { next(); double x = expr(); if (pos < str.length()) throw new RuntimeException(); return x; }
            double expr() { double x = term(); for (;;) { if (eat('+')) x += term(); else if (eat('-')) x -= term(); else return x; } }
            double term() { double x = factor(); for (;;) { if (eat('*')) x *= factor(); else if (eat('/')) x /= factor(); else return x; } }
            double factor() {
                if (eat('+')) return factor(); if (eat('-')) return -factor();
                double x; int sp = this.pos;
                if (eat('(')) { x = expr(); eat(')'); }
                else if ((ch >= '0' && ch <= '9') || ch == '.') { while ((ch >= '0' && ch <= '9') || ch == '.') next(); x = Double.parseDouble(str.substring(sp, this.pos)); }
                else if (Character.isLetter(ch) || ch == '_') {
                    while (Character.isLetterOrDigit(ch) || ch == '_') next();
                    String fn = str.substring(sp, this.pos); eat('('); List<Double> args = new ArrayList<>();
                    if (!eat(')')) { do { args.add(expr()); } while (eat(',')); eat(')'); }
                    x = callFunc(fn, args, book);
                } else throw new RuntimeException();
                if (eat('^')) x = Math.pow(x, factor());
                return x;
            }
        }.parse();
    }

    private static double callFunc(String fn, List<Double> a, XlsxBook book) {
        double p0 = a.size() > 0 ? a.get(0) : 0, p1 = a.size() > 1 ? a.get(1) : 0, p2 = a.size() > 2 ? a.get(2) : 0, p3 = a.size() > 3 ? a.get(3) : 0;
        switch (fn) {
            case "SIN": return Math.sin(Math.toRadians(p0)); case "COS": return Math.cos(Math.toRadians(p0)); case "TAN": return Math.tan(Math.toRadians(p0));
            case "SQRT": return Math.sqrt(p0); case "ABS": return Math.abs(p0); case "HYPOT": return Math.hypot(p0, p1);
            case "ROUND": double p = Math.pow(10, p1); return Math.round(p0 * p) / p;
            case "DMS2DEG": return Math.toDegrees(SurveyMath.dmsToRad(p0)); case "DEG2DMS": return SurveyMath.radToDms(Math.toRadians(p0));
            case "DIST": return SurveyMath.fwj(p0, p1, p2, p3)[0]; case "AZI": return SurveyMath.radToDms(SurveyMath.fwj(p0, p1, p2, p3)[1]);
            case "FWDX": return p0 + p1 * Math.cos(SurveyMath.dmsToRad(p2)); case "FWDY": return p0 + p1 * Math.sin(SurveyMath.dmsToRad(p2));
            case "PQX_X": return SurveyMath.pqxFwd(readTable(book, "平曲线", 8), p0, p1, a.size() > 2 ? p2 : 90)[0];
            case "PQX_Y": return SurveyMath.pqxFwd(readTable(book, "平曲线", 8), p0, p1, a.size() > 2 ? p2 : 90)[1];
            case "PQX_A": return SurveyMath.radToDms(SurveyMath.pqxFwd(readTable(book, "平曲线", 8), p0, 0, 0)[2]);
            case "PQX_K": return SurveyMath.pqxInv(readTable(book, "平曲线", 8), p0, p1)[0];
            case "PQX_B": return SurveyMath.pqxInv(readTable(book, "平曲线", 8), p0, p1)[1];
            case "SQX_H": return SurveyMath.sqxH(readTable(book, "竖曲线", 3), p0);
            case "GAUSS_X": return SurveyMath.gaussProj(p0, p1, a.size() > 2 ? p2 : 360)[0];
            case "GAUSS_Y": return SurveyMath.gaussProj(p0, p1, a.size() > 2 ? p2 : 360)[1];
            case "UTM_X": return SurveyMath.utmProj(p0, p1)[0]; case "UTM_Y": return SurveyMath.utmProj(p0, p1)[1];
            default: throw new RuntimeException();
        }
    }
}
