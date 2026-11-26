package T04ElevatorMotion.src;

import api.Direction;
import api.ShaftSpec;
import assembly.CabinAssembly;
import assembly.CabinRunner;
import bus.BusCodes;
import gui.CabinView;
import gui.ElevatorViewController;
import harness.EveryTwoHarness;
import harness.LeverStopHarness;
import harness.NudgeHarness;
import harness.TopAndBottomHarness;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import manager.FloorSequencer;
import manager.SimulationManager;
import sim.MotionEngine;
import java.util.ArrayList;
import java.util.List;
import bus.BusSource;

public class Main extends Application {

    //Elevator 1 uses the first route, the others use the rest, so every car moves in DEMO mode
    private static final int[][] DEMO_ROUTES = {{2, 10, 4, 1}, {5, 1, 8}, {10, 3}, {7, 2, 9}};
    private enum Mode {DEMO, ROUTE_HARNESS, TOP_BOTTOM_HARNESS, NUDGE_HARNESS, LEVER_TOP_HARNESS, LEVER_BOTTOM_HARNESS, EVERY_TWO_HARNESS}
    private static final Mode MODE = Mode.DEMO; //TODO: Change mode to one of the enums to run it

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/NakatomiTower.fxml"));
        Parent root = loader.load();
        ElevatorViewController controller = loader.getController();

        //Four cars on one bus; each has its own view for the GUI
        //Team03's real bus instead of BusStandIn
        BusSource bus = BusSource.fromArguments(getParameters().getRaw());
        System.out.println("Bus: " + bus.describe());
        List<CabinAssembly> cars = new ArrayList<>();
        List<CabinView> views = new ArrayList<>();
        ShaftSpec spec = new ShaftSpec(MotionEngine.FLOORS, MotionEngine.FLOOR_HEIGHT, MotionEngine.LEVER_MARGIN,
                MotionEngine.CRUISE_SPEED, MotionEngine.ACCELERATION, MotionEngine.DECELERATION);
        for (int n = BusCodes.ELEVATOR_1; n <= BusCodes.ELEVATOR_4; n++) {
            CabinAssembly car = new CabinAssembly(n, bus.open());
            cars.add(car);
            views.add(new CabinView("Elevator " + n, car.engine(), car.trip()::getNudgingFloor, spec));
        }
        CabinAssembly car1 = cars.get(0);   //the scenarios drive elevator 1

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("NakatomiTower");
        primaryStage.show();
        controller.start();                 //the render loop only reads snapshots
        controller.setCabins(views);

        if (MODE == Mode.ROUTE_HARNESS) {
            harness.TestHarness testHarness = new harness.TestHarness(car1.trip(), car1.engine()::tick);
            testHarness.setBeforeStep(car1.gateway()::update);
            testHarness.start();
            primaryStage.setOnCloseRequest(event -> bus.close());
        } else {
            //One simulation manager per car; the slider sets the speed of all of them
            List<SimulationManager> managers = new ArrayList<>();
            for (CabinAssembly car : cars) {
                SimulationManager manager = new SimulationManager(car.trip(), car.engine()::tick);
                manager.setBeforeStep(car.gateway()::update);
                managers.add(manager);
            }
            controller.setSpeedHandler(scale -> managers.forEach(m -> m.setTimeScale(scale)));

            //Add additional harnesses here. Each one runs before elevator 1's gateway, on elevator 1's thread:
            SimulationManager manager1 = managers.get(0);
            Runnable gateway1 = car1.gateway()::update;
            if (MODE == Mode.TOP_BOTTOM_HARNESS) {
                TopAndBottomHarness scenario = new TopAndBottomHarness(bus.open());
                manager1.setBeforeStep(() -> { scenario.update(); gateway1.run(); });
            } else if (MODE == Mode.NUDGE_HARNESS) {
                NudgeHarness scenario = new NudgeHarness(bus.open(), car1.trip());
                manager1.setBeforeStep(() -> { scenario.update(); gateway1.run(); });
            } else if (MODE == Mode.LEVER_TOP_HARNESS || MODE == Mode.LEVER_BOTTOM_HARNESS) {
                Direction runaway = (MODE == Mode.LEVER_TOP_HARNESS) ? Direction.Up : Direction.Down;
                LeverStopHarness scenario = new LeverStopHarness(car1.engine(), bus.open(),
                        BusCodes.ELEVATOR_1, runaway, 40);
                manager1.setBeforeStep(() -> { scenario.update(); gateway1.run(); });
            } else if (MODE == Mode.EVERY_TWO_HARNESS) {
                EveryTwoHarness scenario = new EveryTwoHarness(bus.open());
                manager1.setBeforeStep(() -> { scenario.update(); gateway1.run(); });
            }

            //One thread per elevator. The demo routes only run in DEMO mode and run on the car's own thread,
            //so each car's trip controller still has a single writer.
            List<CabinRunner> runners = new ArrayList<>();
            for (int i = 0; i < cars.size(); i++) {
                FloorSequencer sequencer = (MODE == Mode.DEMO)
                        ? new FloorSequencer(cars.get(i).trip(), DEMO_ROUTES[i], 2.0)
                        : null;
                runners.add(new CabinRunner("elevator-" + (i + 1), managers.get(i), sequencer));
            }
            primaryStage.setOnCloseRequest(event -> {
                runners.forEach(CabinRunner::stop);
                bus.close();
            });
            runners.forEach(CabinRunner::start);   //start last, after every hook is in place
        }
    }

    public static void main(String[] args) {launch(args);}
}