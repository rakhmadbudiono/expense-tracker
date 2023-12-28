package com.rakhmadbudiono.expensetracker;

import java.util.Date;

public class Expense {
    private double amount;
    private String description;
    private String category;
    private String importance;
    private Date date;

    public Expense(double amount, String description, String category, String importance) {
        this.amount = amount;
        this.description = description;
        this.category = category;
        this.importance = importance;
        this.date = new Date();
    }

    public Expense(double amount, String description) {
        this.amount = amount;
        this.description = description;
        this.date = new Date();
    }

    public String toCsvString() {
        return String.format("%s,%.2f,%s,%s", category, amount, importance, description);
    }

    public Date getDate() {
        return date;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }
}
