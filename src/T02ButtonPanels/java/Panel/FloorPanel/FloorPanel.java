package T02ButtonPanels.java.Panel.FloorPanel;

import T02ButtonPanels.java.Panel.Direction;
import T02ButtonPanels.java.Panel.FloorPanel.FloorPanelGUI;
import T02ButtonPanels.java.Panel.SoundEffects;
import javafx.scene.Node;

import javax.swing.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The panel for one floor that shows the status of the 4 elevators
 */
public class FloorPanel {
    public static final int ELEVATOR_COUNT = 4;
    public static final int MIN_FLOOR = 1;
    public static final int MAX_FLOOR = 10;

    private final String floorNumber;
    private final Map<Integer, ElevatorStatus> elevatorStatuses = new LinkedHashMap<>();
    private final FloorPanelGUI gui;

    private boolean alarmOn;
    private Direction requestedDirection = Direction.IDLE;
    private Consumer<Direction> directionRequestHandler;
    private BiConsumer<String, Boolean> alarmHandler;
    private Consumer<floorState> stateListener;

    /**
     * Holds the current state of a floor panel.
     * @param floorNumber the floor it is on
     * @param alarmOn whether the alarm is currently on
     * @param requestedDirection what direction is requested for an elevator at that floor
     * @param elevatorStatuses the state of all 4 elevators
     */
    public record floorState(String floorNumber, boolean alarmOn, Direction requestedDirection,
                             Map<Integer, ElevatorStatus> elevatorStatuses) {
        public floorState {
            elevatorStatuses = Collections.unmodifiableMap(new LinkedHashMap<>(elevatorStatuses));
        }
    }

    /**
     * The current status of an elevator.
     * @param currentFloor the floor it is at
     * @param direction the direction it is traveling
     */
    public record ElevatorStatus(int currentFloor, Direction direction) {
        public ElevatorStatus {
            checkFloor(currentFloor);
            Objects.requireNonNull(direction, "Direction cannot be null.");
        }
    }

    public FloorPanel(String floorNumber) {
        if (floorNumber == null || floorNumber.isBlank()) {
            throw new IllegalArgumentException("Floor number cannot be blank.");
        }

        this.floorNumber = floorNumber.trim();
        for (int elevator = 1; elevator <= ELEVATOR_COUNT; elevator++) {
            elevatorStatuses.put(elevator, null);
        }

        gui = new FloorPanelGUI(this::requestDirection, this::toggleAlarm);
        gui.render(getFloorState());
    }

    /** getters for information stored by the panel */
    public floorState getFloorState() { return new floorState(floorNumber, alarmOn, requestedDirection, elevatorStatuses); }
    public String getFloorNumber() { return floorNumber; }
    public boolean alarmPressed() { return alarmOn; }
    public Direction getRequestedDirection() { return requestedDirection; }
    public Direction getElevatorDirection(int elevatorNumber) {
        checkElevatorNumber(elevatorNumber);
        ElevatorStatus status = elevatorStatuses.get(elevatorNumber);
        return status == null ? Direction.IDLE : status.direction();
    }
    public Node getGui() { return gui.getView(); }

    /** change the alarm status by setting it off, toggling it, or setting it on */
    public void resetAlarm() { setAlarmOn(false); }
    public void toggleAlarm() { setAlarmOn(!alarmOn); }
    public void setAlarmOn(boolean on) {
        if (alarmOn == on) return;
        alarmOn = on;

        if (alarmHandler != null) alarmHandler.accept(floorNumber, alarmOn);
        notifyStateListener();
    }

    // play the arrival sound
    public void ding() { SoundEffects.playDing(); }

    /** update the direction of an elevator */
    public void updateDirection(Direction direction) {
        requestedDirection = Objects.requireNonNull(direction, "Direction cannot be null.");
        notifyStateListener();
    }

    /** notify the bus that a passenger requested an elevator in a direction */
    public void requestDirection(Direction direction) {
        updateDirection(direction);
        if (directionRequestHandler != null) directionRequestHandler.accept(direction);
    }

    /** Stores the most recent status reported by one elevator. */
    public void updateElevatorStatus(int elevatorNumber, int currentFloor, Direction direction) {
        checkElevatorNumber(elevatorNumber);
        elevatorStatuses.put(elevatorNumber, new ElevatorStatus(currentFloor, direction));
        notifyStateListener();
    }

    /** setters for floor panel information */
    public void setDirectionRequestHandler(Consumer<Direction> handler) { directionRequestHandler = handler; }
    public void setStateListener(Consumer<floorState> listener) {
        stateListener = listener;
        notifyStateListener();
    }
    public void setAlarmHandler(BiConsumer<String, Boolean> handler) { alarmHandler = handler; }

    private void notifyStateListener() {
        floorState state = getFloorState();
        gui.render(state);

        if (stateListener != null) stateListener.accept(state);
    }

    /** ensure that a floor number is valid */
    private static void checkFloor(int floor) {
        if (floor < MIN_FLOOR || floor > MAX_FLOOR) {
            throw new IllegalArgumentException("Floor number must be between " + MIN_FLOOR + " and " + MAX_FLOOR + ".");
        }
    }

    /** ensure that an elevator number is valid */
    private static void checkElevatorNumber(int elevatorNumber) {
        if (elevatorNumber < 1 || elevatorNumber > ELEVATOR_COUNT) {
            throw new IllegalArgumentException(
                    "Elevator number must be between 1 and " + ELEVATOR_COUNT + "."
            );
        }
    }
}