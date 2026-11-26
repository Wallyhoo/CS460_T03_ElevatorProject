package T02ButtonPanels.java.Panel.ElevatorPanel;

import T02ButtonPanels.java.Panel.Direction;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * JavaFX implementation for an elevator panel
 * Only ElevatorPanel should use this class
 */
final class ElevatorPanelGUI {
    private static final String UP_ARROW_OFF = "/arrow-up-off.png";
    private static final String UP_ARROW_ON = "/arrow-up-on.png";
    private static final String DOWN_ARROW_OFF = "/arrow-down-off.png";
    private static final String DOWN_ARROW_ON = "/arrow-down-on.png";
    private static final String FLOOR_BUTTON_OFF = "/buttons-grey/%d.png";
    private static final String FLOOR_BUTTON_ON = "/buttons-green/%d-green.png";
    private static final String KEY_OFF = "/key-off.png";
    private static final String KEY_ON = "/key-on.png";
    private static final String BUZZER_OFF = "/buzzer-off.png";
    private static final String BUZZER_ON = "/buzzer-on.png";

    private static final String FLOOR_BUTTON_OFF_STYLE = """
            -fx-background-color: #f4f4f4;
            -fx-border-color: #232323;
            -fx-border-width: 1;
            -fx-font-size: 17px;
            -fx-font-weight: bold;
            -fx-text-fill: #111111;
            """;
    private static final String FLOOR_BUTTON_ON_STYLE = """
            -fx-background-color: #a8f52a;
            -fx-border-color: #386b00;
            -fx-border-width: 2;
            -fx-font-size: 17px;
            -fx-font-weight: bold;
            -fx-text-fill: #111111;
            -fx-effect: dropshadow(gaussian, #9dff00, 16, 0.65, 0, 0);
            """;
    private static final String ARROW_OFF_STYLE = """
            -fx-background-color: #f4f4f4;
            -fx-border-color: #232323;
            -fx-border-width: 1;
            -fx-font-size: 24px;
            -fx-font-weight: bold;
            -fx-text-fill: #111111;
            """;
    private static final String ARROW_ON_STYLE = """
            -fx-background-color: #a8f52a;
            -fx-border-color: #386b00;
            -fx-border-width: 2;
            -fx-font-size: 24px;
            -fx-font-weight: bold;
            -fx-text-fill: #111111;
            -fx-effect: dropshadow(gaussian, #9dff00, 18, 0.8, 0, 0);
            """;

    private final Label elevatorNumberLabel = new Label("ELEVATOR");
    private final Label floorLabel = new Label("1");
    private final Label directionLabel = new Label(Direction.IDLE.getDisplayText());
    private final Button upIndicator;
    private final Button downIndicator;
    private final ImageView keyImage = new ImageView();
    private final StackPane keyContainer = new StackPane(keyImage);
    private final ImageView buzzerImage = new ImageView();
    private final Map<Integer, Button> floorButtons = new LinkedHashMap<>();
    private final VBox view = new VBox();

    private final IntConsumer buttonHandler;
    private final Consumer<Boolean> keyHandler;
    private final Consumer<Boolean> buzzerHandler;
    private final PauseTransition dingImageTimer = new PauseTransition(Duration.millis(500));
    private Direction direction = Direction.IDLE;
    private boolean keyInserted;
    private boolean buzzerActive;

    ElevatorPanelGUI(int elevatorNumber, IntConsumer buttonHandler, Consumer<Boolean> keyHandler,
                     Consumer<Boolean> buzzerHandler) {
        this.buttonHandler = Objects.requireNonNull(buttonHandler, "Button handler cannot be null.");
        this.keyHandler = Objects.requireNonNull(keyHandler, "Key handler cannot be null.");
        this.buzzerHandler = Objects.requireNonNull(buzzerHandler, "Buzzer handler cannot be null.");
        upIndicator = createDirectionIndicator(UP_ARROW_OFF);
        downIndicator = createDirectionIndicator(DOWN_ARROW_OFF);

        buildView();
        setElevatorNumber(elevatorNumber);
        setDirection(Direction.IDLE);
        setKeyInserted(false);
        setBuzzerActive(false);

        dingImageTimer.setOnFinished(event -> {if (!buzzerActive) setControlImage(buzzerImage, BUZZER_OFF);});
    }

