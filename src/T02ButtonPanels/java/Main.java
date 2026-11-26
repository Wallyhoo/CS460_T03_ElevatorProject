package T02ButtonPanels.java;

import Panel.Direction;
import Panel.ElevatorPanel.ElevatorPanel;
import Panel.FloorPanel.FloorPanel;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Runs the one floor demonstration of the passenger facing devices
 */
public class Main extends Application {
    @Override
    public void start(Stage elevatorStage) {
        showElevatorWindow(elevatorStage);
        showFloorPanelWindow();
    }

    /** Create a window that only contains the elevator window */
    private void showElevatorWindow(Stage stage) {
        ElevatorPanel elevatorPanel = new ElevatorPanel(1);

        elevatorPanel.updateFloorDisplay(1);
        elevatorPanel.updateDirDisplay(Direction.IDLE);

        VBox root = new VBox(8, elevatorPanel.getGui());
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #f8f8f8;");

        stage.setTitle("Elevator Panel");
        stage.setScene(new Scene(root, 260, 640));
        stage.show();
    }

    /** Creates a separate window that only contains the floor panel */
    private void showFloorPanelWindow() {
        FloorPanel floorPanel = new FloorPanel("1");

        VBox root = new VBox(8, floorPanel.getGui());
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #f8f8f8;");

        Stage floorStage = new Stage();
        floorStage.setTitle("Floor Panel");
        floorStage.setScene(new Scene(root, 350, 425));
        floorStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}