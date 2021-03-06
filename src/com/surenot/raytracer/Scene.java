package com.surenot.raytracer;

import com.surenot.raytracer.primitives.*;
import com.surenot.raytracer.shapes.Light3D;
import com.surenot.raytracer.shapes.Shape3D;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public final class Scene {
    /*

    Z
               X
    |
    |        /
    |   ____/________________
    |  |        SCREEN       |
    |  |                     |
    |  |                     |
    |__|________x Observer   |
    |  |________|____________|
    | /         |
    |/__________|________________________  Y

    */

    public final static double MAX_AMBIENT_LIGHT_INTENSITY = 0.3;
    public final static double MAX_DIFFUSE_LIGHT_INTENSITY = 1 - MAX_AMBIENT_LIGHT_INTENSITY;
    public final static int ANTI_ALIASING = 1;

    private final Collection<Collection<Ray>> newScreen;
    private final Collection<Shape3D> shapes;
    private final Collection<Light3D> lights;
    private final BufferedImage image;

    public Scene(final Point3D observer,
                 final Point3D origin,
                 final Dimension2D screenSize,
                 final int pixelCountX, final int pixelCountY,
                 final Collection<Shape3D> shapes) {
        if (observer == null || origin == null || screenSize == null || shapes == null) {
            throw new IllegalArgumentException();
        }
        if (screenSize.getX() <= 0) throw new IllegalArgumentException();
        if (screenSize.getY() <= 0) throw new IllegalArgumentException();
        if (pixelCountX <= 0) throw new IllegalArgumentException();
        if (pixelCountY <= 0) throw new IllegalArgumentException();

        int aaPixelCountX = pixelCountX * ANTI_ALIASING;
        int aaPixelCountY = pixelCountY * ANTI_ALIASING;

        this.image = new BufferedImage(pixelCountY, pixelCountX, BufferedImage.TYPE_INT_RGB);
        this.newScreen = new ArrayList<>(pixelCountX * pixelCountY);
        this.shapes = new ArrayList<>(shapes);
        this.lights = new ArrayList<>(shapes.stream()
                .filter((shape) -> shape instanceof Light3D)
                .map(light -> (Light3D) light)
                .collect(Collectors.toList()));

        double pixelSizeY = screenSize.getX() / pixelCountX;
        double pixelSizeZ = screenSize.getY() / pixelCountY;
        for (int i = 0; i < pixelCountY; i++) {
            for (int j = 0; j < pixelCountX; j++) {
                ArrayList<Ray> list = new ArrayList();
                for ( int x = 0; x < ANTI_ALIASING; x++ ){
                    for ( int y = 0; y < ANTI_ALIASING; y++ ){
                        list.add(new Ray(i, j,
                                new Vector3D(
                                        new Point3D(origin.getX(),
                                            origin.getY() + ( i + x ) * pixelSizeY,
                                            origin.getZ() - ( j + y ) * pixelSizeZ)
                                                .substract(observer),
                                        new Point3D(origin.getX(),
                                            origin.getY() + ( i + x ) * pixelSizeY,
                                            origin.getZ() - ( j + y ) * pixelSizeZ)
                                                .substract(observer),
                                        false)));
                    }
                }
                newScreen.add(list);
            }
        }
    }

    public BufferedImage render() {
        newScreen.parallelStream()
                .forEach(rayList -> {
                    int r = 0, g = 0, b = 0;
                    Integer x = null, y = null;
                    for ( Ray ray : rayList ){
                        if ( x == null ) x = ray.getX();
                        if ( y == null ) y = ray.getY();
                        Color c = new Color(computeColor(ray.getVector()));
                        r += c.getRed();
                        g += c.getGreen();
                        b += c.getBlue();
                    }
                    synchronized (image) {
                        image.setRGB(x, y,
                                new Color(r / rayList.size(), g / rayList.size(), b / rayList.size()).getRGB());
                    }
                });
        return image;
    }

    private int computeColor(final Vector3D v) {
        // TODO Expensive computation, spend some time to optimise
        Impact3D impact = shapes.stream()
                //.filter // TODO Find a heuristic to remove shapes for which we know they will not be hit
                .map(shape -> shape.isHit(v))
                .filter(ri -> !ri.equals(Impact3D.NONE))
                .reduce(Impact3D.NONE, (a, b) -> a.getDistance() < b.getDistance() ? a : b);

        if (impact.equals(Impact3D.NONE)) return Color.BLACK.getRGB();
        if (impact.getImpactedObject().getClass() == Light3D.class) return impact.getImpactedObject().getSurface().getColor();

        Vector3D n = impact.getImpactedObject().getNormal(impact.getPoint());

        final Color impactedColor = new Color(impact.getImpactedObject().getSurface().getColor());
        final double or = impactedColor.getRed() / 255.0;
        final double og = impactedColor.getGreen() / 255.0;
        final double ob = impactedColor.getBlue() / 255.0;

        final double diffuseCoefficient = MAX_DIFFUSE_LIGHT_INTENSITY * impact.getImpactedObject().getSurface().getDiffuseReflectionCoefficient();
        final double tmp = MAX_AMBIENT_LIGHT_INTENSITY * impact.getImpactedObject().getSurface().getAmbientReflectionCoefficient();
        double ambientIntensityR = or * tmp, ambientIntensityG = og * tmp, ambientIntensityB = ob * tmp;
        double diffuseIntensityR = 0, diffuseIntensityG = 0, diffuseIntensityB = 0;
        double specularIntensityR = 0, specularIntensityG = 0, specularIntensityB = 0;
        for ( Light3D light : lights ){
            Vector3D lightVector = new Vector3D(light.getCenter(), impact.getPoint().substract(light.getCenter()), true).normalize();
            final double sqd = lightVector.getOrigin().squareDistance(impact.getPoint());
            if (shapes.stream()
                    .filter(shape -> shape != light && shape != impact.getImpactedObject())
                    .anyMatch(shape -> {
                        Impact3D i = shape.isHit(lightVector);
                        return !i.equals(Impact3D.NONE) &&
                                !i.getPoint().equals(impact.getImpactedObject()) &&
                                i.getSquareDistance() < sqd;
                    })) continue;

            final Color lightColor = new Color(light.getSurface().getColor());
            final double lr = lightColor.getRed() / 255.0;
            final double lg = lightColor.getGreen() / 255.0;
            final double lb = lightColor.getBlue() / 255.0;

            // Diffuse light
            double theta = -n.normalize().scalarProduct(lightVector.normalize());
            double atmosphericAttenuation = Math.min(1,
                    (1 / ( light.getConstantAttenuationCoefficient() +
                            light.getLinearAttenuationCoefficient() *
                                    (lightVector.normalize().getOrigin().distance(impact.getPoint()) + impact.getDistance()) +
                            light.getQuadraticAttenuationCoefficient() *
                                    Math.pow(lightVector.normalize().getOrigin().distance(impact.getPoint()) + impact.getDistance(), 2) )));
            double diffuseIntensity = diffuseCoefficient * theta * atmosphericAttenuation;
            diffuseIntensityR += theta < 0 ? 0 : or * diffuseIntensity;
            diffuseIntensityG += theta < 0 ? 0 : og * diffuseIntensity;
            diffuseIntensityB += theta < 0 ? 0 : ob * diffuseIntensity;

            // Specular light
            Vector3D r = v.normalize().substract(n.normalize().multiply(2).multiply(v.normalize().scalarProduct(n.normalize())));
            double scalarProduct = r.normalize().scalarProduct(lightVector.negate().normalize());
            if ( scalarProduct > 0 ) {
                double specularIntensity = atmosphericAttenuation *
                        impact.getImpactedObject().getSurface().getSpecularReflectionCoefficient() *
                        Math.pow(scalarProduct, impact.getImpactedObject().getSurface().getSpecularReflectionExponent());
                specularIntensityR += lr * specularIntensity;
                specularIntensityG += lg * specularIntensity;
                specularIntensityB += lb * specularIntensity;
            }
        }
        final double intensityR = ambientIntensityR + diffuseIntensityR + specularIntensityR;
        final double intensityG = ambientIntensityG + diffuseIntensityG + specularIntensityG;
        final double intensityB = ambientIntensityB + diffuseIntensityB + specularIntensityB;

        return new Color(
                (float) (Math.max(Math.min(intensityR, 1), 0)),
                (float) (Math.max(Math.min(intensityG, 1), 0)),
                (float) (Math.max(Math.min(intensityB, 1), 0)))
                .getRGB();
    }
}