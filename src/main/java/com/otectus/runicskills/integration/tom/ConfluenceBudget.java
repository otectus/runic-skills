package com.otectus.runicskills.integration.tom;

/** One shared budget per originating cast, regardless of the number of projectiles or targets. */
public final class ConfluenceBudget {
    private double remaining;
    public ConfluenceBudget(double amount) {remaining=Double.isFinite(amount)?Math.max(0,Math.min(4,amount)):0;}
    public double remaining() {return remaining;}
    public double claim(double primary) {
        if(!Double.isFinite(primary) || primary<=0)return 0;
        double extra=Math.min(remaining,primary*.10);remaining-=extra;return extra;
    }
}
