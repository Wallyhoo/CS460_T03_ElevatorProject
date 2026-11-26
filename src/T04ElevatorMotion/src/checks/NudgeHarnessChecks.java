package T04ElevatorMotion.src.checks;

import bus.BusCabinGateway;
import bus.BusCodes;
import bus.BusStandIn;
import harness.NudgeHarness;
import manager.TripController;
import sim.FloorSensors;
import sim.MotionEngine;

import java.util.List;

/**
 * Checks nudging behavior and brake-delay handling.
 */
public class NudgeHarnessChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) {
        nudgeScenarioChecks();
        brakeDelayChecks();

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Checks the nudging scenario from start to completion.
     */
    private static void nudgeScenarioChecks() {
        MotionEngine engine = new MotionEngine();
        TripController trip = new TripController(engine);
        BusStandIn bus = new BusStandIn();
        BusCabinGateway gateway = new BusCabinGateway(engine, trip, bus.connect(), BusCodes.ELEVATOR_1);
        NudgeHarness scenario = new NudgeHarness(bus.connect(), trip);
        int[] route = NudgeHarness.FLOORS;
        boolean overshootExpected = NudgeHarness.BRAKE_DELAY_FEET > FloorSensors.ZONE_HEIGHT / 2.0;

        boolean wasMoving = false;
        boolean stoppedPast = false;   //true once the car has stopped for the current leg
        double stopOffset = 0;
        int nudgeSteps = 0;
        boolean nudgeFloorAlwaysTarget = true;
        double lastPosition = engine.getPosition();
        int ticks = 0;

        while (scenario.isFinished() == false && ticks < CheckTime.scaled(40000)) {
            int leg = Math.min(scenario.getArrivals().size(), route.length - 1);   //trip in progress
            double target = MotionEngine.floorPosition(route[leg]);

            scenario.update();
            gateway.update();
            trip.controlStep();
            engine.tick();
            ticks += 1;

            if (engine.isMoving()) {
                wasMoving = true;
            } else if (wasMoving && stoppedPast == false && trip.isTripActive()) {
                stoppedPast = true;
                stopOffset = Math.abs(engine.getPosition() - target);
                lastPosition = engine.getPosition();
            } else if (stoppedPast && Math.abs(engine.getPosition() - lastPosition) > 1e-9) {
                nudgeSteps += 1;
                lastPosition = engine.getPosition();
            }

            if (stoppedPast && trip.isTripActive() && engine.isMoving() == false
                    && Math.abs(engine.getPosition() - target) > 0.11) {
                if (trip.getNudgingFloor() != route[leg]) {
                    nudgeFloorAlwaysTarget = false;
                }
            }

            if (scenario.getArrivals().size() > leg) {   //this trip just finished
                String name = "leg to floor " + route[leg];
                //The scenario can only push the car out of the sensor zone when the car can brake over a distance
                //longer than the zone. At very low speeds it stops inside the zone and nothing needs nudging.
                if (overshootExpected) {
                    check(stopOffset > FloorSensors.ZONE_HEIGHT / 2.0,
                            name + ": the car stopped past the sensor zone (" + stopOffset + " ft)");
                }
                check(stopOffset < MotionEngine.FLOOR_HEIGHT / 2.0, name +
                        ": the car stopped nearer the target floor than any other (" + stopOffset + " ft)");
                if (overshootExpected) {
                    check(nudgeSteps >= 2, name +
                            ": the car was nudged back (" + nudgeSteps + " steps)");
                }
                check(nudgeFloorAlwaysTarget, name + ": getNudgingFloor named the target floor during nudging");
                check(Math.abs(engine.getPosition() - target) <= 0.11, name + ": the car ended level");
                check(trip.getNudgingFloor() == 0, name + ": nudging is over after arrival");
                System.out.printf("floor %d: stopped %.2f ft past, %d nudges, ended %.2f ft off%n",
                        route[leg], stopOffset, nudgeSteps, engine.getPosition() - target);
                wasMoving = false;
                stoppedPast = false;
                nudgeSteps = 0;
                nudgeFloorAlwaysTarget = true;
            }
        }

        check(scenario.isFinished(), "nudge scenario finished before the tick limit");
        check(scenario.getArrivals().equals(List.of(4, 8, 3, 6)), "arrivals " + scenario.getArrivals());
        check(engine.isTopLimitTripped() == false && engine.isBottomLimitTripped() == false,
                "no safety lever was tripped");
    }

    /**
     * Checks the effect of the configured brake delay.
     */
    private static void brakeDelayChecks() {
        MotionEngine engine = new MotionEngine();
        TripController trip = new TripController(engine);
        check(trip.getNudgingFloor() == 0, "idle car is not nudging");

        boolean rejected = false;
        try {
            trip.setBrakeDelay(-0.1);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, "negative brake delay is rejected");

        // normal braking (delay 0): the car is still correct and ends level
        trip.goToFloor(3);
        int ticks = 0;
        while (trip.isTripActive() && ticks < CheckTime.scaled(20000)) {
            trip.controlStep();
            engine.tick();
            ticks += 1;
        }
        check(Math.abs(engine.getPosition() - MotionEngine.floorPosition(3)) <= 0.11, "delay 0 ends level");
        check(trip.getNudgingFloor() == 0, "no nudging after a normal arrival");
    }

    /**
     * Asserts that the supplied condition is true and reports the message if it is false.
     *
     * @param condition condition that must be true for the check to pass
     * @param message description printed when the check fails
     */
    private static void check(boolean condition, String message) {
        checksRun++;
        if (!condition) {
            checksFailed++;
            System.out.println("FAIL: " + message);
        }
    }
}