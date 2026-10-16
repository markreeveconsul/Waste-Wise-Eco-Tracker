package com.group6.wastewise.model;

/** Abstract shared model: subclasses supply the category through polymorphism. */
public abstract class Waste {
    private final int id;
    private String wasteName;
    private double weight;
    private String location;

    protected Waste(int id, String wasteName, double weight, String location) {
        if (id <= 0) throw new IllegalArgumentException("Record ID must be positive.");
        this.id = id;
        setWasteName(wasteName); setWeight(weight); setLocation(location);
    }
    public int getId() { return id; }
    public String getWasteName() { return wasteName; }
    public double getWeight() { return weight; }
    public String getLocation() { return location; }
    public void setWasteName(String name) { wasteName = required(name, "Waste name"); }
    public void setLocation(String value) { location = required(value, "Location"); }
    public void setWeight(double value) {
        if (!Double.isFinite(value) || value <= 0 || value > 1_000_000)
            throw new IllegalArgumentException("Enter a weight above 0 and at most 1,000,000 kg.");
        weight = value;
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        if (value.strip().length() > 120) throw new IllegalArgumentException(field + " must be at most 120 characters.");
        return value.strip();
    }
    public abstract Category getCategory();
    public String getWasteInfo() { return wasteName + " | " + getCategory() + " | " + weight + " kg | " + location; }
    public String displayInfo() { return getWasteInfo(); }
}
