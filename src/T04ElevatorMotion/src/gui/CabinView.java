package T04ElevatorMotion.src.gui;

import T04ElevatorMotion.src.api.CabinAssemblyAPI;
import T04ElevatorMotion.src.api.ShaftSpec;
import java.util.function.IntSupplier;

/** Everything the GUI needs to draw one cabin. Read-only.
 *
 * @param name display name for this cabin or component
 * @param cabin read-only cabin model used by the view
 * @param nudgingFloor supplier of the floor currently being corrected by nudging
 * @param spec shaft dimensions and motion configuration
 */
public record CabinView(String name, CabinAssemblyAPI cabin, IntSupplier nudgingFloor, ShaftSpec spec) {}
