package com.mason.gamesessionprep;

public class PrepAction {

    private final String name;
    private final String description;
    private boolean selected;

    public PrepAction(String name, String description) {
        this.name = name;
        this.description = description;
        this.selected = true;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
