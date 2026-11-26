package T01DoorAssembly;

public class DoorTwin implements DoorApi {
    //For what phase of openess the door is in
    public enum State {
         CLOSED, OPENING, OPEN, CLOSING
         }

    //distance a door panel can travel to fully open/shut. UPDATE USE ONLY IF SPEED CHANGES AND UPDATE DDS
    public static final double DOOR_TRAVEL = 2.0;
    //speed is 1 foot per second per panel. UPDATE USE ONLY IF SPEED CHANGES AND UPDATE DDS
    public static final double DOOR_SPEED_FPS = 0.4;
    //time it takes to open/close a door fully. will be used later for updating GUI
    public static final double DEFAULT_DURATION_SEC = DOOR_TRAVEL/DOOR_SPEED_FPS;
    //Twin threads clock for changing positition
    private static final long TICK_MS = 25;
    //each of the 4 elevator cars and effectively doors ID. Needed for implementation with 4 cars
    //but for the demo we only use car1.
    private final int carId;
    //starts closed
    private State state = State.CLOSED;
    //fully closed position is 0 (1 is open)
    private double position = 0.0;

    //Objects that can be in the way. Nothing sets a sensor directly: the beam sensor
    //detects an object in the doorway and the sill sensor detects one on the floor strip.
    private boolean objectInDoorway = false;
    private boolean objectOnSill = false;

    //Position sensors, like limit switches. They switch on by themselves when the door gets there.
    private boolean closedSensor = true;       //middle: the two panels meet
    private boolean openLeftSensor = false;    //left end: the left panel is fully open
    private boolean openRightSensor = false;   //right end: the right panel is fully open
    //handles the thread that moves the door 20 timees a second.
    private Thread ticker;
    //checks if the twin thread is still going
    private volatile boolean running = true;
    //sets the carID and starting the ticker
    //which basically just starts the update and gets
    //the process going
    public DoorTwin(int carId) {
        this.carId = carId;
        startTicker();
    }
    //basically every 50MS refreshes update
    private void startTicker() {
        //the thread twin runs on
        ticker = new Thread(new Runnable() {
            public void run() {
                //makes sure everything stays in sync
                //by comparing to time now.
                long last = System.currentTimeMillis();
                //loop that moves the clock and calls update
                //whenever it does
                while (running) {
                    try {
                        //sleep the thread so movement doesnt crash something
                        Thread.sleep(TICK_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                    //whats compared to the last time to make sure stuff is synced
                    long now = System.currentTimeMillis();
                    //var describing that^^^
                    double chaneInTime = (now - last) / 1000.0;
                    last = now;
                    update(chaneInTime);
                }
            }
        }, "ThreadNumber" + carId);
        ticker.setDaemon(true);
        //calls the run method above
        ticker.start();
    }
    //update the door states 
    public synchronized void update(double chaneInTime) {
        if (state == State.OPENING) {
            //what percent of the 2 second trip just elapsed?
            position += chaneInTime / DEFAULT_DURATION_SEC;
            if (position >= 1.0) {
                position = 1.0;
                //flip the state
                state = State.OPEN;
            }
        } else if (state == State.CLOSING) {
            position -= chaneInTime / DEFAULT_DURATION_SEC;
            if (position <= 0.0) {
                position = 0.0;
                //flip the state
                state = State.CLOSED;
            }
        }
        updateSensors();
    }

    //A switch lets go as soon as the door starts to move away from it.
    private void updateSensors() {
        closedSensor = position <= 0.0 && state != State.OPENING;
        openLeftSensor = position >= 1.0 && state != State.CLOSING;
        openRightSensor = position >= 1.0 && state != State.CLOSING;
    }

    public synchronized void open() {
        if (state != State.OPEN) {
            state = State.OPENING;
            updateSensors();
        }
    }

    public synchronized void close() {
        if (state != State.CLOSED) {
            state = State.CLOSING;
            updateSensors();
        }
    }

    //what DoorApi reports comes only from the sensors
    public synchronized boolean isFullyOpen() {
        return openLeftSensor && openRightSensor;
    }

    public synchronized boolean isFullyClosed() {
        return closedSensor;
    }

    public synchronized boolean hasObstruction() {
        return isBeamBroken() || isSillLoaded();
    }

    //put an object in the way, or take it away
    public synchronized void placeObjectInDoorway(boolean present) {
        objectInDoorway = present;
    }

    public synchronized void placeObjectOnSill(boolean present) {
        objectOnSill = present;
    }

    //the GUI still calls these two names
    public synchronized void setBeamBroken(boolean broken) {
        placeObjectInDoorway(broken);
    }

    public synchronized void setSillLoaded(boolean loaded) {
        placeObjectOnSill(loaded);
    }

    //the sensors
    public synchronized boolean isBeamBroken() {
        return objectInDoorway;
    }

    public synchronized boolean isSillLoaded() {
        return objectOnSill;
    }

    public synchronized boolean isClosedSensor() {
        return closedSensor;
    }

    public synchronized boolean isOpenLeftSensor() {
        return openLeftSensor;
    }

    public synchronized boolean isOpenRightSensor() {
        return openRightSensor;
    }

    public synchronized double getPosition() {
        return position;
    }

    public synchronized State getState() {
        return state;
    }

    public int getCarId() {
        return carId;
    }

    public void stop() {
        running = false;
        if (ticker != null) {
            ticker.interrupt();
        }
    }
}