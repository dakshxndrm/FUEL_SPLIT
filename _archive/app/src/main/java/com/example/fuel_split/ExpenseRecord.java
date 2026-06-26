package com.example.fuel_split;

public class ExpenseRecord {
    public String description;
    public long amountPaise;
    public String paidBy;
    public long timestamp;

    public ExpenseRecord() {}

    public ExpenseRecord(String description, long amountPaise, String paidBy, long timestamp) {
        this.description = description;
        this.amountPaise = amountPaise;
        this.paidBy      = paidBy;
        this.timestamp   = timestamp;
    }
}