    // get the gui view
    VBox getView() { return view; }

    // update the display showing the floor
    void updateFloorDisplay(int floor) {
        checkFloor(floor);
        floorLabel.setText(String.valueOf(floor));
    }

    // set the direction the elevator is going
    void setDirection(Direction newDirection) {
        direction = Objects.requireNonNull(newDirection, "Direction cannot be null.");
        directionLabel.setText(direction.getDisplayText());

        boolean movingUp = direction == Direction.UP;
        boolean movingDown = direction == Direction.DOWN;

        upIndicator.setStyle(movingUp ? ARROW_ON_STYLE : ARROW_OFF_STYLE);
        downIndicator.setStyle(movingDown ? ARROW_ON_STYLE : ARROW_OFF_STYLE);

        setArrowGraphic(upIndicator, movingUp ? UP_ARROW_ON : UP_ARROW_OFF);
        setArrowGraphic(downIndicator, movingDown ? DOWN_ARROW_ON : DOWN_ARROW_OFF);
    }

    // turn a button for a floor request on or off
    void setFloorButtonStatus(int floor, boolean pressed) {
        checkFloor(floor);
        Button button = floorButtons.get(floor);
        button.setStyle(pressed ? FLOOR_BUTTON_ON_STYLE : FLOOR_BUTTON_OFF_STYLE);
        setFloorButtonGraphic(button, floor, pressed);
    }

    // change the key image to be inserted
    void setKeyInserted(boolean inserted) {
        keyInserted = inserted;
        setControlImage(keyImage, inserted ? KEY_ON : KEY_OFF);
        keyContainer.setStyle(inserted ? """
                  -fx-border-color: #ff2020;
                  -fx-border-width: 2;
                  -fx-border-radius: 4;
                  -fx-effect: dropshadow(gaussian, #ff0000, 16, 0.85, 0, 0);
                  """ : "");
    }

    // switch the status of the buzzer
    void setBuzzerActive(boolean active) {
        buzzerActive = active;
        dingImageTimer.stop();
        setControlImage(buzzerImage, active ? BUZZER_ON : BUZZER_OFF);
    }

    // flash the image of the buzzer for a ding
    void flashBuzzerImage() {
        if (buzzerActive) return;
        setControlImage(buzzerImage, BUZZER_ON);
        dingImageTimer.playFromStart();
    }

    /**
     * Assemble all the parts of the elevator panel gui
     */
    private void buildView() {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setAlignment(Pos.CENTER);

        elevatorNumberLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        GridPane.setColumnSpan(elevatorNumberLabel, 2);
        grid.add(elevatorNumberLabel, 0, 0);

        floorLabel.setAlignment(Pos.CENTER);
        floorLabel.setMinSize(122, 92);
        floorLabel.setMaxSize(122, 92);
        floorLabel.setStyle("""
                -fx-background-color: #111111;
                -fx-border-color: #b7b7b7;
                -fx-border-width: 2;
                -fx-text-fill: #f7f7f7;
                -fx-font-size: 42px;
                -fx-font-family: "Monospaced";
                -fx-font-weight: bold;
                """);
        GridPane.setColumnSpan(floorLabel, 2);
        grid.add(floorLabel, 0, 2);

        directionLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4b4b4b;");
        GridPane.setColumnSpan(directionLabel, 2);
        grid.add(directionLabel, 0, 3);
        grid.add(upIndicator, 0, 4);
        grid.add(downIndicator, 1, 4);

        Label buttonTitle = new Label("SELECT FLOOR");
        buttonTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        GridPane.setColumnSpan(buttonTitle, 2);
        grid.add(buttonTitle, 0, 5);

        for (int floor = ElevatorPanel.MIN_FLOOR; floor <= ElevatorPanel.MAX_FLOOR; floor++) {
            Button button = createFloorButton(floor);
            floorButtons.put(floor, button);

            int index = floor - ElevatorPanel.MIN_FLOOR;
            grid.add(button, index % 2, 6 + index / 2);
        }

        keyContainer.setPrefSize(58, 48);
        keyContainer.setMinSize(58, 48);
        keyContainer.setMaxSize(58, 48);
        configureImageControl(keyImage, KEY_OFF);
        configureImageControl(buzzerImage, BUZZER_OFF);
        keyContainer.setOnMouseClicked(event -> keyHandler.accept(!keyInserted));
        buzzerImage.setOnMouseClicked(event -> buzzerHandler.accept(!buzzerActive));
        grid.add(keyContainer, 0, 11);
        grid.add(buzzerImage, 1, 11);

        GridPane.setHalignment(keyImage, javafx.geometry.HPos.CENTER);
        GridPane.setHalignment(buzzerImage, javafx.geometry.HPos.CENTER);
        GridPane.setValignment(keyImage, javafx.geometry.VPos.CENTER);
        GridPane.setValignment(buzzerImage, javafx.geometry.VPos.CENTER);

        view.setPadding(new Insets(18));
        view.setAlignment(Pos.CENTER);
        view.setStyle("-fx-background-color: #dedede; " + "-fx-border-color: #676767; " + "-fx-border-width: 1;");
        view.getChildren().add(grid);
    }

