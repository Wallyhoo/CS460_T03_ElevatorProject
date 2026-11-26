package T04ElevatorMotion.src;

import T04ElevatorMotion.src.api.ShaftSpec;
import T04ElevatorMotion.src.assembly.CabinAssembly;
import T04ElevatorMotion.src.assembly.CabinRunner;
import T04ElevatorMotion.src.bus.BusCodes;
import T04ElevatorMotion.src.gui.CabinView;
import T04ElevatorMotion.src.gui.ElevatorViewController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import T04ElevatorMotion.src.manager.FloorSequencer;
import T04ElevatorMotion.src.manager.SimulationManager;
import T04ElevatorMotion.src.sim.MotionEngine;
import java.util.ArrayList;
import java.util.List;
import T04ElevatorMotion.src.bus.BusSource;

public class Main extends Application {

    //Elevator 1 uses the first route, the others use the rest, so every car moves in DEMO mode
    private static final int[][] DEMO_ROUTES = {{2, 10, 4, 1}, {5, 1, 8}, {10, 3}, {7, 2, 9}};

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

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("NakatomiTower");
        primaryStage.show();
        controller.start();                 //the render loop only reads snapshots
        controller.setCabins(views);

        //One simulation manager per car; the slider sets the speed of all of them
        List<SimulationManager> managers = new ArrayList<>();
        for (CabinAssembly car : cars) {
            SimulationManager manager = new SimulationManager(car.trip(), car.engine()::tick);
            manager.setBeforeStep(car.gateway()::update);
            managers.add(manager);
        }
        controller.setSpeedHandler(scale -> managers.forEach(m -> m.setTimeScale(scale)));

        //One thread per elevator. Until the integration controller exists, each car follows its demo route.
        List<CabinRunner> runners = new ArrayList<>();
        for (int i = 0; i < cars.size(); i++) {
            FloorSequencer sequencer = new FloorSequencer(cars.get(i).trip(), DEMO_ROUTES[i], 2.0);
            runners.add(new CabinRunner("elevator-" + (i + 1), managers.get(i), sequencer));
        }
        primaryStage.setOnCloseRequest(event -> {
            runners.forEach(CabinRunner::stop);
            bus.close();
        });
        runners.forEach(CabinRunner::start);   //start last, after every hook is in place
    }

    public static void main(String[] args) {launch(args);}
}