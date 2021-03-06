package com.surenot.raytracer.shapes;

import com.surenot.raytracer.primitives.Impact3D;
import com.surenot.raytracer.primitives.Point3D;
import com.surenot.raytracer.primitives.Surface;
import com.surenot.raytracer.primitives.Vector3D;

import java.awt.*;

/**
 * Created by surenot on 1/15/16.
 */
public class Plane implements Shape3D {

    private final Vector3D vector;
    private final Vector3D normal;
    private final Surface surface;

    public Plane(final Vector3D vector, final Vector3D normal){
        this(vector, normal, new Surface(Color.DARK_GRAY.getRGB(), 1, 0.5, 0.2, 50));
    }

    public Plane(final Vector3D vector, final Vector3D normal, final Surface surface){
        this.vector = vector;
        this.normal = normal;
        this.surface = surface;
    }

    public Vector3D getVector() {
        return vector;
    }

    public Vector3D getNormal() {
        return normal;
    }

    @Override
    public Impact3D isHit(Vector3D v) {
        final double vn = v.normalize().scalarProduct(normal);
        final double pvn = normal.scalarProduct(vector.getOrigin().substract(v.getOrigin()));
        if ( vn == 0 ) {
            return pvn == 0 ? Impact3D.NONE : Impact3D.NONE;
        }
        final double d = pvn / vn;
        return d < 0 ? Impact3D.NONE : new Impact3D(v, v.normalize().getDirection().multiply(d), this, d);
    }

    @Override
    public Vector3D getNormal(Point3D p) {
        return normal;
    }

    @Override
    public Point3D getCenter() {
        return Point3D.NONE;
    }

    @Override
    public Surface getSurface() {
        return surface;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Plane plane = (Plane) o;

        if (vector != null ? !vector.equals(plane.vector) : plane.vector != null) return false;
        return !(normal != null ? !normal.equals(plane.normal) : plane.normal != null);

    }

    @Override
    public int hashCode() {
        int result = vector != null ? vector.hashCode() : 0;
        result = 31 * result + (normal != null ? normal.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Plane{" +
                "vector=" + vector +
                ", normal=" + normal +
                '}';
    }
}
