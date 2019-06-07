/*
 * Copyright 2017 Kris_McGlinn.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tcd;

import java.awt.geom.AffineTransform;

/**
 *
 * @author Kris_McGlinn
 */
public class Rotation {
    

    private double cos, sin; // this is secretly a 'complex number', woohoo!  

    public Rotation() {
      cos = 1.0f;
      sin = 0.0f;
    }

    public void setAngle(double radians) 
    {
       cos = (double) Math.cos(radians);
       sin = (double) Math.sin(radians);
    }

    public double[] rotateAroundX(double[] src) 
    {
        double[] dst = new double[3];
        //x, y, z
        double y = src[1], z = src[2];
        dst[0] = src[0];
        dst[1] = cos * y - sin * z;
        dst[2] = sin * y + cos * z;
        
        return dst;
    }

    public double[] rotateAroundY(double[] src) 
    {  
        double[] dst = new double[3];
        
        double x = src[0], z = src[2];
        dst[0] = cos * x - sin * z;
        dst[1] = src[1];
        dst[2] = sin * x + cos * z;
        
        return dst;
    }

    public double[] rotateAroundZ(double[] src) 
    {  
        double[] dst = new double[3];
        
        double x = src[0], y = src[1];
        dst[0] = cos * x - sin * y;
        dst[1] = sin * x + cos * y;
        dst[2] = src[2];   
        
        return dst;
    }

    public double[] rotate(double angle, double[] pt){

        AffineTransform.getRotateInstance(Math.toRadians(angle), 0, 0).transform(pt, 0, pt, 0, 1); // specifying to use this double[] to hold coords
//        double newX = pt[0];
//        double newY = pt[1];
        
        return pt;
    }
    
}
