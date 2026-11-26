package T04ElevatorMotion.src.api;

/** Everything that only reads the cabin: state, floor sensors, safety levers. */
public interface CabinAssemblyAPI extends CabinStateAPI, SensorAPI, SafetyLeverAPI {}
