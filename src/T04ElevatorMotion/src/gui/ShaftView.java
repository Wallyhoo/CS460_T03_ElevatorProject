package T04ElevatorMotion.src.gui;

import T04ElevatorMotion.src.api.CabinSnapshot;
import T04ElevatorMotion.src.api.ShaftSpec;
import T04ElevatorMotion.src.gui.ViewScale;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/** Draws one shaft with its cabin. Created by FXMLLoader from Shaft.fxml, one per elevator. */
public class ShaftView {

    @FXML private Pane world;
    @FXML private Rectangle shaft;
    @FXML private Group cabinAssembly;
    @FXML private Rectangle gondola;
    @FXML private Rectangle ceilingSensorLeft;
    @FXML private Rectangle ceilingSensorRight;
    @FXML private Rectangle floorSensorLeft;
    @FXML private Rectangle floorSensorRight;

    private static final Color sensorsAligned = Color.LIGHTGREEN;
    private static final Color shaftSensorColor = Color.GRAY;
    private static final Color cabinSensorColor = Color.ORANGE;
    private static final Color sensorsFault = Color.RED;
    private static final Color sensorsNudging = Color.YELLOW;   //between orange and lime green
    private static final Color leverColor = Color.DARKRED;
    private static final long blinkNanos = 400_000_000L;        // 0.4 s per on/off phase

    /**
     * Stores the left and right scene-graph markers for a floor.
     *
     * @param floorLeft value for floorLeft
     * @param floorRight value for floorRight
     * @param ceilingLeft value for ceilingLeft
     * @param ceilingRight value for ceilingRight
     */
    private record FloorMarkers(Rectangle floorLeft, Rectangle floorRight,
                                Rectangle ceilingLeft, Rectangle ceilingRight) {}

    private final List<FloorMarkers> floorMarkers = new ArrayList<>();
    private final javafx.scene.control.Label nameLabel = new javafx.scene.control.Label();
    private Line topLever;
    private Line bottomLever;
    private ViewScale scale;                      //null until show() gives this shaft its specification
    private IntSupplier nudgingFloor = () -> 0;   //floor being nudged, or 0
    private double shownFeet = 0;                 //smoothed position that is actually drawn

    /**
     * Initializes the component and prepares its controls or state.
     */
    @FXML
    private void initialize() {
        //The name sits centered over the shaft, just below its top edge. The width is only known
        //after layout, so the x position is recomputed whenever it changes.
        nameLabel.setStyle("-fx-font-size: 40px; -fx-font-weight: bold;");
        nameLabel.setLayoutY(10);
        double shaftCenterX = shaft.getLayoutX() + shaft.getWidth() / 2;
        nameLabel.widthProperty().addListener((observable, oldWidth, newWidth) ->
                nameLabel.setLayoutX(shaftCenterX - newWidth.doubleValue() / 2));
        world.getChildren().add(nameLabel);
    }

    /** The node to put on screen. */
    public Pane getRoot() {
        return world;
    }

    /**
     * Tells this shaft which cabin it draws. The first call also draws the shaft itself from the
     * cabin's specification. Every call resets the smoothing so the cabin does not glide in.
     */
    public void show(CabinView view) {
        if (scale == null) {
            build(view.spec());
        }
        nameLabel.setText(view.name());
        nudgingFloor = view.nudgingFloor();
        shownFeet = view.cabin().getPosition();
        placeCabin(shownFeet);
    }

    /** Draws one frame from one consistent snapshot. */
    public void render(CabinSnapshot s, long now) {
        shownFeet += (s.position() - shownFeet) * 0.3;   //smooths out some of the jitteriness
        if (Math.abs(s.position() - shownFeet) < 0.001) {
            shownFeet = s.position();
        }
        placeCabin(shownFeet);
        updateSensors(s, now);
    }

    /** Scrolls the shaft so the middle of the cabin sits at the middle of the viewport. */
    public void centerOn(double viewportHeight) {
        double gondolaMiddle = cabinAssembly.getLayoutY() + gondola.getLayoutY() + gondola.getHeight() / 2;
        world.setTranslateY(viewportHeight / 2 - gondolaMiddle);
    }

