package com.group6.wastewise.model;

/** No test dependency required: run with test.ps1 or build.ps1 -Test. */
public final class WasteTrackerTest {
    private static int checks;
    public static void main(String[] args) {
        WasteTracker t = new WasteTracker();
        check(t.viewWaste().isEmpty(), "Initially empty"); eq(t.calculateTotalWeight(),0);
        Waste bio=t.addWaste("Food scraps",Category.BIODEGRADABLE,2,"Cafeteria");
        Waste rec=t.addWaste("Plastic bottles",Category.RECYCLABLE,3,"Lobby");
        t.addWaste("Used sachets",Category.RESIDUAL,1,"Bin C");
        check(bio instanceof BiodegradableWaste,"Subclass creation"); check(rec instanceof RecyclableWaste,"Subclass creation");
        eq(t.calculateTotalWeight(),6); eq(t.calculateCategoryWeight(Category.BIODEGRADABLE),2); eq(t.calculateCategoryWeight(Category.RECYCLABLE),3); eq(t.calculateCategoryWeight(Category.RESIDUAL),1);
        check(t.search("CAFETERIA",null).size()==1,"Case-insensitive location search");
        check(t.search("bottles",Category.RECYCLABLE).size()==1,"Combined search and category");
        check(t.search("bottles",Category.BIODEGRADABLE).isEmpty(),"Combined filter excludes other category");
        check(t.removeWaste(rec.getId()),"Remove by stable ID"); eq(t.calculateTotalWeight(),3); eq(t.calculateCategoryWeight(Category.RECYCLABLE),0);
        check(t.undoLastDelete(),"Undo succeeds"); check(t.viewWaste().get(1).getId()==rec.getId(),"Undo preserves position and ID"); check(!t.undoLastDelete(),"Undo cannot duplicate");
        check(!t.removeWaste(999),"Missing ID handled");
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,1_000_001}) reject(() -> t.addWaste("x",Category.RESIDUAL,invalid,"y"));
        reject(() -> t.addWaste(" ",Category.RESIDUAL,1,"y")); reject(() -> t.addWaste("x",null,1,"y")); reject(() -> t.addWaste("x",Category.RESIDUAL,1," "));
        reject(() -> bio.setWeight(-1)); eq(bio.getWeight(),2);
        try { t.viewWaste().clear(); throw new AssertionError("Mutable list leaked"); } catch(UnsupportedOperationException expected) { checks++; }
        t.removeWaste(bio.getId()); t.clear(); check(!t.undoLastDelete(),"Clear invalidates undo"); eq(t.calculateTotalWeight(),0);
        Waste tiny=t.addWaste("Small item",Category.RECYCLABLE,.001,"Lab"); eq(tiny.getWeight(),.001);
        t.clear(); t.loadDemoRecords(); eq(t.calculateTotalWeight(),2.7); t.loadDemoRecords(); check(t.viewWaste().size()==5,"Demo does not duplicate");
        System.out.println("PASS: " + checks + " model checks");
    }
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    private static void eq(double actual,double expected) { check(Math.abs(actual-expected)<1e-9,"Expected " + expected + " got " + actual); }
    private static void reject(Runnable action) { try { action.run(); throw new AssertionError("Invalid input accepted"); } catch(IllegalArgumentException expected) { checks++; } }
}
