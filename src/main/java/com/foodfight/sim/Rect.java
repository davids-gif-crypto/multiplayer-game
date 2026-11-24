package com.foodfight.sim;

/**
 * An axis aligned rectangle (its sides are straight up and down, and
 * straight left and right). (x, y) is the top left corner, and y grows
 * downward like on a screen.
 */
public record Rect(double x, double y, double w, double h) {

    public double left() {
        return x;
    }

    public double right() {
        return x + w;
    }

    public double top() {
        return y;
    }

    public double bottom() {
        return y + h;
    }

    /** True if the two rectangles share any area. Touching edges do not count. */
    public boolean overlaps(Rect other) {
        return left() < other.right() && other.left() < right()
                && top() < other.bottom() && other.top() < bottom();
    }

    /** True if the point is inside or on the edge. */
    public boolean contains(double px, double py) {
        return px >= left() && px <= right() && py >= top() && py <= bottom();
    }
}
