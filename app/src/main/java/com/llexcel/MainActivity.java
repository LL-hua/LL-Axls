package com.llexcel;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

public class MainActivity extends Activity {
    private boolean ignoreTextChange = false;
    private int currentFontSize = 15;
    private LinearLayout numPad;
    private XlsxBook book = new XlsxBook();
    private int curSheet = 0, selR = 0, selC = 0, extraRows = 15, extraCols = 8;
    private File curFile = null;
    private TextView tvCellRef, tvTitle;
    private EditText etFormula;
    private String clipText = "";
    private ExcelGridView gridView;
    private LinearLayout tabBar;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private Button makeBtn(String txt, int bg) {
        Button b = new Button(this);
        b.setText(txt); b.setTextColor(0xFFFFFFFF); b.setTextSize(13f);
        b.setPadding(dp(14), dp(6), dp(14), dp(6)); b.setAllCaps(false); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(bg); gd.setCornerRadius(dp(6)); b.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(dp(3), 0, dp(3), 0); b.setLayoutParams(lp);
        return b;
    }

    @Override public void onBackPressed() {
        if (numPad != null && numPad.getVisibility() == View.VISIBLE) { numPad.setVisibility(View.GONE); return; }
        if (gridView != null && gridView.selEndR >= 0) { gridView.selEndR = -1; gridView.selEndC = -1; tvCellRef.setText(XlsxBook.indexToCol(selC) + (selR + 1)); gridView.ensureCellVisible(selR, selC); gridView.invalidate(); return; }
        moveTaskToBack(true);
    }

    @Override protected void onPause() {
        super.onPause();
        try {
            File auto = new File(getFilesDir(), "autosave.xlsx");
            book.save(auto);
            getSharedPreferences("ll_axls", MODE_PRIVATE).edit()
                .putString("cur_path", curFile != null ? curFile.getAbsolutePath() : "")
                .putInt("cur_sheet", curSheet).apply();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF141414);
        book.sheets.add(new XlsxBook.Sheet("Sheet1"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF121212);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(0xFF1E1E1E); topBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        tvTitle = new TextView(this); tvTitle.setText("新建表格"); tvTitle.setTextColor(0xFF00E5FF); tvTitle.setTextSize(15f);
        topBar.addView(tvTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        
        Button bOpen = makeBtn("打开", 0xFF2E7D32), bNew = makeBtn("新建", 0xFF455A64), bSave = makeBtn("保存", 0xFF0078D4), bFont = makeBtn("字号", 0xFF5D4037);
        topBar.addView(bOpen); topBar.addView(bNew); topBar.addView(bSave); topBar.addView(bFont);
        root.addView(topBar);

        LinearLayout fxBar = new LinearLayout(this);
        fxBar.setOrientation(LinearLayout.HORIZONTAL); fxBar.setGravity(Gravity.CENTER_VERTICAL);
        fxBar.setBackgroundColor(0xFF181818); fxBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        tvCellRef = new TextView(this); tvCellRef.setText("A1"); tvCellRef.setTextColor(0xFFFFCC00);
        tvCellRef.setTextSize(15f); tvCellRef.setWidth(dp(48));
        
        etFormula = new EditText(this); etFormula.setHint("输入数值或公式如 =SUM(B2:B4)");
        etFormula.setTextColor(0xFFFFFFFF); etFormula.setHintTextColor(0xFF666666); etFormula.setTextSize(currentFontSize);
        etFormula.setSingleLine(true); etFormula.setShowSoftInputOnFocus(false);
        etFormula.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        android.graphics.drawable.GradientDrawable fgd = new android.graphics.drawable.GradientDrawable();
        fgd.setColor(0xFF22262C); fgd.setCornerRadius(dp(6)); fgd.setStroke(1, 0xFF0078D4); etFormula.setBackground(fgd);
        etFormula.setPadding(dp(8), dp(4), dp(8), dp(4));
        etFormula.setOnClickListener(v -> { etFormula.setShowSoftInputOnFocus(false); numPad.setVisibility(View.VISIBLE); gridView.postDelayed(() -> gridView.ensureCellVisible(selR, selC), 180); });
        
        etFormula.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (!ignoreTextChange) {
                    activeSheet().set(selR, selC, s.toString());
                    gridView.ensureCellVisible(selR, selC); gridView.invalidate();
                }
            }
        });

