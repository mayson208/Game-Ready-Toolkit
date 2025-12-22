package com.mason.gamesessionprep;

public class PrepAction {

    private final String name;
    private final String description;
    private final boolean enabled;

    public PrepAction(String name, String description, boolean enabled) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
