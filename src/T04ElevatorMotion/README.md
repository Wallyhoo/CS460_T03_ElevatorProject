# ElevatorMotion

Cabin assembly for the CS 460 elevator project (UNM): a simulation of four elevator cars in a ten-floor building, a JavaFX GUI that shows them, and a gateway that connects each car to the Software Bus.

Each car is a motion simulator (acceleration, cruising, braking, leveling), a trip controller that takes the car to a floor, and a bus gateway that receives calls and reports the car's state.

## Requirements

- Java 25 with JavaFX (we use the Zulu JDK FX build)
- IntelliJ IDEA (plain project, no Maven or Gradle)

## Running it

Run `Main`. With no arguments, the four cars run on a built-in stand-in bus and move on their own (demo mode). The window shows all four shafts and a single-elevator view, and has a slider that changes the simulation speed.

### Using Team03's Software Bus

1. Start the bus: run `SoftwareBus.SBMain` with the program argument `6000`.
2. Run `Main` with the program argument `--bus=localhost:6000` (or, like Team03's README, two plain arguments: `localhost 6000`).

Each car opens its own connection to the bus. Use the IP address of the bus machine instead of `localhost` to connect to a bus running elsewhere.

In the default demo mode the cars drive themselves, so no calls come over the bus. To see calls arrive over the bus, use one of the harness modes below.

### Modes

Change the `MODE` constant at the top of `Main` and run again.

| Mode | What it does |
|---|---|
| `DEMO` | Every car follows its own route |
| `ROUTE_HARNESS` | Route test for elevator 1 with its own scheduler |
| `TOP_BOTTOM_HARNESS` | Elevator 1 goes between the top and bottom floors |
| `NUDGE_HARNESS` | Elevator 1 brakes late on purpose, so you can watch it level itself with small nudges |
| `LEVER_TOP_HARNESS` | Elevator 1 runs away upward and trips the top safety lever |
| `LEVER_BOTTOM_HARNESS` | Elevator 1 runs away downward and trips the bottom safety lever |
| `EVERY_TWO_HARNESS` | Elevator 1 stops at floors 3, 5, 7 and 9 |

The harnesses play the command center: they send calls over the bus (stand-in or real) and wait for the car to arrive.

## Checks

Every class in `src/checks` has its own `main` and prints how many checks ran and how many failed. Run them after any change. Examples:

- `MotionEngineCheck`, `TripChecks`, `NudgeHarnessChecks`: physics and trip control
- `BusMessageChecks`, `BusCodesChecks`, `BusStandInChecks`, `BusCabinGatewayChecks`: the bus layer
- `ThreadedCarsChecks`, `CabinAssemblyChecks`: four cars on one bus, one thread each
- `SocketBusChecks`, `BusSourceChecks`: the real bus (these start Team03's server on ports 6100 and 6101, so stop any bus already using those ports)

## How the simulation works

- Fixed time step. Each tick moves the car by `speed x TIME_STEP`, then updates the speed (accelerate, cruise or decelerate). `TIME_STEP` is derived from the cruise speed so a step never jumps over a floor's sensor zone.
- Trips are judged by distance, not time. The trip controller starts braking when the remaining distance equals the braking distance at the current speed.
- Overshoot is real. Braking is decided once per tick, so the car stops slightly past the floor. The controller then corrects it with small nudges (0.1 ft) until it is level.
- Safety levers sit one foot beyond the top and bottom floors. If a car reaches one, it stops, the motor locks out, and a fault is announced.

Defaults: 10 floors, 14 ft per floor, acceleration and braking 2 ft/s², cruise speed 4 ft/s.

## Code layout

| Package | Purpose |
|---|---|
| `api` | Interfaces and plain data (motion controller API, cabin snapshot, shaft specification) |
| `sim` | Motion engine and floor sensors |
| `manager` | Trip controller, simulation manager (timing), floor sequencer |
| `bus` | Messages, topic codes, stand-in bus, socket client for the real bus, cabin gateway |
| `assembly` | One complete car (engine, trip controller, gateway) and its thread |
| `harness` | Test scenarios that play the command center |
| `checks` | Automated checks |
| `gui` | JavaFX views; they only read the car through the API |
| `SoftwareBus`, `Utilities` | Team03's bus code, copied unchanged |

Threading: each car runs on its own thread and is the only writer to its own state. The GUI reads immutable snapshots.

## Bus messages

We follow Team03's approved message catalog (`Utilities/Topics.java`). The subtopic is the elevator number (1 to 4), and each value sits in its own payload byte starting at P1.

| Direction | Topic | Meaning |
|---|---|---|
| Receive | `CABIN_CALL` (6), `GOTO` (15), `SERVICE` (16) | P1 = floor to go to (we have no doors, so all three act the same) |
| Send | `CABIN_STATUS` (3) | Only when the car has stopped and is level: P1 = 1, P2 = floor, P3 = 0 (idle) |
| Send | `ALIGN` (9) | When the shaft-end sensors change: P1 = 0 none, 1 bottom (floor 1), 2 top (floor 10) |
| Send | `CAR_FAULT` (20) | Our own message, not in the catalog: P1 = 1 top lever hit, 2 bottom lever hit |

Not handled yet: `FIRE`, `ELEVATOR_COMMAND`, `MOTOR_COMMAND` and the door topics.

## Credits

`SoftwareBus` and `Utilities` come from Team03's repository and are used as is.
