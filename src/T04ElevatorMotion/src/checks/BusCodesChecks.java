package T04ElevatorMotion.src.checks;

import bus.BusCodes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Checks that our topic numbers match Team03's catalog and that no two topics share a number.
 */
public class BusCodesChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) throws Exception {

        //Values from Team03's Utilities/Topics.java
        check(BusCodes.FIRE == 1 && BusCodes.ELEVATOR_COMMAND == 2 && BusCodes.CABIN_STATUS == 3, "topics 1 to 3 match the catalog");
        check(BusCodes.UP_CALL == 5 && BusCodes.CABIN_CALL == 6 && BusCodes.DOOR_STATUS == 7, "topics 5 to 7 match the catalog");
        check(BusCodes.DOWN_CALL == 8 && BusCodes.ALIGN == 9 && BusCodes.MOTOR_COMMAND == 10, "topics 8 to 10 match the catalog");
        check(BusCodes.DOOR_COMMAND == 11 && BusCodes.BUZZER == 12 && BusCodes.ARRIVAL_CHIME == 13, "topics 11 to 13 match the catalog");
        check(BusCodes.KEY == 14 && BusCodes.GOTO == 15 && BusCodes.SERVICE == 16, "topics 14 to 16 match the catalog");
        check(BusCodes.CLEAR_REQUEST == 17 && BusCodes.CLEAR_BUTTONS == 18, "topics 17 and 18 match the catalog");

        //Our own fault topic must not reuse a catalog topic
        check(BusCodes.CAR_FAULT > BusCodes.CLEAR_BUTTONS, "CAR_FAULT is after the last catalog topic");

        //Commands
        check(BusCodes.REGISTER == 0xF0 && BusCodes.SUBSCRIBE == 0xF1 && BusCodes.UNSUBSCRIBE == 0xF2
                && BusCodes.GET == 0xF3 && BusCodes.NO_MESSAGE == 0xF4, "commands match Protocol");

        //No two topics share a number (this is what the old CAR_FAULT = 8 would have failed)
        String[] topics = {"FIRE", "ELEVATOR_COMMAND", "CABIN_STATUS", "UP_CALL", "CABIN_CALL", "DOOR_STATUS", "DOWN_CALL",
                "ALIGN", "MOTOR_COMMAND", "DOOR_COMMAND", "BUZZER", "ARRIVAL_CHIME", "KEY", "GOTO", "SERVICE",
                "CLEAR_REQUEST", "CLEAR_BUTTONS", "CAR_FAULT"};
        Map<Integer, String> used = new HashMap<>();
        for (String name : topics) {
            Field field = BusCodes.class.getField(name);
            check(Modifier.isStatic(field.getModifiers()), name + " is a constant");
            int value = field.getInt(null);
            check(value >= 1 && value <= BusCodes.MAX_TOPIC, name + " is a legal topic number");
            String clash = used.put(value, name);
            check(clash == null, name + " does not share its number with " + clash);
        }

        //Direction and alignment codes
        check(BusCodes.DIR_IDLE == 0 && BusCodes.DIR_UP == 1 && BusCodes.DIR_DOWN == 2, "direction codes match the catalog");
        check(BusCodes.ALIGN_NONE == 0 && BusCodes.ALIGN_BOTTOM == 1 && BusCodes.ALIGN_TOP == 2
                && BusCodes.ALIGN_BOTH == 3, "alignment codes match the catalog");

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Asserts that the supplied condition is true and reports the message if it is false.
     *
     * @param condition condition that must be true for the check to pass
     * @param message description printed when the check fails
     */
    private static void check(boolean condition, String message) {
        checksRun++;
        if (condition == false) {
            checksFailed++;
            System.out.println("FAIL: " + message);
        }
    }
}