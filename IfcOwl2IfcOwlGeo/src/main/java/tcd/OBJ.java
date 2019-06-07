package tcd;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;

/*
 * Copyright 2019 Joseph Donovan, Trinity College Dublin
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class OBJ {
	
    private String ns_ifcowl;
    private String ns_express;
	
	private ArrayList<double[]> pointList = new ArrayList<double[]>();
	
	public void addToPointList(double[] polylineStart, double[] polylineEnd, double storeyHeight, double wallHeight) {		
		System.out.println("\n**Creating OBJ Vertices**");
		double[] wallVertex1 = getWallVertexXYZ(polylineStart, storeyHeight);
		double[] wallVertex2 = getWallVertexXYZ(polylineEnd, storeyHeight);
		
		double topWallCoordHeight = storeyHeight + wallHeight;
		double[] wallVertex3 = getWallVertexXYZ(polylineStart, topWallCoordHeight);
		double[] wallVertex4 = getWallVertexXYZ(polylineEnd, topWallCoordHeight);
		
		
		System.out.println("Wall height + Storey height = " + String.valueOf(wallHeight) + " + " + String.valueOf(storeyHeight) + " = " + topWallCoordHeight);
		
		System.out.println("OBJ Vertex1: " + wallVertex1[0] + ", " + wallVertex1[1] + ", " + wallVertex1[2]);
		System.out.println("OBJ Vertex2: " + wallVertex2[0] + ", " + wallVertex2[1] + ", " + wallVertex2[2]);
		System.out.println("OBJ Vertex3: " + wallVertex3[0] + ", " + wallVertex3[1] + ", " + wallVertex3[2]);
		System.out.println("OBJ Vertex4: " + wallVertex4[0] + ", " + wallVertex4[1] + ", " + wallVertex4[2]);
		
		pointList.add(wallVertex1);
		pointList.add(wallVertex2);
		pointList.add(wallVertex4);		// reverse order of 3rd and 4th to for face ordering
		pointList.add(wallVertex3);
		System.out.println("**Finished OBJ Vertices**\n");
	}
	
	private double[] getWallVertexXYZ(double[] polylineCoord, double storeyHeight) {
		double[] vertex = new double[3];
		vertex[0] = polylineCoord[0];
		vertex[1] = polylineCoord[1];
		vertex[2] = storeyHeight;
		return vertex;
	}
	
	public void writeOBJ(String filePrefix) {
		String outputFile = filePrefix + "_obj.obj";
		
		try {
			
			OutputStreamWriter char_output = new OutputStreamWriter(new FileOutputStream("obj_output\\" + outputFile),
					Charset.forName("UTF-8").newEncoder());
			BufferedWriter out = new BufferedWriter(char_output);
			
			for(double[] point : pointList) {
				out.write("v " + point[0] + " " + point[1] + " " + point[2] + "\n");
			}
			
			out.write("\n");
			
			int count = 1;
			
			// Write the face definitions as sequential ordering of four vertices, starting at 1
			for(int i = 0; i < pointList.size(); i++) {
				if(i % 4 == 0) {
					out.write("f " + count);
				} else if(count % 4 == 0) { 
					out.write(" " + count + "\n");
				} else {
					out.write(" " + count);
				}
				count++;
			}
			
			out.close();
		} catch (IOException e) {
			System.out.println("Unable to generate " + outputFile);
			e.printStackTrace();
		}
	}
	
	public void setIfcowlPrefix(String ns_ifcowl) {
		this.ns_ifcowl = ns_ifcowl;
	}
	
	public void setExpressPrefix(String ns_express) {
		this.ns_express = ns_express;
	}
	
	public String getIfcowlPrefix() {
		return ns_ifcowl;
	}
	
	public String getExpressPrefix() {
		return ns_express;
	}
}
