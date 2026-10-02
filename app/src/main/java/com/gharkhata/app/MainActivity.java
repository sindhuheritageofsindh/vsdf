package com.gharkhata.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PDF_REQUEST = 9001;

    private final int COLOR_TEXT = Color.rgb(20, 20, 20);
    private final int COLOR_MUTED = Color.rgb(112, 112, 112);
    private final int COLOR_BORDER = Color.rgb(225, 225, 225);
    private final int COLOR_BG = Color.rgb(248, 248, 248);

    private DBHelper db;
    private LinearLayout root;
    private String currentScreen = "home";
    private Spinner homeShopSpinner;
    private EditText nameInput;
    private EditText priceInput;

    private List<DBHelper.Expense> pendingPdfExpenses;
    private String pendingPdfTitle;
    private String pendingPdfSubtitle;
    private String pendingPdfFileName;

    private Long recordFrom = null;
    private Long recordTo = null;

    private final DecimalFormat money = new DecimalFormat("#,##0.##");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper(this);

        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(scroll);
        showHome();
    }

    private void showHome() {
        currentScreen = "home";
        root.removeAllViews();
        root.addView(topBar("GharKhata", false));
        addSpacer(32);

        nameInput = styledInput("Name");
        root.addView(nameInput);
        addSpacer(14);

        priceInput = styledInput("Price (PKR)");
        priceInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(priceInput);
        addSpacer(14);

        homeShopSpinner = styledSpinner();
        root.addView(homeShopSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ));
        refreshHomeShopSpinner();
        addSpacer(20);

        Button save = primaryButton("Save Expense");
        save.setOnClickListener(v -> saveExpense());
        root.addView(save);
    }

    private void saveExpense() {
        String item = nameInput.getText().toString().trim();
        String priceText = priceInput.getText().toString().trim();
        List<DBHelper.Shop> shops = db.getShops();

        if (item.isEmpty()) {
            nameInput.setError("Enter item name");
            return;
        }
        if (priceText.isEmpty()) {
            priceInput.setError("Enter price");
            return;
        }
        if (shops.isEmpty()) {
            Toast.makeText(this, "Add a shop first from the menu", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceText);
        } catch (NumberFormatException e) {
            priceInput.setError("Enter a valid price");
            return;
        }
        if (price <= 0) {
            priceInput.setError("Price must be above 0");
            return;
        }

        int position = homeShopSpinner.getSelectedItemPosition();
        if (position < 0 || position >= shops.size()) {
            Toast.makeText(this, "Select a shop", Toast.LENGTH_SHORT).show();
            return;
        }

        DBHelper.Shop shop = shops.get(position);
        if (db.addExpense(item, price, shop.id)) {
            nameInput.setText("");
            priceInput.setText("");
            nameInput.requestFocus();
            hideKeyboard();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Could not save expense", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshHomeShopSpinner() {
        List<DBHelper.Shop> shops = db.getShops();
        if (shops.isEmpty()) {
            ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                    new String[]{"No shops — use ☰ to add one"});
            emptyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            homeShopSpinner.setAdapter(emptyAdapter);
            return;
        }
        ArrayAdapter<DBHelper.Shop> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, shops);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        homeShopSpinner.setAdapter(adapter);
    }

    private LinearLayout topBar(String title, boolean showBack) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(4), 0, dp(4));

        TextView left = iconText(showBack ? "‹" : "☰");
        if (showBack) {
            left.setOnClickListener(v -> showHome());
        } else {
            left.setOnClickListener(this::showMainMenu);
        }
        bar.addView(left, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, dp(46), 1));

        if (showBack) {
            TextView menu = iconText("☰");
            menu.setOnClickListener(this::showMainMenu);
            bar.addView(menu, new LinearLayout.LayoutParams(dp(46), dp(46)));
        }
        return bar;
    }

    private TextView iconText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(COLOR_TEXT);
        tv.setTextSize(28);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Add Shop");
        menu.getMenu().add("Shops");
        menu.getMenu().add("Records & PDF");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Add Shop")) showAddShopDialog();
            else if (title.equals("Shops")) showShops();
            else if (title.equals("Records & PDF")) showRecords();
            return true;
        });
        menu.show();
    }

    private void showAddShopDialog() {
        EditText input = styledInput("Shop name");
        input.setSingleLine(true);
        int pad = dp(20);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(pad, dp(6), pad, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Shop")
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Enter shop name");
                return;
            }
            if (!db.addShop(name)) {
                input.setError("This shop already exists");
                return;
            }
            dialog.dismiss();
            Toast.makeText(this, "Shop added", Toast.LENGTH_SHORT).show();
            if (currentScreen.equals("home")) refreshHomeShopSpinner();
            else if (currentScreen.equals("shops")) showShops();
        }));
        dialog.show();
    }

    private void showShops() {
        currentScreen = "shops";
        root.removeAllViews();
        root.addView(topBar("Shops", true));
        addSpacer(18);

        Button add = secondaryButton("+ Add Shop");
        add.setOnClickListener(v -> showAddShopDialog());
        root.addView(add);
        addSpacer(18);

        List<DBHelper.Shop> shops = db.getShops();
        if (shops.isEmpty()) {
            root.addView(emptyText("No shops added yet."));
            return;
        }

        for (DBHelper.Shop shop : shops) {
            List<DBHelper.Expense> data = db.getExpenses(shop.id, null, null);
            double total = total(data);

            LinearLayout card = card();
            TextView name = new TextView(this);
            name.setText(shop.name);
            name.setTextColor(COLOR_TEXT);
            name.setTextSize(17);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            card.addView(name);

            TextView meta = new TextView(this);
            meta.setText(data.size() + " records  •  PKR " + money.format(total));
            meta.setTextColor(COLOR_MUTED);
            meta.setTextSize(13);
            meta.setPadding(0, dp(5), 0, 0);
            card.addView(meta);

            card.setOnClickListener(v -> showShopDetail(shop));
            root.addView(card);
            addSpacer(10);
        }
    }

    private void showShopDetail(DBHelper.Shop shop) {
        currentScreen = "shopDetail";
        root.removeAllViews();
        root.addView(topBar(shop.name, true));
        addSpacer(16);

        List<DBHelper.Expense> data = db.getExpenses(shop.id, null, null);
        TextView summary = sectionSummary("Total", "PKR " + money.format(total(data)) + "  •  " + data.size() + " records");
        root.addView(summary);
        addSpacer(14);

        Button pdf = primaryButton("Download " + shop.name + " PDF");
        pdf.setOnClickListener(v -> requestPdfExport(
                data,
                shop.name + " Expense Report",
                "All purchases from " + shop.name,
                safeFileName(shop.name) + "_expenses_" + fileDateFormat.format(new Date()) + ".pdf"
        ));
        root.addView(pdf);
        addSpacer(18);

        if (data.isEmpty()) {
            root.addView(emptyText("No purchases from this shop yet."));
        } else {
            for (DBHelper.Expense e : data) {
                root.addView(expenseRow(e, false));
                addSpacer(8);
            }
        }
    }

    private void showRecords() {
        currentScreen = "records";
        root.removeAllViews();
        root.addView(topBar("Records & PDF", true));
        addSpacer(16);

        List<DBHelper.Shop> shops = db.getShops();
        List<String> shopNames = new ArrayList<>();
        shopNames.add("All Shops");
        for (DBHelper.Shop s : shops) shopNames.add(s.name);

        Spinner shopFilter = styledSpinner();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, shopNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shopFilter.setAdapter(adapter);
        root.addView(shopFilter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        addSpacer(12);

        LinearLayout dates = new LinearLayout(this);
        dates.setOrientation(LinearLayout.HORIZONTAL);
        dates.setGravity(Gravity.CENTER_VERTICAL);

        Button fromButton = secondaryButton("From: Any");
        Button toButton = secondaryButton("To: Any");
        dates.addView(fromButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        addHorizontalSpacer(dates, 10);
        dates.addView(toButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(dates);
        addSpacer(12);

        fromButton.setOnClickListener(v -> pickDate(true, fromButton));
        toButton.setOnClickListener(v -> pickDate(false, toButton));

        Button reset = secondaryButton("Reset Dates");
        root.addView(reset);
        addSpacer(12);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button apply = secondaryButton("Apply Filters");
        Button pdf = primaryButton("Download PDF");
        actionRow.addView(apply, new LinearLayout.LayoutParams(0, dp(54), 1));
        addHorizontalSpacer(actionRow, 10);
        actionRow.addView(pdf, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(actionRow);
        addSpacer(18);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        final Runnable render = () -> {
            listContainer.removeAllViews();
            Long selectedShop = null;
            String selectedShopName = "All Shops";
            int pos = shopFilter.getSelectedItemPosition();
            if (pos > 0 && pos - 1 < shops.size()) {
                selectedShop = shops.get(pos - 1).id;
                selectedShopName = shops.get(pos - 1).name;
            }
            List<DBHelper.Expense> data = db.getExpenses(selectedShop, recordFrom, recordTo);
            listContainer.addView(sectionSummary("Filtered Total", "PKR " + money.format(total(data)) + "  •  " + data.size() + " records"));
            addSpacerTo(listContainer, 12);
            if (data.isEmpty()) {
                listContainer.addView(emptyText("No records found for these filters."));
            } else {
                for (DBHelper.Expense e : data) {
                    listContainer.addView(expenseRow(e, true));
                    addSpacerTo(listContainer, 8);
                }
            }
        };

        reset.setOnClickListener(v -> {
            recordFrom = null;
            recordTo = null;
            fromButton.setText("From: Any");
            toButton.setText("To: Any");
            render.run();
        });
        apply.setOnClickListener(v -> render.run());
        pdf.setOnClickListener(v -> {
            Long selectedShop = null;
            String selectedShopName = "All Shops";
            int pos = shopFilter.getSelectedItemPosition();
            if (pos > 0 && pos - 1 < shops.size()) {
                selectedShop = shops.get(pos - 1).id;
                selectedShopName = shops.get(pos - 1).name;
            }
            List<DBHelper.Expense> data = db.getExpenses(selectedShop, recordFrom, recordTo);
            String subtitle = buildFilterSubtitle(selectedShopName, recordFrom, recordTo);
            requestPdfExport(
                    data,
                    "Household Expense Report",
                    subtitle,
                    "GharKhata_report_" + fileDateFormat.format(new Date()) + ".pdf"
            );
        });
        render.run();
    }

    private void pickDate(boolean from, Button target) {
        Calendar c = Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            if (from) {
                selected.set(Calendar.HOUR_OF_DAY, 0);
                selected.set(Calendar.MINUTE, 0);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);
                recordFrom = selected.getTimeInMillis();
                target.setText("From: " + dateFormat.format(selected.getTime()));
            } else {
                selected.set(Calendar.HOUR_OF_DAY, 23);
                selected.set(Calendar.MINUTE, 59);
                selected.set(Calendar.SECOND, 59);
                selected.set(Calendar.MILLISECOND, 999);
                recordTo = selected.getTimeInMillis();
                target.setText("To: " + dateFormat.format(selected.getTime()));
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

    private String buildFilterSubtitle(String shop, Long from, Long to) {
        StringBuilder text = new StringBuilder("Shop: ").append(shop);
        if (from != null) text.append("  |  From: ").append(dateFormat.format(new Date(from)));
        if (to != null) text.append("  |  To: ").append(dateFormat.format(new Date(to)));
        if (from == null && to == null) text.append("  |  Date: All time");
        return text.toString();
    }

    private TextView expenseRow(DBHelper.Expense e, boolean showShop) {
        TextView row = new TextView(this);
        StringBuilder text = new StringBuilder();
        text.append(e.name).append("\n");
        text.append("PKR ").append(money.format(e.price));
        if (showShop) text.append("  •  ").append(e.shopName);
        text.append("  •  ").append(dateFormat.format(new Date(e.createdAt)));
        row.setText(text.toString());
        row.setTextColor(COLOR_TEXT);
        row.setTextSize(15);
        row.setLineSpacing(0, 1.16f);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(roundRect(Color.WHITE, COLOR_BORDER, 1, 14));
        return row;
    }

    private void requestPdfExport(List<DBHelper.Expense> data, String title, String subtitle, String fileName) {
        if (data == null || data.isEmpty()) {
            Toast.makeText(this, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingPdfExpenses = new ArrayList<>(data);
        pendingPdfTitle = title;
        pendingPdfSubtitle = subtitle;
        pendingPdfFileName = fileName;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, PDF_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PDF_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            writePdf(data.getData());
        }
    }

    private void writePdf(Uri uri) {
        if (pendingPdfExpenses == null || pendingPdfExpenses.isEmpty()) return;

        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 38;
        int y;
        int pageNumber = 1;

        PdfDocument.Page page = startPdfPage(document, pageNumber, pageWidth, pageHeight);
        y = drawPdfHeader(page, paint, margin, pendingPdfTitle, pendingPdfSubtitle, pendingPdfExpenses);

        for (DBHelper.Expense e : pendingPdfExpenses) {
            if (y > pageHeight - 60) {
                document.finishPage(page);
                pageNumber++;
                page = startPdfPage(document, pageNumber, pageWidth, pageHeight);
                y = drawPdfTableHeader(page, paint, margin, 55);
            }

            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(9.5f);
            paint.setColor(Color.rgb(45, 45, 45));
            page.getCanvas().drawText(dateFormat.format(new Date(e.createdAt)), margin, y, paint);
            page.getCanvas().drawText(trimText(e.name, 25), 118, y, paint);
            page.getCanvas().drawText(trimText(e.shopName, 18), 315, y, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            page.getCanvas().drawText("PKR " + money.format(e.price), pageWidth - margin, y, paint);
            paint.setTextAlign(Paint.Align.LEFT);

            paint.setColor(Color.rgb(230, 230, 230));
            page.getCanvas().drawLine(margin, y + 9, pageWidth - margin, y + 9, paint);
            y += 24;
        }

        paint.setColor(Color.rgb(20, 20, 20));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(11);
        paint.setTextAlign(Paint.Align.RIGHT);
        page.getCanvas().drawText("TOTAL: PKR " + money.format(total(pendingPdfExpenses)), pageWidth - margin, Math.min(y + 18, pageHeight - 28), paint);
        paint.setTextAlign(Paint.Align.LEFT);
        document.finishPage(page);

        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Unable to open file");
            document.writeTo(out);
            Toast.makeText(this, "PDF saved", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "PDF export failed", Toast.LENGTH_LONG).show();
        } finally {
            document.close();
            pendingPdfExpenses = null;
        }
    }

    private PdfDocument.Page startPdfPage(PdfDocument document, int pageNumber, int width, int height) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(width, height, pageNumber).create();
        return document.startPage(info);
    }

    private int drawPdfHeader(PdfDocument.Page page, Paint paint, int margin, String title, String subtitle, List<DBHelper.Expense> data) {
        int pageWidth = page.getInfo().getPageWidth();
        paint.setColor(Color.rgb(20, 20, 20));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(20);
        page.getCanvas().drawText(title, margin, 48, paint);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(9.5f);
        paint.setColor(Color.rgb(100, 100, 100));
        page.getCanvas().drawText(trimText(subtitle, 80), margin, 68, paint);
        page.getCanvas().drawText("Generated: " + dateFormat.format(new Date()), margin, 84, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(Color.rgb(20, 20, 20));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(12);
        page.getCanvas().drawText("PKR " + money.format(total(data)), pageWidth - margin, 49, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(9);
        paint.setColor(Color.rgb(110, 110, 110));
        paint.setTextAlign(Paint.Align.RIGHT);
        page.getCanvas().drawText(data.size() + " records", pageWidth - margin, 67, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        return drawPdfTableHeader(page, paint, margin, 112);
    }

    private int drawPdfTableHeader(PdfDocument.Page page, Paint paint, int margin, int y) {
        int pageWidth = page.getInfo().getPageWidth();
        paint.setColor(Color.rgb(30, 30, 30));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(9.5f);
        page.getCanvas().drawText("DATE", margin, y, paint);
        page.getCanvas().drawText("ITEM", 118, y, paint);
        page.getCanvas().drawText("SHOP", 315, y, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        page.getCanvas().drawText("AMOUNT", pageWidth - margin, y, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(190, 190, 190));
        page.getCanvas().drawLine(margin, y + 8, pageWidth - margin, y + 8, paint);
        return y + 28;
    }

    private double total(List<DBHelper.Expense> data) {
        double value = 0;
        for (DBHelper.Expense e : data) value += e.price;
        return value;
    }

    private String trimText(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String safeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9_-]+", "_");
    }

    private EditText styledInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(145, 145, 145));
        input.setTextColor(COLOR_TEXT);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setBackground(roundRect(Color.WHITE, COLOR_BORDER, 1, 14));
        input.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return input;
    }

    private Spinner styledSpinner() {
        Spinner spinner = new Spinner(this);
        spinner.setPadding(dp(12), 0, dp(12), 0);
        spinner.setBackground(roundRect(Color.WHITE, COLOR_BORDER, 1, 14));
        return spinner;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundRect(COLOR_TEXT, COLOR_TEXT, 0, 14));
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(roundRect(Color.WHITE, COLOR_BORDER, 1, 14));
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(roundRect(Color.WHITE, COLOR_BORDER, 1, 14));
        card.setClickable(true);
        card.setFocusable(true);
        return card;
    }

    private TextView sectionSummary(String label, String value) {
        TextView text = new TextView(this);
        text.setText(label + "\n" + value);
        text.setTextColor(COLOR_TEXT);
        text.setTextSize(15);
        text.setLineSpacing(0, 1.15f);
        text.setPadding(dp(16), dp(14), dp(16), dp(14));
        text.setBackground(roundRect(Color.WHITE, COLOR_BORDER, 1, 14));
        return text;
    }

    private TextView emptyText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(COLOR_MUTED);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(30), dp(12), dp(30));
        return tv;
    }

    private GradientDrawable roundRect(int fillColor, int strokeColor, int strokeWidthDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private void addSpacer(int dp) {
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private void addSpacerTo(LinearLayout layout, int sizeDp) {
        View spacer = new View(this);
        layout.addView(spacer, new LinearLayout.LayoutParams(1, dp(sizeDp)));
    }

    private void addHorizontalSpacer(LinearLayout layout, int sizeDp) {
        View spacer = new View(this);
        layout.addView(spacer, new LinearLayout.LayoutParams(dp(sizeDp), 1));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        if ("home".equals(currentScreen)) super.onBackPressed();
        else showHome();
    }
}
