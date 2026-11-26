package T04ElevatorMotion.src.checks;

import Utilities.Message;
import assembly.CabinAssembly;
import bus.BusCodes;
import bus.BusMessageDispatcher;
import bus.BusStandIn;

import java.util.ArrayList;
import java.util.List;

/** Checks for four cars sharing one bus. */
public class CabinAssemblyChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) {
        callMovesOnlyThatCar();
        everyCarServesItsOwnCall();
        invalidNumberRejected();

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Creates the cabin assemblies used by the checks.
     *
     * @param bus software-bus instance
     * @return the resulting List value
     */
    private static List<CabinAssembly> buildCars(BusStandIn bus) {
        List<CabinAssembly> cars = new ArrayList<>();
        for (int n = BusCodes.ELEVATOR_1; n <= BusCodes.ELEVATOR_4; n++) {
            cars.add(new CabinAssembly(n, bus.connect()));
        }
        return cars;
    }

    /**
     * Runs the component’s main processing loop. It runs on the calling or worker thread.
     *
     * @param cars cabin assemblies being checked
     * @param ticks number of simulation ticks
     */
    private static void run(List<CabinAssembly> cars, int ticks) {
        for (int i = 0; i < ticks; i++) {
            for (CabinAssembly car : cars) {
                car.step();
            }
        }
    }

    /**
     * Checks that a call moves only its addressed cabin.
     */
    private static void callMovesOnlyThatCar() {
        BusStandIn bus = new BusStandIn();
        List<CabinAssembly> cars = buildCars(bus);
        BusMessageDispatcher center = bus.connect();
        run(cars, 1);

        center.publish(Message.ofPayload(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_2, 5));
        run(cars, CheckTime.scaled(2400));

        check(cars.get(1).engine().isFloorAligned(5), "car 2 went to floor 5");
        check(cars.get(0).engine().isFloorAligned(1), "car 1 stayed at floor 1");
        check(cars.get(2).engine().isFloorAligned(1), "car 3 stayed at floor 1");
        check(cars.get(3).engine().isFloorAligned(1), "car 4 stayed at floor 1");
    }

    /**
     * Checks that each cabin serves its own call.
     */
    private static void everyCarServesItsOwnCall() {
        BusStandIn bus = new BusStandIn();
        List<CabinAssembly> cars = buildCars(bus);
        BusMessageDispatcher center = bus.connect();
        run(cars, 1);

        int[] floors = {3, 6, 9, 2};
        for (int i = 0; i < 4; i++) {
            center.publish(Message.ofPayload(BusCodes.CABIN_CALL, i + 1, floors[i]));
        }
        run(cars, CheckTime.scaled(2400));

        for (int i = 0; i < 4; i++) {
            check(cars.get(i).engine().isFloorAligned(floors[i]),
                    "car " + (i + 1) + " ends at floor " + floors[i]);
        }
    }

    /**
     * Checks that an invalid elevator number is rejected.
     */
    private static void invalidNumberRejected() {
        BusStandIn bus = new BusStandIn();
        boolean rejected = false;
        try {
            new CabinAssembly(5, bus.connect());
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, "car number 5 is rejected");
    }

    /**
     * Asserts that the supplied condition is true and reports the message if it is false.
     *
     * @param condition condition that must be true for the check to pass
     * @param what description printed when the check fails
     */
    private static void check(boolean condition, String what) {
        checksRun++;
        if (!condition) {
            checksFailed++;
            System.out.println("FAIL: " + what);
        }
    }
}