package T04ElevatorMotion.src.bus;

import Utilities.Message;
import Utilities.Protocol;
import Utilities.Topics;

/**
 * Compatibility facade for shared software-bus constants and elevator-specific fault codes.
 */
public final class BusCodes {

    /**
     * This class now uses the approved Software Bus Message Catalog in Team03's repo (Utilities/Topics.java).
     * Empty constructor so no one can instantiate this like ViewScale
     */
    private BusCodes() {}

    //Commands
    public static final int REGISTER = Protocol.REGISTER;
    public static final int SUBSCRIBE = Protocol.SUBSCRIBE;
    public static final int UNSUBSCRIBE = Protocol.UNSUBSCRIBE;
    public static final int GET = Protocol.GET;
    public static final int NO_MESSAGE = Protocol.NO_MESSAGE;

    //Topics. Payload layout in the comment, payload byte 1 is the first byte after the subtopic.
    public static final int FIRE = Topics.FIRE;     //subtopic 0. P1: 1 = fire alert / test fire, 0 = reset
    public static final int ELEVATOR_COMMAND = Topics.ELEVATOR_COMMAND;     //P1: 0 = out of service, 1 = in service (was MODE)
    public static final int CABIN_STATUS = Topics.CABIN_STATUS;             //P1: stopped (1 = stopped), P2: floor 1-10, P3: direction (was CAR_POSITION
    // 4 is retired (was CAR_DIRECTION, now part of CABIN_STATUS)
    public static final int UP_CALL = Topics.UP_CALL;                       //subtopic 0. P1: floor 1-10 (was HALL_CALL)
    public static final int CABIN_CALL = Topics.CABIN_CALL;                 //P1: floor 1-10 (was CAR_CALL)
    public static final int DOOR_STATUS = Topics.DOOR_STATUS;               //P1: fully open, P2: fully closed, P3: obstructed, P4: overweight (was DOOR_STATE)
    public static final int DOWN_CALL = Topics.DOWN_CALL;                   //subtopic 0. P1: floor 1-10
    public static final int ALIGN = Topics.ALIGN;                           //P1: 0 none, 1 bottom, 2 top, 3 both
    public static final int MOTOR_COMMAND = Topics.MOTOR_COMMAND;           //P1: 0 stop / 1 start, P2: 1 up / 2 down
    public static final int DOOR_COMMAND = Topics.DOOR_COMMAND;             //P1: 0 close / 1 open
    public static final int BUZZER = Topics.BUZZER;                         //P1: 0 off / 1 on
    public static final int ARRIVAL_CHIME = Topics.ARRIVAL_CHIME;           //P1: floor 1-10, P2: leaving direction 0 none / 1 up / 2 down
    public static final int KEY = Topics.KEY;                               //P1: 0 off / 1 on
    public static final int GOTO = Topics.GOTO;                             //P1: floor 1-10, keep the doors closed on arrival
    public static final int SERVICE = Topics.SERVICE;                       //P1: floor 1-10, open the doors on arrival
    public static final int CLEAR_REQUEST = Topics.CLEAR_REQUEST;           //subtopic 0. P1: hallway floor 1-10, P2: 1 up / 2 down / 3 both
    public static final int CLEAR_BUTTONS = Topics.CLEAR_BUTTONS;           //P1: elevator panel button 1-10

    //Subtopics
    public static final int ALL = Topics.ALL;
    public static final int ELEVATOR_1 = Topics.ELEVATOR_1;
    public static final int ELEVATOR_2 = Topics.ELEVATOR_2;
    public static final int ELEVATOR_3 = Topics.ELEVATOR_3;
    public static final int ELEVATOR_4 = Topics.ELEVATOR_4;

    //Payload values
    public static final int DIR_IDLE = Message.DIR_IDLE;    //"idle" in CABIN_STATUS, "none" in ARRIVAL_CHIME
    public static final int DIR_UP = Message.DIR_UP;
    public static final int DIR_DOWN = Message.DIR_DOWN;
    public static final int ALIGN_NONE = Message.ALIGN_NONE;
    public static final int ALIGN_BOTTOM = Message.ALIGN_BOTTOM;
    public static final int ALIGN_TOP = Message.ALIGN_TOP;
    public static final int ALIGN_BOTH = Message.ALIGN_BOTH;

    //Limits
    public static final int FIRST_COMMAND = Protocol.FIRST_COMMAND;
    public static final int MAX_TOPIC = Protocol.FIRST_COMMAND - 1;

    //Our own message, NOT in the catalog. Team03's catalog uses topics 1 to 18, so ours starts after them.
    //TODO: tell the other teams about topic 20 when we integrate.
    public static final int CAR_FAULT = 20;         //subtopic = elevator, P1 = fault code
    public static final int FAULT_TOP_LIMIT = 1;    //cabin hit the lever above floor 10
    public static final int FAULT_BOTTOM_LIMIT = 2; //cabin hit the lever below floor 1
}