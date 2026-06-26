package com.example.fuel_split;

import java.io.Serializable;
import java.util.Map;

public class Trip implements Serializable {
    private String tripName, distance, fuel, total, members, paidBy;
    private Map<String, Double> allShares;

    // On-chain / store fields (nullable/default when loaded from old data)
    private String groupAddress;
    private long   expenseId      = -1;
    private boolean settled       = false;
    private long   timestampMillis;

    public Trip(String tripName, String distance, String fuel, String total,
                String members, Map<String, Double> allShares, String paidBy) {
        this.tripName       = tripName;
        this.distance       = distance;
        this.fuel           = fuel;
        this.total          = total;
        this.members        = members;
        this.allShares      = allShares;
        this.paidBy         = paidBy;
        this.timestampMillis = System.currentTimeMillis();
    }

    public String getTripName()              { return tripName; }
    public String getDistance()              { return distance; }
    public String getFuel()                  { return fuel; }
    public String getTotal()                 { return total; }
    public String getMembers()               { return members; }
    public Map<String, Double> getAllShares() { return allShares; }
    public String getPaidBy()                { return paidBy; }
    public String getGroupAddress()          { return groupAddress; }
    // ARCHIVED: getExpenseId() — expenseId field never written after construction, never read
    // public long getExpenseId() { return expenseId; }
    public boolean isSettled()               { return settled; }
    public long   getTimestampMillis()       { return timestampMillis; }

    public void setGroupAddress(String groupAddress) { this.groupAddress = groupAddress; }
    // ARCHIVED: setExpenseId() — AddTripActivity never calls this after saving on-chain, never called
    // public void setExpenseId(long expenseId) { this.expenseId = expenseId; }
    public void setSettled(boolean settled)          { this.settled = settled; }
    public void setTimestampMillis(long ms)          { this.timestampMillis = ms; }
}