        Button bOk = makeBtn("确定", 0xFF0078D4);
        Button bFx = makeBtn("fx", 0xFF00695C);
        bFx.setOnClickListener(v -> showFormulaHelper());
        fxBar.addView(tvCellRef); fxBar.addView(bFx); fxBar.addView(etFormula, new LinearLayout.LayoutParams(0, -2, 1f)); fxBar.addView(bOk);
        root.addView(fxBar);

        // 删除了原来公式栏下方的那一排按钮（fnScroll）

        gridView = new ExcelGridView(this, Typeface.DEFAULT);
        gridView.setFontSize(currentFontSize);
        gridView.listener = new ExcelGridView.Listener() {
            @Override public void onCellClick(int r, int c) { selectCell(r, c); }
            @Override public void onColHeaderClick(int c) { showColDialog(c); }
            @Override public void onRowHeaderClick(int r) { showRowDialog(r); }
            @Override public void onCellLongClick(int r, int c) { showCellContextMenu(r, c); }
            @Override public void onCellDoubleClick(int r, int c) {
                selectCell(r, c);
                etFormula.setShowSoftInputOnFocus(false);
                numPad.setVisibility(View.VISIBLE);
                etFormula.requestFocus();
                etFormula.selectAll();
                gridView.postDelayed(() -> gridView.ensureCellVisible(r, c), 180);
            }
        };
        root.addView(gridView, new LinearLayout.LayoutParams(-1, 0, 1f));
        gridView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or_, ob) -> { if (ob - ot > 0 && (b - t) < (ob - ot)) gridView.ensureCellVisible(selR, selC); });

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setBackgroundColor(0xFF1E1E1E);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL); tabBar.setPadding(dp(6), dp(4), dp(6), dp(4));
        tabScroll.addView(tabBar);
        root.addView(tabScroll);

        numPad = new LinearLayout(this); numPad.setOrientation(LinearLayout.VERTICAL); numPad.setVisibility(View.GONE);
        numPad.setBackgroundColor(0xFF1A1D21); numPad.setPadding(dp(4), dp(4), dp(4), dp(6));
        String[][] keys = new String[][]{
            {"7","8","9","/","⌫"},
            {"4","5","6","*","←"},
            {"1","2","3","-","→"},
            {"0",".","=","+","↵ 向下"},
            {"(",")",",","中文键盘","收起"}
        };
        for (String[] row : keys) {
            LinearLayout kr = new LinearLayout(this); kr.setOrientation(LinearLayout.HORIZONTAL);
            for (String k : row) {
                int bg = k.equals("↵ 向下") ? 0xFF0078D4 : (k.equals("←") || k.equals("→") ? 0xFF00695C : (k.equals("⌫") ? 0xFF8E24AA : 0xFF2A2E35));
                Button kb = makeBtn(k, bg);
                kb.setTextSize(k.length() <= 2 ? 20f : 14f); kb.setTypeface(Typeface.DEFAULT_BOLD);
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0, dp(42), 1f);
                klp.setMargins(dp(2), dp(2), dp(2), dp(2)); kb.setLayoutParams(klp);
                kb.setOnClickListener(v -> onPadKey(k));
                kr.addView(kb);
            }
            numPad.addView(kr);
        }
        root.addView(numPad);
        setContentView(root);
        

        bOpen.setOnClickListener(v -> showOpenMenu());
        bNew.setOnClickListener(v -> createNewBook());
        bSave.setOnClickListener(v -> saveCurrentBook());
        
        bFont.setOnClickListener(v -> {
            String[] sizes = {"12 (小)", "14 (标准)", "16 (中)", "20 (大)", "24 (特大)"};
            int[] vals = {12, 14, 16, 20, 24};
            new AlertDialog.Builder(this).setTitle("选择字号").setItems(sizes, (d, w) -> {
                currentFontSize = vals[w];
                gridView.setFontSize(currentFontSize);
                etFormula.setTextSize(currentFontSize);
            }).show();
        });

        bOk.setOnClickListener(v -> commitFormula());
        etFormula.setOnEditorActionListener((v, a, e) -> { commitFormula(); return true; });

        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            try {
                android.content.Intent it = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
                startActivity(it);
            } catch (Exception ignored) {}
        }

        renderAll();
        if (getIntent() != null && getIntent().getData() != null) handleUri(getIntent().getData());
        else {
            try {
                File auto = new File(getFilesDir(), "autosave.xlsx");
                if (auto.exists() && auto.length() > 0) {
                    book = XlsxBook.load(auto);
                    String cp = getSharedPreferences("ll_axls", MODE_PRIVATE).getString("cur_path", "");
                    curSheet = getSharedPreferences("ll_axls", MODE_PRIVATE).getInt("cur_sheet", 0);
                    if (!cp.isEmpty()) { curFile = new File(cp); tvTitle.setText(curFile.getName()); }
                    selectCell(0, 0); renderAll();
                }
            } catch (Exception ignored) {}
        }
    }

    private XlsxBook.Sheet activeSheet() {
        if (book.sheets.isEmpty()) book.sheets.add(new XlsxBook.Sheet("Sheet1"));
        if (curSheet >= book.sheets.size()) curSheet = 0;
        return book.sheets.get(curSheet);
    }

    private void selectCell(int r, int c) {
        String fx = etFormula.getText().toString();
        String ref = XlsxBook.indexToCol(c) + (r + 1);
        if (fx.startsWith("=") && !(r == selR && c == selC)) {
            int pos = Math.max(etFormula.getSelectionStart(), fx.length());
            String left = fx.substring(0, pos);
            String right = fx.substring(pos);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Z]+[0-9]+)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(left);
            String insert = (m.find() && !m.group(1).equalsIgnoreCase(ref)) ? (":" + ref) : ref;
            ignoreTextChange = true;
            etFormula.setText(left + insert + right);
            etFormula.setSelection(left.length() + insert.length());
            ignoreTextChange = false;
            etFormula.requestFocus();
            return;
        }
        selR = r; selC = c; gridView.selR = r; gridView.selC = c; gridView.selEndR = -1; gridView.selEndC = -1; gridView.selEndR = -1; gridView.selEndC = -1;
        tvCellRef.setText(ref);
        ignoreTextChange = true;
        etFormula.setText(activeSheet().get(r, c));
        etFormula.setSelection(etFormula.getText().length());
        ignoreTextChange = false;
        gridView.ensureCellVisible(selR, selC); gridView.invalidate();
    }

    private void onPadKey(String k) {
        if (k.equals("←")) { commitAndMove(0, -1); return; }
        if (k.equals("→")) { commitAndMove(0, 1); return; }
        if (k.equals("↵ 向下")) { commitAndMove(1, 0); return; }
        if (k.equals("收起")) { numPad.setVisibility(View.GONE); return; }
        if (k.equals("中文键盘")) {
            numPad.setVisibility(View.GONE);
            etFormula.setShowSoftInputOnFocus(true);
            etFormula.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            etFormula.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etFormula, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            return;
        }
        String cur = etFormula.getText().toString();
        int st = Math.max(etFormula.getSelectionStart(), 0), ed = Math.max(etFormula.getSelectionEnd(), 0);
        int a = Math.min(st, ed), b = Math.max(st, ed);
        if (k.equals("⌫")) {
            if (a != b) { etFormula.setText(cur.substring(0, a) + cur.substring(b)); etFormula.setSelection(a); }
            else if (a > 0) { etFormula.setText(cur.substring(0, a - 1) + cur.substring(a)); etFormula.setSelection(a - 1); }
        } else {
            etFormula.setText(cur.substring(0, a) + k + cur.substring(b));
            etFormula.setSelection(a + k.length());
        }
    }

    private void commitAndMove(int dRow, int dCol) {
        activeSheet().set(selR, selC, etFormula.getText().toString().trim());

        gridView.refreshData(); gridView.ensureCellVisible(selR, selC);
        int nr = Math.max(0, selR + dRow), nc = Math.max(0, selC + dCol);
        if (nr >= gridView.rowCount() - 2) gridView.extraRows += 5;
        if (nc >= gridView.colCount() - 1) gridView.extraCols += 2;
        selR = nr; selC = nc; gridView.selR = nr; gridView.selC = nc;
        tvCellRef.setText(XlsxBook.indexToCol(nc) + (nr + 1));
        ignoreTextChange = true;
        etFormula.setText(activeSheet().get(nr, nc));
        etFormula.selectAll();
        ignoreTextChange = false;
        gridView.ensureCellVisible(selR, selC); gridView.invalidate();
    }
    private void commitFormula() { commitAndMove(1, 0); }

    private void renderAll() {
        tabBar.removeAllViews();
        for (int i = 0; i < book.sheets.size(); i++) {
            final int idx = i;
            Button tb = makeBtn(book.sheets.get(i).name, i == curSheet ? 0xFF0078D4 : 0xFF2C2C2C);
            tb.setOnClickListener(v -> {
                curSheet = idx; selR = 0; selC = 0; gridView.selR = 0; gridView.selC = 0;
                tvCellRef.setText("A1");
                ignoreTextChange = true;
                etFormula.setText(activeSheet().get(0, 0));
                ignoreTextChange = false;
                renderAll();
            });
            tabBar.addView(tb);
        }
        Button addSh = makeBtn("+Sheet", 0xFF2E7D32);
        addSh.setOnClickListener(v -> { book.sheets.add(new XlsxBook.Sheet("Sheet" + (book.sheets.size() + 1))); curSheet = book.sheets.size() - 1; renderAll(); });
        tabBar.addView(addSh);
        gridView.book = book;
        gridView.setSheet(activeSheet(), false);
    }

    private static String shiftFormulaRows(String fml, int dRow) {
        if (!fml.startsWith("=") || dRow == 0) return fml;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b([A-Z]+)([0-9]+)\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(fml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int nr = Math.max(1, Integer.parseInt(m.group(2)) + dRow);
            m.appendReplacement(sb, m.group(1).toUpperCase() + nr);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void showFormulaHelper() {
        final String[] labels = new String[]{
            "【平曲线正算 X】 =PQX_X(桩号, 偏距, 夹角dms)",
            "【平曲线正算 Y】 =PQX_Y(桩号, 偏距, 夹角dms)",
            "【平曲线切线方位角】 =PQX_A(桩号)",
            "【平曲线反算桩号】 =PQX_K(X坐标, Y坐标)",
            "【平曲线反算偏距】 =PQX_B(X坐标, Y坐标)",
            "【竖曲线设计高程】 =SQX_H(桩号)",
            "【米转 K 桩号】 =NUM2K(里程米)",
            "【两点平面距离】 =DIST(X1, Y1, X2, Y2)",
            "【两点方位角(度分秒)】 =AZI(X1, Y1, X2, Y2)",
            "【坐标正算 X】 =FWDX(起点X, 平距, 方位角dms)",
            "【坐标正算 Y】 =FWDY(起点Y, 平距, 方位角dms)",
            "【度分秒 → 十进制度】 =DMS2DEG(ddd.mmss)",
            "【十进制度 → 度分秒】 =DEG2DMS(十进制度)",
            "【高斯投影 X / Y】 =GAUSS_X(经度, 纬度, 中央子午线)",
            "【UTM 投影 X / Y】 =UTM_X(经度, 纬度)",
            "【常规统计】 =SUM(起:止) / AVERAGE / MIN / MAX"
        };
        final String[] inserts = new String[]{
            "=PQX_X(", "=PQX_Y(", "=PQX_A(", "=PQX_K(", "=PQX_B(",
            "=SQX_H(", "=NUM2K(", "=DIST(", "=AZI(", "=FWDX(", "=FWDY(",
            "=DMS2DEG(", "=DEG2DMS(", "=GAUSS_X(", "=UTM_X(", "=SUM("
        };
        new AlertDialog.Builder(this).setTitle("选择测量与表格函数（点击插入）")
            .setItems(labels, (d, w) -> {
                etFormula.setText(inserts[w]);
                etFormula.setSelection(inserts[w].length());
                etFormula.setShowSoftInputOnFocus(false);
                numPad.setVisibility(View.VISIBLE);
                etFormula.requestFocus();
            }).show();
    }

    private void showCellContextMenu(int r, int col) {
        String ref = XlsxBook.indexToCol(col) + (r + 1);
        int r1 = Math.min(selR, gridView.selEndR >= 0 ? gridView.selEndR : r);
        int r2 = Math.max(selR, gridView.selEndR >= 0 ? gridView.selEndR : r);
        int c1 = Math.min(selC, gridView.selEndC >= 0 ? gridView.selEndC : col);
        int c2 = Math.max(selC, gridView.selEndC >= 0 ? gridView.selEndC : col);
        String rangeLabel = XlsxBook.indexToCol(c1) + (r1 + 1) + ":" + XlsxBook.indexToCol(c2) + (r2 + 1);

        List<String> opts = new ArrayList<>();
        opts.add("设置对齐方式（左/中/右）"); // 新增
        opts.add("向下智能填充（公式行号递增 / 序列填充）");
        opts.add("清空所选单元格 / 区域");
        opts.add("合并所选区域 (" + rangeLabel + ")");
        opts.add("复制起点格 (" + XlsxBook.indexToCol(selC) + (selR + 1) + ")");
        opts.add("粘贴到此格 (" + ref + ")");
        opts.add("行操作（第 " + (r + 1) + " 行）");
        opts.add("列操作（" + XlsxBook.indexToCol(col) + " 列）");

        new AlertDialog.Builder(this).setTitle("单元格与选区操作 (" + rangeLabel + ")")
            .setItems(opts.toArray(new String[0]), (d, w) -> {
                XlsxBook.Sheet sh = activeSheet();
                if (w == 0) {
                    // 设置对齐
                    new AlertDialog.Builder(this).setTitle("选择对齐方式")
                        .setItems(new String[]{"左对齐", "居中对齐", "右对齐"}, (ad, aw) -> {
                            for (int rr = r1; rr <= r2; rr++) {
                                for (int cc = c1; cc <= c2; cc++) {
                                    long key = (((long) rr) << 32) | (cc & 0xffffffffL);
                                    gridView.cellAligns.put(key, aw); // 0=左, 1=中, 2=右
                                }
                            }
                            gridView.ensureCellVisible(selR, selC); gridView.invalidate();
                        }).show();
                } else if (w == 1) {
                    final int srcStart = r1;
                    final int srcEnd = r2;
                    final int patSize = srcEnd - srcStart + 1;
                    final int targetEnd = srcEnd + Math.max(10, patSize * 3);

                    new AlertDialog.Builder(this)
                        .setTitle("向下填充（源: 第 " + (srcStart + 1) + "~" + (srcEnd + 1) + " 行 → 至第 " + (targetEnd + 1) + " 行）")
                        .setItems(new String[]{"复制同值 / 公式行号递增", "数字智能递增 (自动或手动算步长)"}, (fd, fw) -> {
                            if (fw == 0) {
                                for (int cc = c1; cc <= c2; cc++) {
                                    for (int rr = srcEnd + 1; rr <= targetEnd; rr++) {
                                        int patIdx = (rr - srcStart) % patSize;
                                        int srcRow = srcStart + patIdx;
                                        String base = sh.get(srcRow, cc);
                                        if (base.isEmpty()) continue;
                                        int offset = rr - srcRow;
                                        if (base.startsWith("=")) sh.set(rr, cc, shiftFormulaRows(base, offset));
                                        else sh.set(rr, cc, base);
                                    }
                                }
                                gridView.refreshData(); gridView.ensureCellVisible(selR, selC);
                                Toast.makeText(this, "已填充", Toast.LENGTH_SHORT).show();
                            } else {
                                double step = 1;
                                String firstVal = sh.get(srcStart, c1);
                                String lastVal = sh.get(srcEnd, c1);
                                boolean validDouble = false;
                                try {
                                    double v1 = Double.parseDouble(firstVal);
                                    double v2 = Double.parseDouble(lastVal);
                                    if (patSize > 1) {
                                        step = (v2 - v1) / (patSize - 1);
                                        validDouble = true;
                                    }
                                } catch (Exception ignored) {}
                                
                                if (validDouble || patSize > 1) {
                                    doStepFill(sh, r1, r2, c1, c2, targetEnd, step);
                                    gridView.refreshData(); gridView.ensureCellVisible(selR, selC);
                                    Toast.makeText(this, "智能递增填充完毕（步长 " + step + "）", Toast.LENGTH_SHORT).show();
                                } else {
                                    EditText etStep = new EditText(this);
                                    etStep.setText("1");
                                    etStep.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
                                    new AlertDialog.Builder(this)
                                        .setTitle("设置递增步长")
                                        .setMessage("起始值为 " + firstVal + "，每次增加多少？")
                                        .setView(etStep)
                                        .setPositiveButton("开始填充", (sd, sw) -> {
                                            double manualStep = 1;
                                            try { manualStep = Double.parseDouble(etStep.getText().toString()); } catch (Exception ignored) {}
                                            doStepFill(sh, r1, r2, c1, c2, targetEnd, manualStep);
                                            gridView.refreshData(); gridView.ensureCellVisible(selR, selC);
                                        })
                                        .setNegativeButton("取消", null).show();
                                }
                            }
                        }).show();
                } else if (w == 2) {
                    boolean isSingle = (r1 == r2 && c1 == c2);
                    if (isSingle) {
                        new AlertDialog.Builder(this).setTitle("清空")
                            .setItems(new String[]{"仅清空当前格", "清空当前格及向下 10 行"}, (cd, cw) -> {
                                if (cw == 0) {
                                    sh.set(r1, c1, "");
                                } else {
                                    for (int rr = r1; rr <= r1 + 10; rr++) sh.set(rr, c1, "");
                                }
                                ignoreTextChange = true;
                                etFormula.setText(""); 
                                ignoreTextChange = false;
                                gridView.refreshData(); gridView.ensureCellVisible(selR, selC);
                            }).show();
                    } else {
                        for (int rr = r1; rr <= r2; rr++) for (int cc = c1; cc <= c2; cc++) sh.set(rr, cc, "");
                        ignoreTextChange = true;
                        etFormula.setText(""); 
                        ignoreTextChange = false;
                        gridView.refreshData(); gridView.ensureCellVisible(selR, selC);
                    }
                } else if (w == 3) {
                    if (r1 == r2 && c1 == c2) { showMergeDialog(); return; }
                    sh.merges.removeIf(m -> !(m[2] < r1 || m[0] > r2 || m[3] < c1 || m[1] > c2));
                    String topVal = sh.get(r1, c1);
                    for (int rr = r1; rr <= r2; rr++) for (int cc = c1; cc <= c2; cc++) {
                        if (topVal.isEmpty() && !sh.get(rr, cc).isEmpty()) topVal = sh.get(rr, cc);
                        if (!(rr == r1 && cc == c1)) sh.set(rr, cc, "");
                    }
                    sh.set(r1, c1, topVal);
                    sh.merges.add(new int[]{r1, c1, r2, c2});
                    selectCell(r1, c1);
                } else if (w == 4) {
                    clipText = sh.get(selR, selC);
                    Toast.makeText(this, "已复制: " + clipText, Toast.LENGTH_SHORT).show();
                } else if (w == 5) {
                    sh.set(r, col, clipText); selectCell(r, col); gridView.refreshData(); gridView.ensureCellVisible(selR, selC);
                } else if (w == 6) showRowDialog(r);
                else if (w == 7) showColDialog(col);
            }).show();
    }

    private void doStepFill(XlsxBook.Sheet sh, int r1, int r2, int c1, int c2, int targetEnd, double step) {
        for (int cc = c1; cc <= c2; cc++) {
            String baseStr = sh.get(r1, cc);
            double startVal = 0;
            try { startVal = Double.parseDouble(baseStr); } catch (Exception e) { continue; }
            
            for (int rr = r2 + 1; rr <= targetEnd; rr++) {
                double nv = startVal + (rr - r1) * step;
                sh.set(rr, cc, Math.abs(nv - Math.round(nv)) < 1e-9
                    ? String.valueOf(Math.round(nv))
                    : String.format(Locale.US, "%.4f", nv).replaceAll("0+$", "").replaceAll("\\.$", ""));
            }
        }
    }

    private void showMergeDialog() {
        XlsxBook.Sheet sh = activeSheet();
        int[] curM = null;
        for (int[] m : sh.merges) if (selR >= m[0] && selR <= m[2] && selC >= m[1] && selC <= m[3]) curM = m;
        String curRef = XlsxBook.indexToCol(selC) + (selR + 1);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(12), dp(20), dp(4));
        EditText etEnd = new EditText(this);
        etEnd.setHint("结束单元格（如 C" + (selR + 1) + "）");
        etEnd.setText(XlsxBook.indexToCol(selC + 1) + (selR + 1));
        box.addView(etEnd);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
            .setTitle("合并单元格（起点: " + curRef + "）")
            .setView(box)
            .setPositiveButton("确定合并", (d, w) -> {
                String endRef = etEnd.getText().toString().trim().toUpperCase();
                if (endRef.isEmpty()) return;
                int r2 = XlsxBook.rowToIndex(endRef), c2 = XlsxBook.colToIndex(endRef);
                int rMin = Math.min(selR, r2), rMax = Math.max(selR, r2);
                int cMin = Math.min(selC, c2), cMax = Math.max(selC, c2);
                if (rMin == rMax && cMin == cMax) return;
                sh.merges.removeIf(m -> !(m[2] < rMin || m[0] > rMax || m[3] < cMin || m[1] > cMax));
                String topVal = sh.get(rMin, cMin);
                for (int r = rMin; r <= rMax; r++) {
                    for (int col = cMin; col <= cMax; col++) {
                        if (topVal.isEmpty() && !sh.get(r, col).isEmpty()) topVal = sh.get(r, col);
                        if (!(r == rMin && col == cMin)) sh.set(r, col, "");
                    }
                }
                sh.set(rMin, cMin, topVal);
                sh.merges.add(new int[]{rMin, cMin, rMax, cMax});
                selectCell(rMin, cMin);
            })
            .setNegativeButton("取消", null);

        if (curM != null) {
            final int[] fm = curM;
            b.setNeutralButton("拆分取消合并", (d, w) -> {
                sh.merges.remove(fm);
                renderAll();
            });
        }
        b.show();
    }

    private void showColDialog(int col) {
        String name = XlsxBook.indexToCol(col) + " 列";
        String[] items = new String[]{"自适应列宽", "左对齐", "居中对齐", "右对齐", "加宽此列 (+30)", "变窄此列 (-25)", "隐藏此列", "显示全部隐藏列", "左侧插入一列", "右侧插入一列", "删除此列"};
        new AlertDialog.Builder(this).setTitle("列格式与操作: " + name)
            .setItems(items, (d, w) -> {
                XlsxBook.Sheet sh = activeSheet();
                if (w == 0) gridView.autoFitCol(col);
                else if (w == 1) gridView.colAligns.put(col, 0);
                else if (w == 2) gridView.colAligns.put(col, 1);
                else if (w == 3) gridView.colAligns.put(col, 2);
                else if (w == 4) gridView.colWidths.put(col, gridView.colWidths.getOrDefault(col, 105) + 30);
                else if (w == 5) gridView.colWidths.put(col, Math.max(50, gridView.colWidths.getOrDefault(col, 105) - 25));
                else if (w == 6) gridView.hiddenCols.add(col);
                else if (w == 7) gridView.hiddenCols.clear();
                else if (w == 8) sh.insertCol(col);
                else if (w == 9) sh.insertCol(col + 1);
                else if (w == 10) sh.deleteCol(col);
                renderAll();
            }).show();
    }

    private void showRowDialog(int row) {
        String name = "第 " + (row + 1) + " 行";
        String[] items = new String[]{"自适应行高", "整行左对齐", "整行居中对齐", "整行右对齐", "加高此行 (+12)", "变矮此行 (-8)", "隐藏此行", "显示全部隐藏行", "上方插入一行", "下方插入一行", "删除此行"};
        new AlertDialog.Builder(this).setTitle("行格式与操作: " + name)
            .setItems(items, (d, w) -> {
                XlsxBook.Sheet sh = activeSheet();
                if (w == 0) gridView.autoFitRow(row);
                else if (w == 1) gridView.rowAligns.put(row, 0);
                else if (w == 2) gridView.rowAligns.put(row, 1);
                else if (w == 3) gridView.rowAligns.put(row, 2);
                else if (w == 4) gridView.rowHeights.put(row, gridView.rowHeights.getOrDefault(row, 36) + 12);
                else if (w == 5) gridView.rowHeights.put(row, Math.max(24, gridView.rowHeights.getOrDefault(row, 36) - 8));
                else if (w == 6) gridView.hiddenRows.add(row);
                else if (w == 7) gridView.hiddenRows.clear();
                else if (w == 8) sh.insertRow(row);
                else if (w == 9) sh.insertRow(row + 1);
                else if (w == 10) sh.deleteRow(row);
                renderAll();
            }).show();
    }

    private void handleUri(android.net.Uri u) {
        if (u == null) return;
        try {
            String fn = "外部表格.xlsx";
            try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
                if (c != null && c.moveToFirst()) { int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (idx >= 0) fn = c.getString(idx); }
            }
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) { book = XlsxBook.load(in); }
            File dir = new File("/storage/emulated/0/1hdm"); if (!dir.exists()) dir.mkdirs();
            File dst = new File(dir, fn);
            if (dst.exists()) {
                int dot = fn.lastIndexOf(".");
                String base = dot > 0 ? fn.substring(0, dot) : fn, ext = dot > 0 ? fn.substring(dot) : ".xlsx";
                int idx = 1;
                while (dst.exists()) dst = new File(dir, base + "(" + idx++ + ")" + ext);
                fn = dst.getName();
            }
            curFile = dst; curSheet = 0; selR = 0; selC = 0;
            tvTitle.setText(fn); etFormula.setText(""); selectCell(0, 0); renderAll();
        } catch (Exception e) { Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void showOpenMenu() {
        new AlertDialog.Builder(this).setTitle("打开表格").setItems(new String[]{"打开 /sdcard/1hdm/ 目录", "浏览手机全部文件夹"}, (d, w) -> {
            if (w == 0) showOpenDialog();
            else {
                android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(android.content.Intent.CATEGORY_OPENABLE); it.setType("*/*");
                startActivityForResult(it, 101);
            }
        }).show();
    }

    private void showOpenDialog() {
        File dir = new File("/storage/emulated/0/1hdm");
        File[] fs = dir.listFiles();
        List<File> xls = new ArrayList<>(); List<String> names = new ArrayList<>();
        if (fs != null) for (File f : fs) if (f.isFile() && f.getName().toLowerCase().endsWith(".xlsx")) { xls.add(f); names.add(f.getName()); }
        if (xls.isEmpty()) { Toast.makeText(this, "/sdcard/1hdm/ 下未找到 .xlsx 文件", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("选择表格 (/sdcard/1hdm/)")
            .setItems(names.toArray(new String[0]), (d, w) -> {
                try {
                    curFile = xls.get(w); book = XlsxBook.load(curFile); curSheet = 0; selR = 0; selC = 0;
                    tvTitle.setText(curFile.getName()); etFormula.setText(""); selectCell(0, 0); renderAll();
                } catch (Exception e) {
                    Toast.makeText(this, "打开失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private void createNewBook() {
        EditText et = new EditText(this); et.setHint("输入文件名，如 放样记录.xlsx");
        new AlertDialog.Builder(this).setTitle("新建工作簿").setView(et)
            .setPositiveButton("创建", (d, w) -> {
                String fn = et.getText().toString().trim();
                if (fn.isEmpty()) fn = "新建表格.xlsx";
                if (!fn.toLowerCase().endsWith(".xlsx")) fn += ".xlsx";
                File dir = new File("/storage/emulated/0/1hdm"); if (!dir.exists()) dir.mkdirs();
                curFile = new File(dir, fn); book = new XlsxBook(); book.sheets.add(new XlsxBook.Sheet("Sheet1"));
                curSheet = 0; tvTitle.setText(fn); selectCell(0, 0); renderAll();
            }).setNegativeButton("取消", null).show();
    }

    private void saveCurrentBook() {
        if (curFile == null) { createNewBook(); return; }
        try {
            File tmp = new File(curFile.getParentFile(), curFile.getName() + ".tmp");
            book.save(tmp);
            if (tmp.length() > 0) { if (curFile.exists()) curFile.delete(); tmp.renameTo(curFile); }
            Toast.makeText(this, "已保存: " + curFile.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}