package T04ElevatorMotion.src.gui;

import api.ShaftSpec;

/** Turns feet along the shaft into pixels in the drawing, for one shaft specification. */
public final class ViewScale {

    public static final double pixelsPerFoot = 20;
    public static final double cabinSize = 8;
    public static final double headroomAboveTopFloor = 12;   //ft of shaft drawn above the top floor
    public static final double pitBelowFloorOne = 4;         //ft of shaft drawn below floor 1

    private final ShaftSpec spec;
    private final double floorOneYValue;
    private final double shaftHeight;

    /**
     * Creates and initializes the ViewScale instance.
     *
     * @param spec shaft dimensions and motion configuration
     */
    public ViewScale(ShaftSpec spec) {
        this.spec = spec;
        double travel = spec.floorPosition(spec.floors());   //feet between floor 1 and the top floor
        this.floorOneYValue = (travel + headroomAboveTopFloor) * pixelsPerFoot;
        this.shaftHeight = (headroomAboveTopFloor + travel + pitBelowFloorOne) * pixelsPerFoot;
    }

    /**
     * Returns the number of floors in the shaft.
     *
     * @return the resulting integer value
     */
    public int floorCount() {
        return spec.floors();
    }

    /** Feet past floor 1 and the top floor where the safety levers sit.
     *
     * @return the resulting measurement or calculated value
     */
    public double leverMargin() {
        return spec.leverMargin();
    }

    /** Height of the whole drawn shaft in pixels.
     *
     * @return the resulting measurement or calculated value
     */
    public double shaftHeight() {
        return shaftHeight;
    }

    /**
     * Converts a floor number to its vertical position in feet.
     *
     * @param floorNumber floor number
     * @return the vertical position in feet
     */
    public double floorToFeet(int floorNumber) {
        return spec.floorPosition(floorNumber);
    }

    /**
     * Converts a position in feet to the view’s world-Y coordinate.
     *
     * @param feet distance or position in feet
     * @return the corresponding world-Y view coordinate
     */
    public double feetToWorldYValue(double feet) {
        return floorOneYValue - feet * pixelsPerFoot;
    }
}