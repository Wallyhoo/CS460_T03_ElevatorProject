package T02ButtonPanels.java.Panel;

import java.util.Locale;

/**
 * Direction status used by both panels to keep track of where elevators are at.
 * @author Amber Smith
 */
public enum Direction {
    IDLE("IDLE"),
    UP("UP"),
    DOWN("DOWN");

    private final String displayText;

    Direction(String displayText) {
        this.displayText = displayText;
    }

    // get the display text for the direction
    public String getDisplayText() { return displayText; }

    // get a direction value from a string input
    public static Direction fromText(String value) {
        if (value == null) throw new IllegalArgumentException("Direction cannot be null.");

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "up", "going up" -> UP;
            case "down", "going down" -> DOWN;
            case "idle", "stopped", "stop", "" -> IDLE;
            default -> throw new IllegalArgumentException("Direction must be UP, DOWN, or IDLE: " + value);
        };
    }
}