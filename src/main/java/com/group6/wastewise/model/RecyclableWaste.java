package com.group6.wastewise.model;
public final class RecyclableWaste extends Waste {
    public RecyclableWaste(int id, String name, double weight, String location) { super(id, name, weight, location); }
    @Override public Category getCategory() { return Category.RECYCLABLE; }
}
