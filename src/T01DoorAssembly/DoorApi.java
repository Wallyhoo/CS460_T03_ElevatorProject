package T01DoorAssembly;

/**
 * Standard interface for Elevator Door Assembly (CS460 Team #01).
 * Defines the 5 core APIs required for door control and telemetry.
 */
public interface DoorApi {
    /**
     * Commands the door assembly to open.
     */
    void open();

    /**
     * Commands the door assembly to close.
     */
    void close();

    /**
     * Queries whether the door is fully open.
     */
    boolean isFullyOpen();

    /**
     * Queries whether the door is fully closed.
     */
    boolean isFullyClosed();

    /**
     * Queries whether an obstruction is detected in the doorway.
     */
    boolean hasObstruction();
}