    /** Outlines the shaft in gold when this car is the selected one in the world view. */
    public void setSelected(boolean selected) {
        shaft.setStroke(selected ? Color.GOLD : Color.BLACK);
        shaft.setStrokeWidth(selected ? 14 : 1);
    }

    /** Puts the shaft back where it was drawn; the world view does not scroll. */
    public void resetCamera() {
        world.setTranslateY(0);
    }

    /**
     * Builds the shaft view from the supplied shaft specification.
     *
     * @param spec shaft dimensions and motion configuration
     */
    private void build(ShaftSpec spec) {
        scale = new ViewScale(spec);
        buildShaft();
        buildFloors();
        buildLevers();
    }

    /**
     * Creates the shaft scene-graph elements.
     */
    private void buildShaft() {
        shaft.setLayoutY(0);
        shaft.setHeight(scale.shaftHeight());
    }

    /**
     * Creates the floor markers and floor labels.
     */
    private void buildFloors() {
        for (int i = 1; i <= scale.floorCount(); i++) {
            double floorYPosition = scale.feetToWorldYValue(scale.floorToFeet(i));
            Line floorLine = new Line(shaft.getLayoutX(), floorYPosition,
                    shaft.getLayoutX() + shaft.getWidth(), floorYPosition);
            floorLine.setStroke(Color.GREEN);
            javafx.scene.control.Label floorNumber = new javafx.scene.control.Label(String.valueOf(i));
            floorNumber.setLayoutX(shaft.getLayoutX() - 35);
            floorNumber.setLayoutY(floorYPosition - 10);
            world.getChildren().add(0, floorLine);
            world.getChildren().add(0, floorNumber);
            floorMarkers.add(new FloorMarkers(
                    buildShaftSensor(floorSensorLeft, floorYPosition),
                    buildShaftSensor(floorSensorRight, floorYPosition),
                    buildShaftSensor(ceilingSensorLeft, floorYPosition),
                    buildShaftSensor(ceilingSensorRight, floorYPosition)));
        }
    }

    /**
     * Creates a shaft sensor aligned to a cabin sensor and floor line.
     *
     * @param cabinSensor cabin sensor graphic used for alignment
     * @param floorLineY floor-line coordinate in the shaft view
     * @return the resulting Rectangle value
     */
    private Rectangle buildShaftSensor(Rectangle cabinSensor, double floorLineY) {
        double x = cabinAssembly.getLayoutX() + cabinSensor.getLayoutX();
        double y = floorLineY + cabinSensor.getLayoutY() - gondolaBottomOffset();
        Rectangle shaftSensor = new Rectangle(x, y, cabinSensor.getWidth(), cabinSensor.getHeight());
        shaftSensor.setFill(shaftSensorColor);
        shaftSensor.setStroke(Color.BLACK);
        world.getChildren().add(0, shaftSensor);
        return shaftSensor;
    }

    /**
     * Creates the top and bottom safety-lever graphics.
     */
    private void buildLevers() {
        topLever = buildLever(scale.floorToFeet(scale.floorCount()) + scale.leverMargin());
        bottomLever = buildLever(-scale.leverMargin());
    }

    /**
     * Creates a safety-lever graphic at the supplied position in feet.
     *
     * @param feet distance or position in feet
     * @return the resulting Line value
     */
    private Line buildLever(double feet) {
        double y = scale.feetToWorldYValue(feet);
        Line lever = new Line(shaft.getLayoutX(), y, shaft.getLayoutX() + shaft.getWidth(), y);
        lever.setStroke(leverColor);
        lever.setStrokeWidth(3);
        world.getChildren().add(0, lever);
        return lever;
    }

    /**
     * Returns the cabin-bottom offset used for shaft positioning, in feet.
     *
     * @return the resulting measurement or calculated value
     */
    private double gondolaBottomOffset() {
        return gondola.getLayoutY() + gondola.getHeight();
    }

    /**
     * Positions the cabin graphic at the supplied height in feet.
     *
     * @param feet distance or position in feet
     */
    private void placeCabin(double feet) {
        double floorLineY = scale.feetToWorldYValue(feet);
        cabinAssembly.setLayoutY(floorLineY - gondolaBottomOffset());
    }

