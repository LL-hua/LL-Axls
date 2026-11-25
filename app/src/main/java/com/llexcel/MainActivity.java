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
    private Typeface singleFont, curFont; private boolean useSingle = true; private LinearLayout numPad;
    private XlsxBook book = new XlsxBook();
    private int curSheet = 0, selR = 0, selC = 0, extraRows = 15, extraCols = 8;
    private File curFile = null;
    private TextView tvCellRef, tvTitle;
    private EditText etFormula;
    private String clipText = "";
    private ExcelGridView gridView;
    private LinearLayout tabBar;

    public static Typeface loadSingleLineFont(Activity act) {
        try {
            File ext = new File("/storage/emulated/0/1hdm/HZ_SingleLine.ttf");
            if (ext.exists()) return Typeface.createFromFile(ext);
            return Typeface.createFromAsset(act.getAssets(), "HZ_SingleLine.ttf");
        } catch (Exception e) { return Typeface.MONOSPACE; }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private Button makeBtn(String txt, int bg) {
        Button b = new Button(this);
        b.setText(txt); b.setTextColor(0xFFFFFFFF); b.setTextSize(13f); b.setTypeface(singleFont);
        b.setPadding(dp(14), dp(6), dp(14), dp(6)); b.setAllCaps(false); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(bg); gd.setCornerRadius(dp(6)); b.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(dp(3), 0, dp(3), 0); b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF141414);
        singleFont = loadSingleLineFont(this); curFont = singleFont;
        book.sheets.add(new XlsxBook.Sheet("Sheet1"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF121212);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL); topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(0xFF1E1E1E); topBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        tvTitle = new TextView(this); tvTitle.setText("新建表格"); tvTitle.setTextColor(0xFF00E5FF); tvTitle.setTextSize(15f); tvTitle.setTypeface(singleFont);
        topBar.addView(tvTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        Button bOpen = makeBtn("打开", 0xFF2E7D32), bNew = makeBtn("新建", 0xFF455A64), bSave = makeBtn("保存", 0xFF0078D4), bFont = makeBtn("字体", 0xFF5D4037);
        topBar.addView(bOpen); topBar.addView(bNew); topBar.addView(bSave); topBar.addView(bFont);
        root.addView(topBar);
        LinearLayout fxBar = new LinearLayout(this);
        fxBar.setOrientation(LinearLayout.HORIZONTAL); fxBar.setGravity(Gravity.CENTER_VERTICAL);
        fxBar.setBackgroundColor(0xFF181818); fxBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        tvCellRef = new TextView(this); tvCellRef.setText("A1"); tvCellRef.setTextColor(0xFFFFCC00);
        tvCellRef.setTextSize(15f); tvCellRef.setTypeface(singleFont); tvCellRef.setWidth(dp(48));
        etFormula = new EditText(this); etFormula.setHint("输入数值或公式如 =SUM(B2:B4)");
        etFormula.setTextColor(0xFFFFFFFF); etFormula.setHintTextColor(0xFF666666); etFormula.setTextSize(15f);
        etFormula.setTypeface(singleFont); etFormula.setSingleLine(true); etFormula.setShowSoftInputOnFocus(false); etFormula.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED); android.graphics.drawable.GradientDrawable fgd = new android.graphics.drawable.GradientDrawable(); fgd.setColor(0xFF22262C); fgd.setCornerRadius(dp(6)); fgd.setStroke(1, 0xFF0078D4); etFormula.setBackground(fgd);
        etFormula.setPadding(dp(8), dp(4), dp(8), dp(4));
        Button bOk = makeBtn("确定", 0xFF0078D4);
        fxBar.addView(tvCellRef); fxBar.addView(etFormula, new LinearLayout.LayoutParams(0, -2, 1f)); fxBar.addView(bOk);
        root.addView(fxBar);

        HorizontalScrollView fnScroll = new HorizontalScrollView(this);
        fnScroll.setBackgroundColor(0xFF1A1A1A);
        LinearLayout fnBar = new LinearLayout(this);
        fnBar.setOrientation(LinearLayout.HORIZONTAL);
        fnBar.setPadding(dp(6), dp(3), dp(6), dp(3));
        Button bKb = makeBtn("ABC", 0xFF455A64);
        bKb.setOnClickListener(v -> {
            boolean isNum = (etFormula.getInputType() & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER;
            etFormula.setInputType(isNum ? android.text.InputType.TYPE_CLASS_TEXT : (android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED));
            bKb.setText(isNum ? "123" : "ABC");
            etFormula.setSelection(etFormula.getText().length());
        });
        Button bLeft = makeBtn("←", 0xFF00695C), bRight = makeBtn("→", 0xFF00695C);
        bLeft.setOnClickListener(v -> commitAndMove(0, -1));
        bRight.setOnClickListener(v -> commitAndMove(0, 1));
        fnBar.addView(bKb); fnBar.addView(bLeft); fnBar.addView(bRight);
        for (String tok : java.util.Arrays.asList("=", "SUM(", "AVERAGE(", "SIN(", "COS(", "SQRT(", "HYPOT(", ":", ",", ")")) {
            Button fb = makeBtn(tok, 0xFF2B2B2B);
            fb.setOnClickListener(v -> {
                String cur = etFormula.getText().toString();
                int pos = Math.max(etFormula.getSelectionStart(), 0);
                String next = cur.substring(0, pos) + tok + cur.substring(pos);
                etFormula.setText(next);
                etFormula.setSelection(pos + tok.length());
                etFormula.requestFocus();
            });
            fnBar.addView(fb);
        }
        fnScroll.addView(fnBar);
        root.addView(fnScroll);

        gridView = new ExcelGridView(this, singleFont);
        gridView.listener = new ExcelGridView.Listener() {
            @Override public void onCellClick(int r, int c) { selectCell(r, c); }
            @Override public void onColHeaderClick(int c) { showColDialog(c); }
            @Override public void onRowHeaderClick(int r) { showRowDialog(r); }
            @Override public void onCellLongClick(int r, int c) { selectCell(r, c); showCellContextMenu(r, c); }
            @Override public void onCellDoubleClick(int r, int c) {
                selR = r; selC = c; gridView.selR = r; gridView.selC = c;
                tvCellRef.setText(XlsxBook.indexToCol(c) + (r + 1));
                etFormula.setText(activeSheet().get(r, c));
                gridView.invalidate();
                etFormula.requestFocus();
                etFormula.selectAll();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                numPad.setVisibility(View.VISIBLE);
            }
        };
        root.addView(gridView, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setBackgroundColor(0xFF1E1E1E);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL); tabBar.setPadding(dp(6), dp(4), dp(6), dp(4));
        tabScroll.addView(tabBar);
        root.addView(tabScroll);
        numPad = new LinearLayout(this); numPad.setOrientation(LinearLayout.VERTICAL); numPad.setBackgroundColor(0xFF1A1D21); numPad.setPadding(dp(4), dp(4), dp(4), dp(6));
        String[][] keys = new String[][]{{"7","8","9","/","⌫"}, {"4","5","6","*","←"}, {"1","2","3","-","→"}, {"0",".","=","+","↵ 向下"}, {"(",")",":","中文键盘","收起"}};
        for (String[] row : keys) {
            LinearLayout kr = new LinearLayout(this); kr.setOrientation(LinearLayout.HORIZONTAL);
            for (String k : row) {
                int bg = k.equals("↵ 向下") ? 0xFF0078D4 : (k.equals("←") || k.equals("→") ? 0xFF00695C : (k.equals("⌫") ? 0xFF8E24AA : 0xFF2A2E35));
                Button kb = makeBtn(k, bg);
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0, dp(38), 1f); klp.setMargins(dp(2), dp(2), dp(2), dp(2)); kb.setLayoutParams(klp);
                kb.setOnClickListener(v -> onPadKey(k));
                kr.addView(kb);
            }
            numPad.addView(kr);
        }
        root.addView(numPad);
        setContentView(root);

        bOpen.setOnClickListener(v -> showOpenMenu());
        bNew.setOnClickListener(v -> createNewBook());
        bSave.setOnClickListener(v -> saveCurrentBook()); bFont.setOnClickListener(v -> { useSingle = !useSingle; curFont = useSingle ? singleFont : Typeface.SANS_SERIF; gridView.setFont(curFont); etFormula.setTypeface(curFont); Toast.makeText(this, useSingle ? "已切换: CAD单线体" : "已切换: 系统标准体", Toast.LENGTH_SHORT).show(); });

        bOk.setOnClickListener(v -> commitFormula()); etFormula.setOnEditorActionListener((v, a, e) -> { commitFormula(); return true; });
        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            try {
                android.content.Intent it = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
                startActivity(it);
            } catch (Exception ignored) {}
        }
        renderAll();
        if (getIntent() != null && getIntent().getData() != null) handleUri(getIntent().getData());
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
            etFormula.setText(left + insert + right);
            etFormula.setSelection(left.length() + insert.length());
            etFormula.requestFocus();
            return;
        }
        selR = r; selC = c; gridView.selR = r; gridView.selC = c;
        tvCellRef.setText(ref);
        etFormula.setText(activeSheet().get(r, c));
        etFormula.setSelection(etFormula.getText().length());
        gridView.invalidate();
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
        gridView.refreshData();
        int nr = Math.max(0, selR + dRow), nc = Math.max(0, selC + dCol);
        if (nr >= gridView.rowCount() - 2) gridView.extraRows += 5;
        if (nc >= gridView.colCount() - 1) gridView.extraCols += 2;
        selR = nr; selC = nc; gridView.selR = nr; gridView.selC = nc;
        tvCellRef.setText(XlsxBook.indexToCol(nc) + (nr + 1));
        etFormula.setText(activeSheet().get(nr, nc));
        etFormula.selectAll();
        gridView.invalidate();
    }
    private void commitFormula() { commitAndMove(1, 0); }

    private void renderAll() {
        tabBar.removeAllViews();
        for (int i = 0; i < book.sheets.size(); i++) {
            final int idx = i;
            Button tb = makeBtn(book.sheets.get(i).name, i == curSheet ? 0xFF0078D4 : 0xFF2C2C2C);
            tb.setOnClickListener(v -> { curSheet = idx; selR = 0; selC = 0; gridView.selR = 0; gridView.selC = 0; tvCellRef.setText("A1"); etFormula.setText(activeSheet().get(0, 0)); renderAll(); });
            tabBar.addView(tb);
        }
        Button addSh = makeBtn("+Sheet", 0xFF2E7D32);
        addSh.setOnClickListener(v -> { book.sheets.add(new XlsxBook.Sheet("Sheet" + (book.sheets.size() + 1))); curSheet = book.sheets.size() - 1; renderAll(); });
        tabBar.addView(addSh);
        gridView.setSheet(activeSheet(), false);
    }

    private void showCellContextMenu(int r, int col) {
        String ref = XlsxBook.indexToCol(col) + (r + 1);
        String[] items = new String[]{"复制单元格", "粘贴到此格", "清空内容", "合并 / 拆分单元格", "行操作（第 " + (r + 1) + " 行）", "列操作（" + XlsxBook.indexToCol(col) + " 列）"};
        new AlertDialog.Builder(this).setTitle("单元格操作 (" + ref + ")")
            .setItems(items, (d, w) -> {
                XlsxBook.Sheet sh = activeSheet();
                if (w == 0) { clipText = sh.get(r, col); Toast.makeText(this, "已复制: " + clipText, Toast.LENGTH_SHORT).show(); }
                else if (w == 1) { sh.set(r, col, clipText); etFormula.setText(clipText); gridView.refreshData(); }
                else if (w == 2) { sh.set(r, col, ""); etFormula.setText(""); gridView.refreshData(); }
                else if (w == 3) showMergeDialog();
                else if (w == 4) showRowDialog(r);
                else if (w == 5) showColDialog(col);
            }).show();
    }

    private void showMergeDialog() {
        XlsxBook.Sheet sh = activeSheet();
        int[] curM = null; for (int[] m : sh.merges) if (selR >= m[0] && selR <= m[2] && selC >= m[1] && selC <= m[3]) curM = m;
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
        final int[] finalCurM = curM;
        if (finalCurM != null) {
            b.setNeutralButton("拆分取消合并", (d, w) -> {
                sh.merges.remove(finalCurM);
                renderAll();
        if (getIntent() != null && getIntent().getData() != null) handleUri(getIntent().getData());
            });
        }
        b.show();
    }

    private void showColDialog(int col) {
        String name = XlsxBook.indexToCol(col) + " 列";
        String[] items = new String[]{"加宽此列 (+30)", "变窄此列 (-25)", "左侧插入一列", "右侧插入一列", "删除此列", "末尾追加 2 列"};
        new AlertDialog.Builder(this).setTitle("列操作: " + name)
            .setItems(items, (d, w) -> {
                XlsxBook.Sheet sh = activeSheet();
                if (w == 0) gridView.colWidths.put(col, gridView.colWidths.getOrDefault(col, 105) + 30);
                else if (w == 1) gridView.colWidths.put(col, Math.max(50, gridView.colWidths.getOrDefault(col, 105) - 25));
                else if (w == 2) sh.insertCol(col);
                else if (w == 3) sh.insertCol(col + 1);
                else if (w == 4) sh.deleteCol(col);
                else if (w == 5) gridView.extraCols += 2;
                renderAll();
        if (getIntent() != null && getIntent().getData() != null) handleUri(getIntent().getData());
            }).show();
    }

    private void showRowDialog(int row) {
        String name = "第 " + (row + 1) + " 行";
        String[] items = new String[]{"加高此行 (+12)", "变矮此行 (-8)", "上方插入一行", "下方插入一行", "删除此行", "末尾追加 5 行"};
        new AlertDialog.Builder(this).setTitle("行操作: " + name)
            .setItems(items, (d, w) -> {
                XlsxBook.Sheet sh = activeSheet();
                if (w == 0) gridView.rowHeights.put(row, gridView.rowHeights.getOrDefault(row, 36) + 12);
                else if (w == 1) gridView.rowHeights.put(row, Math.max(24, gridView.rowHeights.getOrDefault(row, 36) - 8));
                else if (w == 2) sh.insertRow(row);
                else if (w == 3) sh.insertRow(row + 1);
                else if (w == 4) sh.deleteRow(row);
                else if (w == 5) gridView.extraRows += 5;
                renderAll();
        if (getIntent() != null && getIntent().getData() != null) handleUri(getIntent().getData());
            }).show();
    }

    private void showRowColMenu(int r, int col) {
        String[] items = new String[]{"调整当前行 (" + (r + 1) + "行)", "调整当前列 (" + XlsxBook.indexToCol(col) + "列)", "末尾追加 5 行", "末尾追加 2 列"};
        new AlertDialog.Builder(this).setTitle("行列调整")
            .setItems(items, (d, w) -> {
                if (w == 0) showRowDialog(r);
                else if (w == 1) showColDialog(col);
                else if (w == 2) { gridView.extraRows += 5; renderAll(); }
                else if (w == 3) { gridView.extraCols += 2; renderAll(); }
            }).show();
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 101 && res == RESULT_OK && data != null && data.getData() != null) handleUri(data.getData());
    }
    private void handleUri(android.net.Uri u) {
        if (u == null) return;
        try {
            String fn = "外部表格.xlsx";
            try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
                if (c != null && c.moveToFirst()) { int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (idx >= 0) fn = c.getString(idx); }
            }
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
                book = XlsxBook.load(in);
            }
            File dir = new File("/storage/emulated/0/1hdm"); if (!dir.exists()) dir.mkdirs();
            File dst = new File(dir, fn);
            if (dst.exists()) {
                int dot = fn.lastIndexOf(".");
                String base = dot > 0 ? fn.substring(0, dot) : fn;
                String ext = dot > 0 ? fn.substring(dot) : ".xlsx";
                int idx = 1;
                while (dst.exists()) { dst = new File(dir, base + "(" + idx++ + ")" + ext); }
                fn = dst.getName();
            }
            curFile = dst;
            curSheet = 0; selR = 0; selC = 0;
            tvTitle.setText(fn); etFormula.setText(""); selectCell(0, 0); renderAll();
        } catch (Exception e) { Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void showOpenMenu() {
        new AlertDialog.Builder(this).setTitle("打开表格").setItems(new String[]{"打开 /sdcard/1hdm/ 目录", "浏览手机全部文件夹"}, (d, w) -> {
            if (w == 0) showOpenDialog();
            else { android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT); it.addCategory(android.content.Intent.CATEGORY_OPENABLE); it.setType("*/*"); startActivityForResult(it, 101); }
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
                } catch (Exception e) { Toast.makeText(this, "打开失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
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
                curSheet = 0; tvTitle.setText(fn); selectCell(0, 0);
            }).setNegativeButton("取消", null).show();
    }

    private void saveCurrentBook() {
        if (curFile == null) { createNewBook(); return; }
        try { File tmp = new File(curFile.getParentFile(), curFile.getName() + ".tmp"); book.save(tmp); if (tmp.length() > 0) { if (curFile.exists()) curFile.delete(); tmp.renameTo(curFile); } Toast.makeText(this, "已保存: " + curFile.getName(), Toast.LENGTH_SHORT).show(); }
        catch (Exception e) { Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
}
