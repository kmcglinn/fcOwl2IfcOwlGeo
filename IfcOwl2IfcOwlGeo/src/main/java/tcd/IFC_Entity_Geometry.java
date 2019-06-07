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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.jena.rdf.model.Resource;

/**
 *
 * @author Kris_McGlinn
 */
public class IFC_Entity_Geometry 
{

    public List location_matrix;
    public List direction_matrix;


    public double[] location = new double[3]; //x, y, z
    public double[] direction = new double[3];
    public String guid;
    public Resource resourceURI;
    
    public IFC_Entity_Geometry()
    {
        this.direction_matrix = new ArrayList();
        this.location_matrix = new ArrayList();
        this.location = new double[]{0.0, 0.0, 0.0};
        this.direction = new double[]{0.0, 0.0, 0.0};
        this.guid = "";
        
    }
    
    public IFC_Entity_Geometry(double[] l, double[] d, String g)
    {
        this.direction_matrix = new ArrayList();
        
        this.location = l;
        this.direction = d;
        this.guid = g;
        
    }
    
    public List getLocation_matrix() {
        return location_matrix;
    }

    public void setLocation_matrix(List location_matrix) {
        this.location_matrix = location_matrix;
    }

    public void addLocation_matrix(double[] location) {
        this.location_matrix.add(location);
    }

    
    public List getDirection_matrix() {
        return direction_matrix;
    }

    public void setDirection_matrix(List direction_matrix) {
        this.direction_matrix = direction_matrix;
    }
    
    public void addDirection_matrix(double[] direction) {
        this.direction_matrix.add(direction);
    }

    
    public double[] getLocation() {
        return location;
    }

    public void setLocation(double[] location) {
        this.location = location;
    }

    public double[] getDirection() {
        return direction;
    }

    public void setDirection(double[] direction) {
        this.direction = direction;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }
    
    @Override
    public String toString() {
        return "IFC_Entity_Geometry{" + "location=" + Arrays.toString(location) + ", direction=" + Arrays.toString(direction) + ", guid=" + guid + '}';
    }
    
    public void setResourceURI(Resource resourceURI) {
    	this.resourceURI = resourceURI;
    }
    
    public Resource getResourceURI() {
    	return resourceURI;
    }
    
}
