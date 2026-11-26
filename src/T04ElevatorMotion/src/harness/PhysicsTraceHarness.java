package T04ElevatorMotion.src.harness;

import api.MotionState;
import manager.TripController;
import sim.MotionEngine;

/**
 * Prints one trip tick by tick so the physics can be seen: position, speed, state, distance
 * remaining and the braking distance the controller compares it against. No GUI, run main().
 * It steps the same way SimulationManager does: controlStep() first, then tick().
 */
public class PhysicsTraceHarness {

    public static void main(String[] args) {
        int fromFloor = 1;
        int toFloor = 2;
        if (args.length >= 2) {
            fromFloor = Integer.parseInt(args[0]);
            toFloor = Integer.parseInt(args[1]);
        }
        MotionEngine engine = new MotionEngine(MotionEngine.floorPosition(fromFloor));
        TripController trip = new TripController(engine);
        double target = MotionEngine.floorPosition(toFloor);

        System.out.printf("TIME_STEP %.4f s, accel %.1f, decel %.1f, cruise %.1f ft/s%n",
                MotionEngine.TIME_STEP, MotionEngine.ACCELERATION, MotionEngine.DECELERATION,
                MotionEngine.CRUISE_SPEED);
        System.out.printf("Ideal braking distance at cruise: %.3f ft%n",
                MotionEngine.brakingDistance(MotionEngine.CRUISE_SPEED));
        System.out.println("   time   position   speed   state         remaining  brakeDist");

        trip.goToFloor(toFloor);
        double time = 0.0;
        boolean firstStopReported = false;
        int tick = 0;
        while (tick < 20000) {
            trip.controlStep();
            engine.tick();
            tick += 1;
            time += MotionEngine.TIME_STEP;
            double remaining = Math.abs(target - engine.getPosition());
            //Print every 10th tick, plus every tick while braking or stopping.
            boolean busy = engine.getState() == MotionState.DECELERATING
                    || engine.getState() == MotionState.STOPPED;
            if (tick % 10 == 0 || busy) {
                System.out.printf("%7.2f %9.3f %7.3f   %-12s %9.3f %10.3f%n", time, engine.getPosition(),
                        engine.getVelocity(), engine.getState(), remaining,
                        MotionEngine.brakingDistance(engine.getVelocity()));
            }
            if (engine.getState() == MotionState.STOPPED && firstStopReported == false) {
                firstStopReported = true;
                System.out.printf("FIRST STOP at %.3f ft, target %.3f ft: overshoot %.3f ft (%.2f steps)%n",
                        engine.getPosition(), target, engine.getPosition() - target,
                        (engine.getPosition() - target) / MotionEngine.STEP_SIZE);
            }
            if (trip.isTripActive() == false && firstStopReported) {
                break;
            }
        }
        System.out.printf("Final position %.3f ft, error %.3f ft (tolerance %.2f ft)%n",
                engine.getPosition(), engine.getPosition() - target, TripController.levelTolerance);
    }
}