    /** create a button to choose a floor */
    private Button createFloorButton(int floor) {
        Button button = new Button();
        button.setMinSize(58, 48);
        button.setMaxSize(58, 48);
        button.setStyle(FLOOR_BUTTON_OFF_STYLE);
        setFloorButtonGraphic(button, floor, false);
        button.setOnAction(event -> buttonHandler.accept(floor));
        return button;
    }

    /** make the direction arrows on the display */
    private Button createDirectionIndicator(String imagePath) {
        Button button = new Button();
        button.setMinSize(58, 40);
        button.setMaxSize(58, 40);
        button.setFocusTraversable(false);
        button.setMouseTransparent(true);
        setArrowGraphic(button, imagePath);
        return button;
    }

    /** set the number of the current elevator */
    private void setElevatorNumber(int elevatorNumber) {
        if (elevatorNumber < 1) throw new IllegalArgumentException("Elevator number must be positive.");
        elevatorNumberLabel.setText("ELEVATOR " + elevatorNumber);
    }

    /** change an image for a control button */
    private void configureImageControl(ImageView imageView, String imagePath) {
        ImageView image = createImage(imagePath, 58, 48);

        imageView.setImage(image == null ? null : image.getImage());
        imageView.setFitWidth(58);
        imageView.setFitHeight(48);
        imageView.setPreserveRatio(true);
    }

    /** set the image for a control of the elevator */
    private void setControlImage(ImageView imageView, String imagePath) {
        ImageView image = createImage(imagePath, 58, 48);
        imageView.setImage(image == null ? null : image.getImage());
    }

    /** set the image for an arrow */
    private void setArrowGraphic(Button button, String imagePath) {
        ImageView arrowImage = createImage(imagePath, 28, 28);
        button.setText("");
        button.setGraphic(arrowImage);
    }

    /** set the image for the floor button */
    private void setFloorButtonGraphic(Button button, int floor, boolean lit) {
        String imagePath = lit ? FLOOR_BUTTON_ON.formatted(floor) : FLOOR_BUTTON_OFF.formatted(floor);
        button.setText("");
        button.setGraphic(createImage(imagePath, 46, 36));
    }

    /** create an image from a path */
    private ImageView createImage(String imagePath, double width, double height) {
        if (imagePath == null || imagePath.isBlank()) return null;

        try (InputStream stream = getClass().getResourceAsStream(imagePath)) {
            if (stream == null) return null;
            ImageView imageView = new ImageView(new Image(stream));
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setPreserveRatio(true);
            return imageView;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /** check that the floor is valid */
    private static void checkFloor(int floor) {
        if (floor < ElevatorPanel.MIN_FLOOR || floor > ElevatorPanel.MAX_FLOOR) {
            throw new IllegalArgumentException("Floor number must be between " + ElevatorPanel.MIN_FLOOR + " and "
                            + ElevatorPanel.MAX_FLOOR + ".");
        }
    }
}