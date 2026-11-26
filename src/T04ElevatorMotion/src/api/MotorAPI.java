package T04ElevatorMotion.src.api;

import api.Direction;

/**
 * Defines commands for starting, stopping, and stepping the motor.
 */
public interface MotorAPI {

    void startMotor(Direction direction);
    void stopMotor();
    void stepUpMotor();         //Applies one upward motor-controlled step
    void stepDnMotor();         //Applies one downward motor-controlled step

}
