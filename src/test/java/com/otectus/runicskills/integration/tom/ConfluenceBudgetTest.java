package com.otectus.runicskills.integration.tom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfluenceBudgetTest {
    @Test void fortyProjectilesShareFourHealthTotal() {
        var budget=new ConfluenceBudget(4);double total=0;
        for(int i=0;i<40;i++)total+=budget.claim(10);
        assertEquals(4,total,1e-9);assertEquals(0,budget.remaining(),1e-9);
    }
    @Test void canceledAndMalformedHitsDoNotSpend() {
        var budget=new ConfluenceBudget(4);
        for(double damage:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertEquals(0,budget.claim(damage));
        assertEquals(4,budget.remaining());assertEquals(.25,budget.claim(2.5));assertEquals(3.75,budget.remaining());
    }
    @Test void oneLargeHitAndUnchargedCastStayBounded() {
        assertEquals(4,new ConfluenceBudget(1000).claim(1000));
        assertEquals(0,new ConfluenceBudget(0).claim(1000));
    }
}
