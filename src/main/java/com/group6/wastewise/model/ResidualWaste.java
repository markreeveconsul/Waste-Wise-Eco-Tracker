package com.group6.wastewise.model;
public final class ResidualWaste extends Waste {
    public ResidualWaste(int id, String name, double weight, String location) { super(id, name, weight, location); }
    @Override public Category getCategory() { return Category.RESIDUAL; }
}
