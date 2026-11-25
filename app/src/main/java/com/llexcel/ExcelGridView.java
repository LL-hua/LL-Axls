package com.llexcel;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.OverScroller;
import java.util.*;

public class ExcelGridView extends View {
    public interface Listener {
        void onCellClick(int r, int c);
        void onColHeaderClick(int c);
        void onRowHeaderClick(int r);
        void onCellLongClick(int r, int c);
        void onCellDoubleClick(int r, int c);
    }

    public XlsxBook.Sheet sheet;
    public int selR = 0, selC = 0, extraRows = 25, extraCols = 10;
    public final Map<Integer, Integer> colWidths = new HashMap<>();
    public final Map<Integer, Integer> rowHeights = new HashMap<>();
    private final Map<Long, String> evalCache = new HashMap<>();
    private final Paint pBg = new Paint(), pText = new Paint(Paint.ANTI_ALIAS_FLAG), pLine = new Paint(), pSel = new Paint();
    private final OverScroller scroller;
    private final GestureDetector detector;
    public Listener listener;
    private float scrollX = 0, scrollY = 0;

    public ExcelGridView(Context ctx, Typeface tf) {
        super(ctx);
        scroller = new OverScroller(ctx);
        pText.setTypeface(tf);
        pText.setTextSize(dp(15));
        pLine.setColor(0xFF333333);
        pLine.setStrokeWidth(1.5f);
        pSel.setColor(0xFF00E5FF);
        pSel.setStyle(Paint.Style.STROKE);
        pSel.setStrokeWidth(dp(2));
        detector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { scroller.forceFinished(true); return true; }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                clampScroll(scrollX + dx, scrollY + dy); invalidate(); return true;
            }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                scroller.fling((int) scrollX, (int) scrollY, (int) -vx, (int) -vy, 0, Math.max(0, totalW() - getWidth()), 0, Math.max(0, totalH() - getHeight()));
                invalidate(); return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleTap(e.getX(), e.getY(), 0); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { handleTap(e.getX(), e.getY(), 2); return true; }
            @Override public void onLongPress(MotionEvent e) { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); handleTap(e.getX(), e.getY(), 1); }
        });
    }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    public int getColW(int col) { return (int) dp(colWidths.getOrDefault(col, 105)); }
    public int getRowH(int row) { return (int) dp(rowHeights.getOrDefault(row, 36)); }
    private int hdrW() { return (int) dp(44); }
    private int hdrH() { return (int) dp(34); }

    public int rowCount() { return sheet == null ? extraRows : Math.max(sheet.rows.size() + 8, extraRows); }
    public int colCount() {
        int mc = extraCols;
        if (sheet != null) for (List<String> r : sheet.rows) mc = Math.max(mc, r.size() + 3);
        return mc;
    }
    private int totalW() { int w = hdrW(); for (int i = 0; i < colCount(); i++) w += getColW(i); return w; }
    private int totalH() { int h = hdrH(); for (int i = 0; i < rowCount(); i++) h += getRowH(i); return h; }

    private void clampScroll(float nx, float ny) {
        scrollX = Math.max(0, Math.min(nx, Math.max(0, totalW() - getWidth())));
        scrollY = Math.max(0, Math.min(ny, Math.max(0, totalH() - getHeight())));
    }

    public void setSheet(XlsxBook.Sheet sh, boolean resetScroll) {
        this.sheet = sh;
        evalCache.clear();
        if (resetScroll) { scrollX = 0; scrollY = 0; }
        clampScroll(scrollX, scrollY);
        invalidate();
    }

    public void refreshData() { evalCache.clear(); invalidate(); }
    public void setFont(Typeface tf) { pText.setTypeface(tf); invalidate(); }

    private String getDisplay(int r, int col) {
        if (sheet == null) return "";
        String raw = sheet.get(r, col);
        if (!raw.startsWith("=")) return raw;
        long key = (((long) r) << 32) | (col & 0xffffffffL);
        String v = evalCache.get(key);
        if (v == null) { v = FormulaEngine.evaluate(raw, sheet); evalCache.put(key, v); }
        return v;
    }

    private int[] findMerge(int r, int col) {
        if (sheet == null) return null;
        for (int[] m : sheet.merges) if (r >= m[0] && r <= m[2] && col >= m[1] && col <= m[3]) return m;
        return null;
    }
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF141414);
        int vw = getWidth(), vh = getHeight(), hw = hdrW(), hh = hdrH(), rc = rowCount(), cc = colCount();
        Paint.FontMetrics fm = pText.getFontMetrics();
        float textOffY = -(fm.ascent + fm.descent) / 2f;

        canvas.save();
        canvas.clipRect(hw, hh, vw, vh);
        float y = hh - scrollY;
        for (int r = 0; r < rc; r++) {
            int rh = getRowH(r);
            if (y > vh) break;
            if (y + rh >= hh) {
                float x = hw - scrollX;
                for (int col = 0; col < cc; col++) {
                    int cw = getColW(col);
                    if (x > vw) break;
                    int[] m = findMerge(r, col);
                    if (m != null && !(m[0] == r && m[1] == col)) { x += cw; continue; }
                    int wSpan = cw, hSpan = rh;
                    if (m != null) {
                        wSpan = 0; for (int k = m[1]; k <= m[3]; k++) wSpan += getColW(k);
                        hSpan = 0; for (int k = m[0]; k <= m[2]; k++) hSpan += getRowH(k);
                    }
                    if (x + wSpan >= hw) {
                        boolean isSel = (m != null) ? (selR >= m[0] && selR <= m[2] && selC >= m[1] && selC <= m[3]) : (r == selR && col == selC);
                        pBg.setColor(isSel ? 0xFF1A365D : (m != null ? 0xFF1C242B : 0xFF161616));
                        canvas.drawRect(x, y, x + wSpan, y + hSpan, pBg);
                        canvas.drawRect(x, y, x + wSpan, y + hSpan, pLine);
                        String txt = getDisplay(r, col);
                        if (!txt.isEmpty()) {
                            canvas.save(); canvas.clipRect(x + dp(4), y, x + wSpan - dp(4), y + hSpan);
                            pText.setColor(sheet != null && sheet.get(r, col).startsWith("=") ? 0xFF00E5FF : 0xFFEEEEEE);
                            float tx = (m != null) ? Math.max(x + dp(4), x + (wSpan - pText.measureText(txt)) / 2f) : (x + dp(6));
                            canvas.drawText(txt, tx, y + hSpan / 2f + textOffY, pText);
                            canvas.restore();
                        }
                        if (isSel) canvas.drawRect(x + 1, y + 1, x + wSpan - 1, y + hSpan - 1, pSel);
                    }
                    x += cw;
                }
            }
            y += rh;
        }
        canvas.restore();

        canvas.save(); canvas.clipRect(hw, 0, vw, hh);
        float hx = hw - scrollX;
        for (int col = 0; col < cc; col++) {
            int cw = getColW(col);
            if (hx > vw) break;
            if (hx + cw >= hw) {
                pBg.setColor(col == selC ? 0xFF283593 : 0xFF222222);
                canvas.drawRect(hx, 0, hx + cw, hh, pBg); canvas.drawRect(hx, 0, hx + cw, hh, pLine);
                String lbl = XlsxBook.indexToCol(col); pText.setColor(0xFFCCCCCC);
                canvas.drawText(lbl, hx + (cw - pText.measureText(lbl)) / 2f, hh / 2f + textOffY, pText);
            }
            hx += cw;
        }
        canvas.restore();

        canvas.save(); canvas.clipRect(0, hh, hw, vh);
        float hy = hh - scrollY;
        for (int r = 0; r < rc; r++) {
            int rh = getRowH(r);
            if (hy > vh) break;
            if (hy + rh >= hh) {
                pBg.setColor(r == selR ? 0xFF283593 : 0xFF222222);
                canvas.drawRect(0, hy, hw, hy + rh, pBg); canvas.drawRect(0, hy, hw, hy + rh, pLine);
                String lbl = String.valueOf(r + 1); pText.setColor(0xFFAAAAAA);
                canvas.drawText(lbl, (hw - pText.measureText(lbl)) / 2f, hy + rh / 2f + textOffY, pText);
            }
            hy += rh;
        }
        canvas.restore();

        pBg.setColor(0xFF252525); canvas.drawRect(0, 0, hw, hh, pBg); canvas.drawRect(0, 0, hw, hh, pLine);
    }

    @Override public void computeScroll() { if (scroller.computeScrollOffset()) { clampScroll(scroller.getCurrX(), scroller.getCurrY()); invalidate(); } }
    @Override public boolean onTouchEvent(MotionEvent e) { return detector.onTouchEvent(e); }

    private void handleTap(float tx, float ty, int mode) {
        int hw = hdrW(), hh = hdrH(), rc = rowCount(), cc = colCount();
        int hitC = -1; float x = hw - scrollX;
        for (int col = 0; col < cc; col++) { int cw = getColW(col); if (tx >= x && tx < x + cw) { hitC = col; break; } x += cw; }
        int hitR = -1; float y = hh - scrollY;
        for (int r = 0; r < rc; r++) { int rh = getRowH(r); if (ty >= y && ty < y + rh) { hitR = r; break; } y += rh; }
        if (listener == null) return;
        if (ty < hh && tx >= hw && hitC >= 0) listener.onColHeaderClick(hitC);
        else if (tx < hw && ty >= hh && hitR >= 0) listener.onRowHeaderClick(hitR);
        else if (tx >= hw && ty >= hh && hitR >= 0 && hitC >= 0) {
            if (mode == 1) listener.onCellLongClick(hitR, hitC);
            else if (mode == 2) listener.onCellDoubleClick(hitR, hitC);
            else listener.onCellClick(hitR, hitC);
        }
    }
}
