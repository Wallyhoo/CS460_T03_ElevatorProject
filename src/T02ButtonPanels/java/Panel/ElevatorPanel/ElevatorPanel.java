package T02ButtonPanels.java.Panel.ElevatorPanel;

import T02ButtonPanels.java.Panel.Direction;
import T02ButtonPanels.java.Panel.ElevatorPanel.ElevatorPanelGUI;
import T02ButtonPanels.java.Panel.SoundEffects;
import javafx.scene.Node;

import java.util.Objects;

/**
 * The panel for one elevator car. Holds the state and the behavior of the panel.
 */
public class ElevatorPanel {
    public static final int MIN_FLOOR = 1;
    public static final int MAX_FLOOR = 10;
    private final int elevatorNum;
    private final ElevatorPanelGUI gui;
    private final boolean[] floorButtonPressed = new boolean[MAX_FLOOR];
    private boolean keyInserted;
    private boolean buzzerOn;

    public ElevatorPanel(int elevatorNum) {
        if (elevatorNum < 1) throw new IllegalArgumentException("Elevator number must be positive.");
        this.elevatorNum = elevatorNum;
        gui = new ElevatorPanelGUI(elevatorNum, this::pressFloorButton, this::setKeyInserted, this::setBuzzerOn);
    }

    // get the gui for the elevator panel
    public Node getGui() {
        return gui.getView();
    }

    // when a passenger presses a floor button, called by gui
    private void pressFloorButton(int floorNum) {
        checkFloor(floorNum);
        floorButtonPressed[floorNum - 1] = true;
        gui.setFloorButtonStatus(floorNum, true);
    }

    // register a button as pressed
    public boolean btnPressed(int floorNum) {
        checkFloor(floorNum);
        return floorButtonPressed[floorNum - 1];
    }

    // unregister a button as pressed
    public void resetBtn(int floorNum) {
        checkFloor(floorNum);
        floorButtonPressed[floorNum - 1] = false;
        gui.setFloorButtonStatus(floorNum, false);
    }

    // change the status of the key
    public void setKeyInserted(boolean inserted) {
        keyInserted = inserted;
        gui.setKeyInserted(inserted);
    }

    // play the ding sound for door opening
    public void ding() {
        SoundEffects.playDing();
        gui.flashBuzzerImage();
    }

    // play the buzz for a door passing weight limit
    public void playBuzz() {
        if (buzzerOn) return;

        buzzerOn = true;
        SoundEffects.startBuzzer();
        gui.setBuzzerActive(true);
    }

    // stop the buzzer
    public void stopBuzz() {
        if (!buzzerOn) return;

        buzzerOn = false;
        SoundEffects.stopBuzzer();
        gui.setBuzzerActive(false);
    }

    // check if buzzer is on
    public boolean isBuzzerOn() { return buzzerOn; }

    // toggle the buzzer, called by gui
    private void setBuzzerOn(boolean on) {
        if (on) {
            playBuzz();
        } else {
            stopBuzz();
        }
    }

    // update the display to change with new information
    public void updateFloorDisplay(int newFloor) {
        checkFloor(newFloor);
        gui.updateFloorDisplay(newFloor);
    }

    // udpate the direction on the gui
    public void updateDirDisplay(Direction newDirection) {
        gui.setDirection(Objects.requireNonNull(newDirection, "Direction cannot be null."));
    }

    // get the number of the elevator
    public int getElevatorNum() {return elevatorNum; }

    // check that the floor number is valid
    private static void checkFloor(int floorNum) {
        if (floorNum < MIN_FLOOR || floorNum > MAX_FLOOR) {throw new IllegalArgumentException(
                "Floor number must be between " + MIN_FLOOR + " and " + MAX_FLOOR + ".");
        }
    }
}
