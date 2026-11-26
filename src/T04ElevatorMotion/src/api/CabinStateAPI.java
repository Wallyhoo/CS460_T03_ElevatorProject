package T04ElevatorMotion.src.api;

/**
 * Defines read-only access to a cabin’s motion state and snapshot.
 */
public interface CabinStateAPI {

    double getPosition();       //ft
    double getVelocity();       //ft/s
    Direction getDirection();
    boolean isMoving();
    MotionState getState();     //returns current motion state
    CabinSnapshot snapshot();   //returns immutable snapshot of current state

}
