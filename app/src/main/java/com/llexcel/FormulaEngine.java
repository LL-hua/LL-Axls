package com.llexcel;

import java.util.*;
import java.util.regex.*;

public class FormulaEngine {
    private static final Pattern RANGE_P = Pattern.compile("(SUM|AVERAGE|AVG|MIN|MAX|COUNT)\\(([A-Z0-9:,]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL_P = Pattern.compile("\\b([A-Z]+)([0-9]+)\\b", Pattern.CASE_INSENSITIVE);

    public static String evaluate(String raw, XlsxBook.Sheet sheet) {
        return evalInternal(raw, sheet, new HashSet<>());
    }

    private static String evalInternal(String raw, XlsxBook.Sheet sheet, Set<String> visited) {
        if (raw == null) return "";
        String s = raw.trim();
        if (!s.startsWith("=")) return s;
        String expr = s.substring(1).trim().toUpperCase(Locale.US).replace("PI()", String.valueOf(Math.PI));

        Matcher mr = RANGE_P.matcher(expr);
        StringBuffer sb1 = new StringBuffer();
        while (mr.find()) {
            String fn = mr.group(1);
            List<Double> vals = collectRange(mr.group(2), sheet, visited);
            double r = 0;
            if (!vals.isEmpty()) {
                for (double v : vals) r += v;
                if (fn.equals("AVERAGE") || fn.equals("AVG")) r /= vals.size();
                else if (fn.equals("MIN")) r = Collections.min(vals);
                else if (fn.equals("MAX")) r = Collections.max(vals);
                else if (fn.equals("COUNT")) r = vals.size();
            }
            mr.appendReplacement(sb1, String.valueOf(r));
        }
        mr.appendTail(sb1);
        expr = sb1.toString();

        Matcher mc = CELL_P.matcher(expr);
        StringBuffer sb2 = new StringBuffer();
        while (mc.find()) {
            String ref = mc.group(0);
            double v = 0;
            if (!visited.contains(ref)) {
                Set<String> nv = new HashSet<>(visited);
                nv.add(ref);
                int r = XlsxBook.rowToIndex(ref), col = XlsxBook.colToIndex(ref);
                String res = evalInternal(sheet.get(r, col), sheet, nv);
                try { v = Double.parseDouble(res); } catch (Exception ignored) {}
            }
            mc.appendReplacement(sb2, String.valueOf(v));
        }
        mc.appendTail(sb2);
        expr = sb2.toString();

        try {
            double ans = evalMath(expr);
            if (!Double.isFinite(ans)) return "#ERR";
            if (Math.abs(ans - Math.round(ans)) < 1e-9) return String.valueOf(Math.round(ans));
            return String.format(Locale.US, "%.6f", ans).replaceAll("0+$", "").replaceAll("\\.$", "");
        } catch (Exception e) {
            return "#ERR";
        }
    }
    private static List<Double> collectRange(String spec, XlsxBook.Sheet sheet, Set<String> visited) {
        List<Double> out = new ArrayList<>();
        for (String part : spec.split(",")) {
            part = part.trim();
            if (part.contains(":")) {
                List<String> ab = Arrays.asList(part.split(":"));
                int r1 = XlsxBook.rowToIndex(ab.get(0)), c1 = XlsxBook.colToIndex(ab.get(0));
                int r2 = XlsxBook.rowToIndex(ab.get(1)), c2 = XlsxBook.colToIndex(ab.get(1));
                for (int r = Math.min(r1, r2); r <= Math.max(r1, r2); r++)
                    for (int c = Math.min(c1, c2); c <= Math.max(c1, c2); c++) {
                        try { out.add(Double.parseDouble(evalInternal(sheet.get(r, c), sheet, visited))); } catch (Exception ignored) {}
                    }
            } else {
                try { out.add(Double.parseDouble(evalInternal(sheet.get(XlsxBook.rowToIndex(part), XlsxBook.colToIndex(part)), sheet, visited))); } catch (Exception ignored) {}
            }
        }
        return out;
    }

    private static double evalMath(final String str) {
        return new Object() {
            int pos = -1, ch;
            void next() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
            boolean eat(int c) { while (ch == ' ') next(); if (ch == c) { next(); return true; } return false; }
            double parse() { next(); double x = expr(); if (pos < str.length()) throw new RuntimeException(); return x; }
            double expr() { double x = term(); for (;;) { if (eat('+')) x += term(); else if (eat('-')) x -= term(); else return x; } }
            double term() { double x = factor(); for (;;) { if (eat('*')) x *= factor(); else if (eat('/')) x /= factor(); else return x; } }
            double factor() {
                if (eat('+')) return factor(); if (eat('-')) return -factor();
                double x; int sp = this.pos;
                if (eat('(')) { x = expr(); eat(')'); }
                else if ((ch >= '0' && ch <= '9') || ch == '.') { while ((ch >= '0' && ch <= '9') || ch == '.') next(); x = Double.parseDouble(str.substring(sp, this.pos)); }
                else if (Character.isLetter(ch)) {
                    while (Character.isLetter(ch)) next(); String fn = str.substring(sp, this.pos); eat('(');
                    double a = expr(), b = eat(',') ? expr() : 0; eat(')');
                    if (fn.equals("SIN")) x = Math.sin(Math.toRadians(a)); else if (fn.equals("COS")) x = Math.cos(Math.toRadians(a));
                    else if (fn.equals("TAN")) x = Math.tan(Math.toRadians(a)); else if (fn.equals("SQRT")) x = Math.sqrt(a);
                    else if (fn.equals("ABS")) x = Math.abs(a); else if (fn.equals("HYPOT")) x = Math.hypot(a, b);
                    else if (fn.equals("ROUND")) { double p = Math.pow(10, b); x = Math.round(a * p) / p; }
                    else throw new RuntimeException();
                } else throw new RuntimeException();
                if (eat('^')) x = Math.pow(x, factor());
                return x;
            }
        }.parse();
    }
}
