package T04ElevatorMotion.src.api;


/**
 * Defines access to the top and bottom safety-limit switches.
 */
public interface SafetyLeverAPI {

    /** True once the car has hit the safety lever above floor 10. */
    default boolean isTopLimitTripped() { return false; }

    /** True once the car has hit the safety lever below floor 1. */
    default boolean isBottomLimitTripped() { return false; }

}
