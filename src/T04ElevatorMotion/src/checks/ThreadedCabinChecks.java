package T04ElevatorMotion.src.checks;

import Utilities.Message;
import api.CabinSnapshot;
import assembly.CabinAssembly;
import assembly.CabinRunner;
import bus.BusCodes;
import bus.BusMessageDispatcher;
import bus.BusStandIn;
import manager.SimulationManager;
import sim.FloorSensors;
import sim.MotionEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stress check: four cars on four threads, one thread flooding the bus with calls and one
 * thread reading snapshots the way the GUI does. Real time: a few seconds.
 */
public class ThreadedCabinChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;
    private static volatile String snapshotProblem = null;

    public static void main(String[] args) throws Exception {
        busHammerChecks();
        carChecks();
        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Four producer threads publish numbered messages to four consumer threads at full speed.
     * With a correct bus every consumer sees each producer's numbers strictly increasing: a
     * duplicate or a message out of order means two threads corrupted a queue.
     */
    private static void busHammerChecks() throws Exception {
        BusStandIn bus = new BusStandIn();
        int consumers = 4;
        int producers = 4;
        int perProducer = 200_000;
        List<BusMessageDispatcher> consumerLinks = new ArrayList<>();
        for (int k = 1; k <= consumers; k++) {
            BusMessageDispatcher link = bus.connect();
            link.subscribe(BusCodes.CABIN_STATUS, k);
            consumerLinks.add(link);
        }
        List<BusMessageDispatcher> producerLinks = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            producerLinks.add(bus.connect());
        }

        java.util.concurrent.atomic.AtomicReference<String> problem = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean producing = new java.util.concurrent.atomic.AtomicBoolean(true);
        List<Thread> threads = new ArrayList<>();

        for (int k = 1; k <= consumers; k++) {
            final int subtopic = k;
            final BusMessageDispatcher link = consumerLinks.get(k - 1);
            Thread t = new Thread(() -> {
                long[] last = new long[producers];
                java.util.Arrays.fill(last, -1);
                try {
                    boolean more = true;
                    while (more) {
                        more = producing.get();
                        java.util.Optional<Message> m = link.get(BusCodes.CABIN_STATUS, subtopic);
                        while (m.isPresent()) {
                            long v = m.get().value();
                            int p = (int) (v / 1_000_000);
                            long seq = v % 1_000_000;
                            if (seq <= last[p]) {
                                problem.compareAndSet(null, "consumer " + subtopic + " saw producer " + p
                                        + " go from " + last[p] + " to " + seq);
                            }
                            last[p] = seq;
                            m = link.get(BusCodes.CABIN_STATUS, subtopic);
                        }
                    }
                } catch (Throwable e) {
                    problem.compareAndSet(null, "consumer threw " + e);
                }
            }, "consumer-" + k);
            threads.add(t);
        }
        List<Thread> producerThreads = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            final int id = p;
            final BusMessageDispatcher link = producerLinks.get(p);
            Thread t = new Thread(() -> {
                try {
                    for (int i = 0; i < perProducer; i++) {
                        for (int k = 1; k <= consumers; k++) {
                            link.publish(Message.of(BusCodes.CABIN_STATUS, k, id * 1_000_000L + i));
                        }
                    }
                } catch (Throwable e) {
                    problem.compareAndSet(null, "producer threw " + e);
                }
            }, "producer-" + p);
            producerThreads.add(t);
        }
        threads.forEach(Thread::start);
        producerThreads.forEach(Thread::start);
        for (Thread t : producerThreads) {
            t.join();
        }
        producing.set(false);
        for (Thread t : threads) {
            t.join();
        }
        check(problem.get() == null, "the bus survives four producers and four consumers"
                + (problem.get() == null ? "" : ": " + problem.get()));
    }

    /**
     * Checks cabin behavior while its simulation runner is active.
     *
     * @throws Exception if the operation cannot be completed
     */
    private static void carChecks() throws Exception {
        BusStandIn bus = new BusStandIn();
        List<CabinAssembly> cars = new ArrayList<>();
        List<SimulationManager> managers = new ArrayList<>();
        List<CabinRunner> runners = new ArrayList<>();
        for (int n = BusCodes.ELEVATOR_1; n <= BusCodes.ELEVATOR_4; n++) {
            CabinAssembly car = new CabinAssembly(n, bus.connect());
            SimulationManager manager = new SimulationManager(car.trip(), car.engine()::tick);
            manager.setBeforeStep(car.gateway()::update);
            manager.setTimeScale(8.0);   //simulated time runs 8 times faster than real time
            cars.add(car);
            managers.add(manager);
            runners.add(new CabinRunner("elevator-" + n, manager, null));
        }

        BusMessageDispatcher center = bus.connect();
        Thread watcher = startWatcher(cars);
        runners.forEach(CabinRunner::start);

        //Flood the bus with calls from this thread while the cars run on theirs
        Random random = new Random(42);
        long end = System.currentTimeMillis() + 3000;
        int sent = 0;
        while (System.currentTimeMillis() < end) {
            int car = 1 + random.nextInt(4);
            int floor = 1 + random.nextInt(10);
            center.publish(Message.of(BusCodes.CABIN_CALL, car, floor));
            sent++;
            Thread.sleep(5);
        }

        //Let the cars finish what is queued: wait until all are idle several polls in a row
        int idlePolls = 0;
        long giveUp = System.currentTimeMillis() + 60_000;
        while (idlePolls < 10 && System.currentTimeMillis() < giveUp) {
            Thread.sleep(100);
            idlePolls = allIdle(cars) ? idlePolls + 1 : 0;
        }
        watcher.interrupt();
        watcher.join();
        runners.forEach(CabinRunner::stop);

        check(sent > 100, "sent a good number of calls (" + sent + ")");
        for (int i = 0; i < 4; i++) {
            check(runners.get(i).getFailure() == null, "car " + (i + 1) + " thread has no exception");
            MotionEngine engine = cars.get(i).engine();
            check(!engine.isTopLimitTripped() && !engine.isBottomLimitTripped(), "car " + (i + 1) + " tripped no lever");
            check(!engine.isMoving() && FloorSensors.currentFloor(engine.getPosition()) != 0,
                    "car " + (i + 1) + " ended stopped at a floor");
        }
        check(snapshotProblem == null, "every snapshot was consistent" +
                (snapshotProblem == null ? "" : ": " + snapshotProblem));
    }

    /**
     * Reports whether every cabin is currently idle.
     *
     * @param cars cabin assemblies being checked
     * @return whether the requested condition is true
     */
    private static boolean allIdle(List<CabinAssembly> cars) {
        for (CabinAssembly car : cars) {
            if (car.engine().isMoving() || car.trip().isTripActive()) {
                return false;
            }
        }
        return true;
    }

    /** Acts like the GUI: reads snapshots and nudging floors in a tight loop and checks they make sense.
     *
     * @param cars cabin assemblies being checked
     * @return the resulting Thread value
     */
    private static Thread startWatcher(List<CabinAssembly> cars) {
        Thread watcher = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                for (CabinAssembly car : cars) {
                    CabinSnapshot s = car.engine().snapshot();
                    car.trip().getNudgingFloor();
                    if (s.position() < MotionEngine.MIN_POSITION || s.position() > MotionEngine.MAX_POSITION) {
                        snapshotProblem = "position out of the shaft: " + s.position();
                    } else if (s.velocity() < 0) {
                        snapshotProblem = "negative speed: " + s.velocity();
                    } else if (s.state() == api.MotionState.STOPPED && s.velocity() != 0.0) {
                        snapshotProblem = "stopped but speed " + s.velocity();
                    } else if (s.alignedFloor() != FloorSensors.currentFloor(s.position())) {
                        snapshotProblem = "aligned floor does not match position";
                    }
                }
            }
        }, "watcher");
        watcher.setDaemon(true);
        watcher.start();
        return watcher;
    }

    /**
     * Asserts that the supplied condition is true and reports the message if it is false.
     *
     * @param condition condition that must be true for the check to pass
     * @param what description printed when the check fails
     */
    private static void check(boolean condition, String what) {
        checksRun++;
        if (!condition) {
            checksFailed++;
            System.out.println("FAIL: " + what);
        }
    }
}