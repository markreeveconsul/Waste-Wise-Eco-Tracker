package com.group6.wastewise.model;

public enum Category {
    BIODEGRADABLE("Biodegradable", "bio"), RECYCLABLE("Recyclable", "rec"), RESIDUAL("Residual", "res");
    private final String label;
    private final String style;
    Category(String label, String style) { this.label = label; this.style = style; }
    public String style() { return style; }
    @Override public String toString() { return label; }
}
