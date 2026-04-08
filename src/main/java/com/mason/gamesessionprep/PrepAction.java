package com.mason.gamesessionprep;

import java.util.function.Supplier;

public class PrepAction {

    private final String name;
    private final String description;
    private final String category;
    private boolean selected;
    private final Supplier<PrepActionResult> executor;

    public PrepAction(String name, String description, String category,
                      Supplier<PrepActionResult> executor) {
        this.name        = name;
        this.description = description;
        this.category    = category;
        this.selected    = true;
        this.executor    = executor;
    }

    // Legacy constructor without category (defaults to "General")
    public PrepAction(String name, String description,
                      Supplier<PrepActionResult> executor) {
        this(name, description, "General", executor);
    }

    public PrepActionResult execute() {
        try { return executor.get(); }
        catch (Exception e) { return new PrepActionResult(name, false, e.getMessage()); }
    }

    public String  getName()        { return name; }
    public String  getDescription() { return description; }
    public String  getCategory()    { return category; }
    public boolean isSelected()     { return selected; }
    public void    setSelected(boolean selected) { this.selected = selected; }
}