    /**
     * Sets a sensor graphic’s active or inactive color.
     *
     * @param sensor sensor graphic to update
     * @param lit whether the sensor should be shown as active
     * @param offColor color used when the sensor is inactive
     */
    private void setSensorColor(Rectangle sensor, boolean lit, Color offColor) {
        sensor.setFill(lit ? sensorsAligned : offColor);
    }

    /**
     * Updates a sensor graphic’s blinking state.
     *
     * @param sensor sensor graphic to update
     * @param on whether the sensor is active
     * @param offColor color used when the sensor is inactive
     */
    private void blinkSensor(Rectangle sensor, boolean on, Color offColor) {
        sensor.setFill(on ? sensorsFault : offColor);
    }

    /**
     * Determines whether the sensor should be lit at the supplied animation time.
     *
     * @param nowNanos current time in nanoseconds
     * @return whether the requested condition is true
     */
    private boolean blinkOn(long nowNanos) {
        return (nowNanos / blinkNanos) % 2 == 0;
    }

    /**
     * Updates the sensor graphics from the cabin snapshot and animation time.
     *
     * @param s value for s
     * @param now current animation time
     */
    private void updateSensors(CabinSnapshot s, long now) {
        boolean anyAligned = s.alignedFloor() != 0;
        for (int i = 1; i <= scale.floorCount(); i++) {
            boolean aligned = (s.alignedFloor() == i);
            FloorMarkers markers = floorMarkers.get(i - 1);
            setSensorColor(markers.floorLeft(), aligned, shaftSensorColor);
            setSensorColor(markers.floorRight(), aligned, shaftSensorColor);
            setSensorColor(markers.ceilingLeft(), aligned, shaftSensorColor);
            setSensorColor(markers.ceilingRight(), aligned, shaftSensorColor);
        }
        setSensorColor(floorSensorLeft, anyAligned, cabinSensorColor);
        setSensorColor(floorSensorRight, anyAligned, cabinSensorColor);
        setSensorColor(ceilingSensorLeft, anyAligned, cabinSensorColor);
        setSensorColor(ceilingSensorRight, anyAligned, cabinSensorColor);

        //While the car is nudged onto its target floor, show the in-between color on that
        //floor's sensors and the cabin's sensors. They turn lime green once the car is level.
        int nudging = nudgingFloor.getAsInt();
        if (nudging >= 1 && nudging <= scale.floorCount()) {
            FloorMarkers target = floorMarkers.get(nudging - 1);
            target.floorLeft().setFill(sensorsNudging);
            target.floorRight().setFill(sensorsNudging);
            target.ceilingLeft().setFill(sensorsNudging);
            target.ceilingRight().setFill(sensorsNudging);
            floorSensorLeft.setFill(sensorsNudging);
            floorSensorRight.setFill(sensorsNudging);
            ceilingSensorLeft.setFill(sensorsNudging);
            ceilingSensorRight.setFill(sensorsNudging);
        }

        boolean topFault = s.topLimitTripped();
        boolean bottomFault = s.bottomLimitTripped();
        if (topFault || bottomFault) {
            boolean on = blinkOn(now);
            Line trippedLever = topFault ? topLever : bottomLever;
            trippedLever.setStroke(on ? sensorsFault : leverColor);
            FloorMarkers endFloor = floorMarkers.get(topFault ? scale.floorCount() - 1 : 0);
            blinkSensor(endFloor.floorLeft(), on, shaftSensorColor);
            blinkSensor(endFloor.floorRight(), on, shaftSensorColor);
            blinkSensor(endFloor.ceilingLeft(), on, shaftSensorColor);
            blinkSensor(endFloor.ceilingRight(), on, shaftSensorColor);
            blinkSensor(floorSensorLeft, on, cabinSensorColor);
            blinkSensor(floorSensorRight, on, cabinSensorColor);
            blinkSensor(ceilingSensorLeft, on, cabinSensorColor);
            blinkSensor(ceilingSensorRight, on, cabinSensorColor);
        }
    }
}