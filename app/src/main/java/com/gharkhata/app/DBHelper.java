package com.gharkhata.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "gharkhata.db";
    private static final int DB_VERSION = 1;

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE shops (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, price REAL NOT NULL, shop_id INTEGER NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(shop_id) REFERENCES shops(id))");
        db.execSQL("CREATE INDEX idx_expenses_shop ON expenses(shop_id)");
        db.execSQL("CREATE INDEX idx_expenses_date ON expenses(created_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS expenses");
        db.execSQL("DROP TABLE IF EXISTS shops");
        onCreate(db);
    }

    public boolean addShop(String name) {
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("created_at", System.currentTimeMillis());
        try {
            return getWritableDatabase().insertOrThrow("shops", null, values) != -1;
        } catch (Exception ignored) {
            return false;
        }
    }

    public List<Shop> getShops() {
        List<Shop> shops = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id, name FROM shops ORDER BY name COLLATE NOCASE", null);
        try {
            while (c.moveToNext()) {
                shops.add(new Shop(c.getLong(0), c.getString(1)));
            }
        } finally {
            c.close();
        }
        return shops;
    }

    public boolean addExpense(String name, double price, long shopId) {
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("price", price);
        values.put("shop_id", shopId);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("expenses", null, values) != -1;
    }

    public List<Expense> getExpenses(Long shopId, Long fromMillis, Long toMillis) {
        List<Expense> expenses = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT e.id, e.name, e.price, e.shop_id, s.name, e.created_at " +
                "FROM expenses e JOIN shops s ON s.id = e.shop_id WHERE 1=1"
        );
        List<String> args = new ArrayList<>();

        if (shopId != null) {
            sql.append(" AND e.shop_id = ?");
            args.add(String.valueOf(shopId));
        }
        if (fromMillis != null) {
            sql.append(" AND e.created_at >= ?");
            args.add(String.valueOf(fromMillis));
        }
        if (toMillis != null) {
            sql.append(" AND e.created_at <= ?");
            args.add(String.valueOf(toMillis));
        }
        sql.append(" ORDER BY e.created_at DESC, e.id DESC");

        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        try {
            while (c.moveToNext()) {
                expenses.add(new Expense(
                        c.getLong(0),
                        c.getString(1),
                        c.getDouble(2),
                        c.getLong(3),
                        c.getString(4),
                        c.getLong(5)
                ));
            }
        } finally {
            c.close();
        }
        return expenses;
    }

    public static class Shop {
        public final long id;
        public final String name;

        public Shop(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class Expense {
        public final long id;
        public final String name;
        public final double price;
        public final long shopId;
        public final String shopName;
        public final long createdAt;

        public Expense(long id, String name, double price, long shopId, String shopName, long createdAt) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.shopId = shopId;
            this.shopName = shopName;
            this.createdAt = createdAt;
        }
    }
}
