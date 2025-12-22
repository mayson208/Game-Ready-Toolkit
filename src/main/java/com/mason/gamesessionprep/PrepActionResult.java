package com.mason.gamesessionprep;

public class PrepActionResult {

    private final String actionName;
    private final boolean success;
    private final String message;

    public PrepActionResult(String actionName, boolean success, String message) {
        this.actionName = actionName;
        this.success = success;
        this.message = message;
    }

    public String getActionName() {
        return actionName;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
