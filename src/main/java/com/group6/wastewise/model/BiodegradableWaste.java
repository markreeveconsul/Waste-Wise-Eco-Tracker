package com.group6.wastewise.model;
public final class BiodegradableWaste extends Waste {
    public BiodegradableWaste(int id, String name, double weight, String location) { super(id, name, weight, location); }
    @Override public Category getCategory() { return Category.BIODEGRADABLE; }
}
