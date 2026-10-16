package com.group6.wastewise.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** All records live in this ArrayList. No database or file persistence. */
public final class WasteTracker {
    private final ArrayList<Waste> wasteList = new ArrayList<>();
    private int nextId = 101;
    private Waste lastDeleted;
    private int lastDeletedIndex;

    public Waste addWaste(String name, Category category, double weight, String location) {
        if (category == null) throw new IllegalArgumentException("Choose a waste category.");
        Waste waste = switch (category) {
            case BIODEGRADABLE -> new BiodegradableWaste(nextId, name, weight, location);
            case RECYCLABLE -> new RecyclableWaste(nextId, name, weight, location);
            case RESIDUAL -> new ResidualWaste(nextId, name, weight, location);
        };
        wasteList.add(waste); nextId++; return waste;
    }
    public List<Waste> viewWaste() { return List.copyOf(wasteList); }
    public double calculateTotalWeight() { return wasteList.stream().mapToDouble(Waste::getWeight).sum(); }
    public double calculateCategoryWeight(Category category) {
        return wasteList.stream().filter(w -> w.getCategory() == category).mapToDouble(Waste::getWeight).sum();
    }
    public long count(Category category) { return wasteList.stream().filter(w -> w.getCategory() == category).count(); }
    public List<Waste> search(String query, Category category) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return wasteList.stream().filter(w -> category == null || w.getCategory() == category)
            .filter(w -> w.getWasteName().toLowerCase(Locale.ROOT).contains(q) || w.getLocation().toLowerCase(Locale.ROOT).contains(q)).toList();
    }
    public boolean removeWaste(int id) {
        for (int i = 0; i < wasteList.size(); i++) {
            if (wasteList.get(i).getId() == id) {
                lastDeletedIndex = i; lastDeleted = wasteList.remove(i); return true;
            }
        }
        return false;
    }
    public boolean undoLastDelete() {
        if (lastDeleted == null) return false;
        wasteList.add(Math.min(lastDeletedIndex, wasteList.size()), lastDeleted); lastDeleted = null; return true;
    }
    public boolean canUndo() { return lastDeleted != null; }
    public void clear() { wasteList.clear(); lastDeleted = null; }
    public void loadDemoRecords() {
        if (!wasteList.isEmpty()) return;
        addWaste("Banana Peel & Organic Waste", Category.BIODEGRADABLE, .4, "Cafeteria Bin A");
        addWaste("Corrugated Shipping Box", Category.RECYCLABLE, 1.2, "Mailroom Desk");
        addWaste("Styrofoam Container", Category.RESIDUAL, .1, "Breakroom Bin");
        addWaste("Coffee Compost Grounds", Category.BIODEGRADABLE, .2, "Kitchen Sink");
        addWaste("Glass Water Bottle", Category.RECYCLABLE, .8, "Lobby Recycling");
    }
}
