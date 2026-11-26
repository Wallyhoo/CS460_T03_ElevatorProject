package T01DoorAssembly;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.IOException;

public class DoorGUI extends Application {
    //I dont really know how we are going to show 4 different doors.
    private final DoorTwin twin = new DoorTwin(1);
    private Circle leftLight;
    private Circle rightLight;
    private Circle midLight;
    private Rectangle sillPlate;

    //Only API methods
    private final DoorApi door = twin;
    private final DoorManager manager = new DoorManager(twin, 1);
    private DoorDash dash;

    //Door leaves travel 200 px each; closed at 600/800, open at 400/1000.
    private static final double LEAF_TRAVEL_PX = 200;
    private static final double LEFT_CLOSED_X = 600;
    private static final double RIGHT_CLOSED_X = 800;
    private Button sillButton;

    private Rectangle doorRight;
    private Rectangle doorLeft;

    private void updateDoorStatus() {
        double p = twin.getPosition();
        doorLeft.setTranslateX(LEFT_CLOSED_X - LEAF_TRAVEL_PX * p);
        doorRight.setTranslateX(RIGHT_CLOSED_X + LEAF_TRAVEL_PX * p);
        boolean open = door.isFullyOpen();
        boolean closed = door.isFullyClosed();
        // Green means open; red means closed; yellow means moving.
        Color statusColor;
        if (open) {
            statusColor = Color.GREEN;
        } else if (closed) {
            statusColor = Color.RED;
        } else {
            statusColor = Color.YELLOW;
        }
        leftLight.setFill(statusColor);
        rightLight.setFill(statusColor);
        midLight.setFill(statusColor);

        sillPlate.setFill(twin.isSillLoaded() ? Color.RED : Color.DIMGRAY);
        sillButton.setText(twin.isSillLoaded() ? "Obstruction Sensor: LOADED" : "Obstruction Sensor: clear");
    }


    private void handleOpen() {
        manager.requestOpen();
    }

    private void handleClose() {
        manager.requestClose();
    }

    private void toggleSill() {
        twin.setSillLoaded(!twin.isSillLoaded());
    }

    private void initialize(Scene scene, StackPane root) {
        Rectangle background = new Rectangle(0, 0, 1200, 800);
        background.setFill(Color.TAN);
        StackPane.setAlignment(background, Pos.TOP_LEFT);
        root.getChildren().add(background);

        Rectangle doorway = new Rectangle(400, 500);
        doorway.setFill(Color.BLACK);
        StackPane.setAlignment(doorway, Pos.TOP_LEFT);
        doorway.setTranslateX(600);
        doorway.setTranslateY(150);
        root.getChildren().add(doorway);

        Rectangle sidePanel = new Rectangle(0, 0, 400, 800);
        sidePanel.setFill(Color.SLATEGRAY);
        StackPane.setAlignment(sidePanel, Pos.TOP_LEFT);
        root.getChildren().add(sidePanel);

        sillButton = new Button("Sill Sensor: clear");
        sillButton.setPrefSize(200, 25);
        StackPane.setAlignment(sillButton, Pos.TOP_LEFT);
        sillButton.setTranslateX(50);
        sillButton.setTranslateY(150);
        root.getChildren().add(sillButton);
        sillButton.setOnAction(e -> toggleSill());

        doorLeft = new Rectangle(200, 500);
        doorLeft.setFill(Color.SILVER);
        //dark outline so you can see the line where the two doors meet
        doorLeft.setStroke(Color.DIMGRAY);
        doorLeft.setStrokeWidth(2);
        StackPane.setAlignment(doorLeft, Pos.TOP_LEFT);
        doorLeft.setTranslateX(600);
        doorLeft.setTranslateY(150);
        root.getChildren().add(doorLeft);

        doorRight = new Rectangle(200, 500);
        doorRight.setFill(Color.SILVER);
        doorRight.setStroke(Color.DIMGRAY);
        doorRight.setStrokeWidth(2);
        StackPane.setAlignment(doorRight, Pos.TOP_LEFT);
        doorRight.setTranslateX(800);
        doorRight.setTranslateY(150);
        root.getChildren().add(doorRight);

        leftLight = new Circle(12);
        leftLight.setStroke(Color.BLACK);
        StackPane.setAlignment(leftLight, Pos.TOP_LEFT);
        leftLight.setTranslateX(566);
        leftLight.setTranslateY(150);
        root.getChildren().add(leftLight);

        midLight = new Circle(12);
        midLight.setStroke(Color.BLACK);
        StackPane.setAlignment(midLight, Pos.TOP_LEFT);
        midLight.setTranslateX(788);
        midLight.setTranslateY(140);
        root.getChildren().add(midLight);

        rightLight = new Circle(12);
        rightLight.setStroke(Color.BLACK);
        StackPane.setAlignment(rightLight, Pos.TOP_LEFT);
        rightLight.setTranslateX(1010);
        rightLight.setTranslateY(150);
        root.getChildren().add(rightLight);

        sillPlate = new Rectangle(400, 12);
        sillPlate.setFill(Color.DIMGRAY);
        StackPane.setAlignment(sillPlate, Pos.TOP_LEFT);
        sillPlate.setTranslateX(600);
        sillPlate.setTranslateY(650);
        root.getChildren().add(sillPlate);
    }

    @Override
    public void start(Stage primaryStage) {
        manager.start();
        dash = new DoorDash(manager, "localhost", 6000);
        try{
            dash.start();
        } catch (IOException ex){
            System.out.println(ex.getMessage());
        }
        StackPane root = new StackPane();
        Scene scene = new Scene(root, 1200, 800);
        initialize(scene, root);
        //redraw from the twin's real position every frame
        new AnimationTimer() {
            public void handle(long now) {
                updateDoorStatus();
            }
        }.start();
        primaryStage.setTitle("Elevator Door");
        primaryStage.setScene(scene);
        primaryStage.setOnHidden(e -> {
            dash.stop();
            manager.stop();
            twin.stop();
        });
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}