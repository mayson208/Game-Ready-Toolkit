package com.mason.gamesessionprep;

import java.util.function.Supplier;

public class PrepAction {

    private final String name;
    private final String description;
    private final String category;
    private boolean selected;
    private final Supplier<PrepActionResult> executor;
    private final Supplier<Boolean> checker; // null = no check available

    public PrepAction(String name, String description, String category,
                      Supplier<PrepActionResult> executor) {
        this(name, description, category, executor, null);
    }

    public PrepAction(String name, String description, String category,
                      Supplier<PrepActionResult> executor, Supplier<Boolean> checker) {
        this.name        = name;
        this.description = description;
        this.category    = category;
        this.selected    = true;
        this.executor    = executor;
        this.checker     = checker;
    }

    // Legacy constructor without category
    public PrepAction(String name, String description,
                      Supplier<PrepActionResult> executor) {
        this(name, description, "General", executor, null);
    }

    public PrepActionResult execute() {
        try { return executor.get(); }
        catch (Exception e) { return new PrepActionResult(name, false, e.getMessage()); }
    }

    /** Returns true=applied, false=not applied, null=unknown (no checker) */
    public Boolean checkApplied() {
        if (checker == null) return null;
        try { return checker.get(); }
        catch (Exception e) { return null; }
    }

    public String  getName()        { return name; }
    public String  getDescription() { return description; }
    public String  getCategory()    { return category; }
    public boolean isSelected()     { return selected; }
    public void    setSelected(boolean selected) { this.selected = selected; }
    public boolean hasChecker()     { return checker != null; }
}
