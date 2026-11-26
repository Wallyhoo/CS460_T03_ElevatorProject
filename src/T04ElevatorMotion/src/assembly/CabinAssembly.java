package T04ElevatorMotion.src.assembly;

import T04ElevatorMotion.src.bus.BusCabinGateway;
import T04ElevatorMotion.src.bus.BusMessageDispatcher;
import T04ElevatorMotion.src.manager.TripController;
import T04ElevatorMotion.src.sim.MotionEngine;

/**
 * One complete elevator car: motion engine, trip controller and bus gateway.
 * The car number (1 to 4) is also its bus subtopic. Nothing here depends on the GUI.
 */
public class CabinAssembly {

    private final int number;
    private final MotionEngine engine = new MotionEngine();
    private final TripController trip = new TripController(engine);
    private final BusCabinGateway gateway;

    /**
     * Creates and initializes the CabinAssembly instance.
     *
     * @param number elevator number
     * @param bus software-bus instance
     */
    public CabinAssembly(int number, BusMessageDispatcher bus) {
        this.number = number;
        this.gateway = new BusCabinGateway(engine, trip, bus, number);   //rejects numbers outside 1 to 4
    }

    /** One simulation step for this car: bus first, then the controller, then the physics. */
    public void step() {
        gateway.update();
        trip.controlStep();
        engine.tick();
    }

    /**
     * Returns this cabin assembly’s elevator number.
     *
     * @return the resulting integer value
     */
    public int number() { return number; }

    /**
     * Returns the cabin’s motion engine.
     *
     * @return the resulting MotionEngine value
     */
    public MotionEngine engine() { return engine; }

    /**
     * Returns the cabin’s trip controller.
     *
     * @return the resulting TripController value
     */
    public TripController trip() { return trip; }

    /**
     * Returns the cabin’s bus gateway.
     *
     * @return the resulting BusCabinGateway value
     */
    public BusCabinGateway gateway() { return gateway; }
}
