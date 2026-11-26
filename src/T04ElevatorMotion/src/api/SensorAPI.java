package T04ElevatorMotion.src.api;

/**
 * Defines access to floor-alignment and end-of-shaft sensors.
 */
public interface SensorAPI {

    boolean isTopAligned();
    boolean isBottomAligned();
    boolean isFloorAligned(int floor);

}
