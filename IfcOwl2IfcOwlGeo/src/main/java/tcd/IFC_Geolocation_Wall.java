package tcd;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.RDF;

import static tcd.Coordinates.convertDe;

/*
 * Copyright 2017 Kris McGlinn, Adapt Centre, Trinity College University, Dublin, Ireland 
 * This code builds upon code developed by Pieter Pauwels for deleting geoemtry from IFC models, 
 * called SimpleBIM - https://github.com/pipauwel/IFCtoSimpleBIM/blob/master/src/main/java/be/ugent/Cleaner.java
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License atf
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class IFC_Geolocation_Wall {	
	
    //String inputfile = "AC20-FZK-Haus.ttl";		// IFC4_ADD1
    //String inputfile = "Barcelona_Pavilion.ttl";	// IFC2X3_TC1
    //String inputfile = "20170804_Musterhaus_MIT.ttl";	// IFC2X3_TC1
    //String inputfile = "20170601_Mauer_BmB.ttl";	// IFC2X3_TC1
    //String inputfile = "20160414office_model_CV2_fordesign.ttl";
	//String inputfile = "Duplex_A_20110907_optimized.ttl";	// IFC2X3_TC1
    String inputfile = "Simple3-storeytestbuilding.ttl";	// IFC2X3_TC1
	//String inputfile = "smallhouse_saref.ttl";		// IFC2X3_TC1
    String outputfile = "";
    
    // ifcowl prefix version is set in main() 
    private static String ns_ifcowl;

    String geometry_store = "{\"geometry\":{\"paths\":[";
    String geometry_wkt_store = "MULTILINESTRING (";
    Set<String> wktPointSet = new LinkedHashSet<String>();
    List<String> wktPoints = new LinkedList<String>();

    private static Model model;
    private static Model botModel;
    
    private static NDSFutility ndsf;
    private static Rotation rot;
    private static BuildingTopologyOntology  bot;
    private static OBJ obj;
    
    double metric_adjustment = 1;
    
    private static Property ifcSiteProperty;
    private static Resource ifcSiteResource;
    private static List latitude = new ArrayList();
    private static double latitude_decimal;
    private static List longitude = new ArrayList();
    private static double longitude_decimal;
    private static List trueNorth = new ArrayList();
    
    // Map of the building storey height to the IfcBuildingStorey resource
    private static Map<String, Resource> storeyResourceMap;
    
    // Map of the IfcBuildingStorey resource string to its WKT MULTILINESTRING()
    private static Map<String, String> storeyMultistringMap = new HashMap<String, String>();
    
    // Placement of current wall's storey
    private static List storeyPlacement;
    
    private static double geolocation_x_epsg3857;
    private static double geolocation_y_epsg3857;
        
    // Current wall count
    private static int count = 0;
    
    // Metres or millimetres
    private static double scale = 0.0;
        
    private static double trueNorthRotationAngle = 0.0;	// This angle is used to rotate each start and end polyline coord to the True North direction

    private final String ns_list = "https://w3id.org/list#";
    private final String ns_geo = "http://www.opengis.net/ont/geosparql#";
    private final String ns_express = "https://w3id.org/express#";
    private final String ns_bot = "https://w3id.org/bot#";
    
//    private static String Entity_Array[] = {"IfcBeam", "IfcBeamStandardCase", "IfcChimney", "IfcColumn", "IfcCovering", "IfcCurtainWall", "IfcDoor", "IfcDoorStandardCase", 
//    "IfcPlate", "IfcPlateStandardCase", "IfcRailing", "IfcRamp", "IfcRoof", "IfcSlab", "IfcStair", "IfcWall", "IfcWallStandardCase", "IfcWindow", "IfcWindowStandardCase"};
    //This is list is incomplete
    private static final String Entity_Array[] = {"IfcWallStandardCase"};
    //This is list is incomplete

    public IFC_Geolocation_Wall() {	
    }

    /**
     * @param arg0: filename + extension (inputfile)
     * @param arg1: filename + extension (outputfile)
     * @throws IOException 
     */
    public static void main(String[] args) throws IOException {	
        System.out.println("Working Directory = " +
        System.getProperty("user.dir"));
        
        IFC_Geolocation_Wall c = new IFC_Geolocation_Wall();
        ndsf = new NDSFutility();
        rot = new Rotation();
        bot = new BuildingTopologyOntology();
        obj = new OBJ();
     
        c.checkArguments(args);
        
        System.out.println("Loading File"); 
        model = c.loadFile();  
        
        // Check if ifcowl version is ADD1_IFC4 or IFC2X3_TC1
        ns_ifcowl = model.getNsPrefixURI("ifcowl");
        System.out.println("IFCOWL VERSION: " + model.getNsPrefixURI("ifcowl"));
        
        // Set BOT prefixes
        bot.setBotPrefix(c.ns_bot);
        bot.setGeoPrefix(c.ns_geo);
        bot.setIfcowlPrefix(ns_ifcowl);
        botModel = ModelFactory.createDefaultModel();
          
        obj.setIfcowlPrefix(ns_ifcowl);
        obj.setExpressPrefix(c.ns_express);
        
        // True north point extraction
        System.out.println("Extracting True North"); 
        trueNorth = c.returnTrueNorth(model);
        trueNorthRotationAngle = c.returnTrueNorthRotationAngle(trueNorth);
        
        // Geolocation extraction
        System.out.println("Adding WKT Site Geolocation as WKT to Model"); 
        ifcSiteProperty = model.createProperty( ns_ifcowl + "IfcSite" ); 
        ifcSiteResource = c.returnLongLat(model); 
        model = c.addWKTGeolocationToModel(model, ifcSiteResource);
        System.out.println("WKT Geolocation succesfully added to Model");
                
        // Create a xy (epsg3857) version of the building geolocation
        epsg4326_to_epsg3857(latitude_decimal, longitude_decimal);
        
        // Set whether the model is defined in metres or millimetres
        c.setLengthUnitScaleValue(model);
        
        // Find all building Storeys
        storeyResourceMap = c.findStoreys(model);
                 
        // Process all wall entities
        addAllEntities(c, model, Entity_Array);
        
        // BOT Model
        System.out.println("\nCreating BOT building component relationship hierarchy...");
        botModel = bot.createBotHasRelationships(model, botModel);
        bot.writeModel(botModel, c.inputfile.split(".ttl")[0]);

        // ifcowl Model
        c.writeModel(model);
        
        // OBJ Model
        obj.writeOBJ(c.inputfile.split(".ttl")[0]);
    }

    public static void addAllEntities(IFC_Geolocation_Wall c, Model original, String[] s){
        Model m = original;
        for (String item : s) {
            System.out.println("EXTRACTING ENTITY " + item + "'s GEOLOCATION AS WKT");
            Property ifcEntityProperty = model.createProperty(ns_ifcowl + item);
            c.returnEntityLocalPlacement(m, ifcEntityProperty.asResource());
        }
    }
    
    //Author Kris McGlinn - This method returns the longitude and latitude by making use of traverseList()
    private Model returnEntityLocalPlacement(Model original, Resource entityResource){ 
                
        Model m = original;
        StmtIterator iter = m.listStatements( null, RDF.type, entityResource );
        
        while ( iter.hasNext() ) 
        {
        	System.out.println("------ START WALL " + count  + " ------");
        	
            Statement stmt = iter.nextStatement();
            List ifcEntity_localPlacement;// = new ArrayList(); //A list of the local placements of each story
            List ifcEntity_relativePlacement;// = new ArrayList();
  
            ifcEntity_localPlacement = returnLocalPlacementOfEntity(m, stmt.getSubject());
//            System.out.println("LOCAL PLACEMENT OF ENTITY: " + stmt.getSubject() + " is "+ ifcEntity_localPlacement);
            
            ifcEntity_relativePlacement = returnRelativePlacement(m, stmt.getSubject());
            
            System.out.println("STOREY: " + ifcEntity_relativePlacement.get(ifcEntity_relativePlacement.size()-1).toString() + " , has wall: " + stmt.getSubject().toString());
            storeyPlacement = (List)ifcEntity_relativePlacement.get(ifcEntity_relativePlacement.size()-1);
            
            // Check that the wall is an external wall
            //boolean externalWall = checkExternalWall(m, stmt.getSubject());
            
            // Uncomment this if you want to represent both internal and external building walls
            boolean externalWall = true;
            
            if(externalWall) {

            	//System.out.println("PRINTING LIST OF ALL RELATIVE PLACEMENTS: " + ifcEntity_relativePlacement);
	            ifcEntity_relativePlacement.add(ifcEntity_localPlacement);            
	              
	            System.out.println("PRINTING LIST OF RELATIVE PLACEMENTS + LOCAL PLACEMENT OF ENTITY: " + stmt.getSubject() + " = "+ ifcEntity_relativePlacement);
	            IFC_Entity_Geometry ifc = returnCartesianLocation(ifcEntity_localPlacement); 
	            ifc.setResourceURI(stmt.getSubject());
	            
	            addWKTGeolocationToEntity(m, stmt.getSubject(), ifc, false);  //Attach a geolocation to an entity
	            
	            returnEntityGeometry(m, stmt.getSubject(), ifc);
	        }
            
            count++;
            System.out.println("------ END WALL ------\n");
        }       
        
        System.out.println();
        System.out.println(geometry_store);
        
        // Printing the WKT MULTILINESTRING() for each storey
        for(Entry<String, String> storey : storeyMultistringMap.entrySet()) {
        	String wkt = storey.getValue().substring(0, storey.getValue().length()-2) + " )";
        	System.out.println("\n***Storey: " + storey.getKey() + " has WKT:\n" + wkt);
        }
        
        // If you want to create a WKT POLYGON() from the walls uncomment the following lines
        // This only works for AC20-FZK-Haus.tll at the moment
        /*if(wktPoints.size() > 0) {
	        String firstPoint = wktPoints.get(0);
	        wktPoints.add(firstPoint);	//to complete the connected polygon geometry
	        String[] pointArray = wktPoints.toArray(new String [0]);
	        
	        // Temporary solution for 4 walled external building geometry
	        // Need to reorder the middle points (point 3 and point 4) so the points are connected in sequential order
			String temp = pointArray[2];
			pointArray[2] = pointArray[3];
			pointArray[3] = temp;
			
	        String wktPolygon = "POLYGON((";
	
	        for(int i = 0; i < pointArray.length-1; i++) {
	        	wktPolygon = wktPolygon + pointArray[i] + ", ";
	        }
	        wktPolygon = wktPolygon + pointArray[pointArray.length-1] + ") )";
	        
	        System.out.println("POLYGON: " + wktPolygon);
        }*/
        geometry_wkt_store = geometry_wkt_store.substring(0, geometry_wkt_store.length()-2) + ")";
        System.out.println("\n***Building WKT:\n" +geometry_wkt_store);
        addWKTMultilinestringToModel(m, geometry_wkt_store);   
        //addWKTMultilinestringToModel(m, r, wktPolygon);

        return m;
    }
    
    private boolean checkExternalWall(Model m, Resource wallResource) {
    	
		Property relatedElementProperty = m.createProperty(ns_ifcowl + "relatedBuildingElement_IfcRelSpaceBoundary");
		Property externalInteralProperty = m.createProperty(ns_ifcowl + "internalOrExternalBoundary_IfcRelSpaceBoundary");
		Resource externalResource = m.createResource(ns_ifcowl + "EXTERNAL"); 
		
		StmtIterator iter = m.listStatements(null, relatedElementProperty, (RDFNode)wallResource);       
		
		// Check if the ifcowl wall even has the external wall property
		if(!iter.hasNext()) {
			System.out.println("No external wall property");
			return true;
		}
		
	    while ( iter.hasNext() ) {
	    	Statement stmt = iter.nextStatement();
	    	
	    	Resource relationResource = stmt.getSubject();
	    	
	    	if(relationResource.hasProperty(externalInteralProperty, externalResource)) {
				System.out.println("EXTERNAL WALL: " + wallResource.toString());
				return true;
	    	}
	    	
	    }
    	
		System.out.println("INTERNAL WALL: " + wallResource.toString());

        return false;
    }
    
            
    //Author Kris McGlinn - This function takes the Model and a resources, and adds it to that resourse in the model
    //For wkt literal, a seperate class WktLiteral java is required, to add the literal datatype to the Model
    private Model addWKTGeolocationToModel(Model original, Resource r)
    {
        original.setNsPrefix("geo", ns_geo);
        Model m = original;//ModelFactory.createDefaultModel().add(original);
        
        String wktLiteralID = "urn:geom:";
        Property geo_hasGeometry = m.createProperty( ns_geo + "hasGeometry" );      
        Property unique_guid = m.createProperty( ns_ifcowl + "globalId_IfcRoot" );
        Property unique_guid_string = m.createProperty( ns_express + "hasString" );
        
        StmtIterator iter = m.listStatements(r, unique_guid , (RDFNode) null ); 
        
        
        while ( iter.hasNext() ) 
        {
            Statement stmt = iter.nextStatement(); 
            iter = m.listStatements(stmt.getResource(), unique_guid_string , (RDFNode) null );             
            
            while ( iter.hasNext() ) {

                wktLiteralID = wktLiteralID + iter.nextStatement().getLiteral().toString();
//                System.out.println(wktLiteralID);
            }
        }
        
        Resource rr = m.createResource(wktLiteralID);
                
        m.getResource(r.toString()).addProperty(geo_hasGeometry, rr);
                
        Property geo_asWKT = m.createProperty( ns_geo + "asWKT" );
        
        System.out.println("latitude = " + latitude);
        System.out.println("longitude = " + longitude);

        latitude = longLatNegativeConvert(latitude);
      
        longitude = longLatNegativeConvert(longitude);
        double s1 = convertDe(latitude.get(3) + " " +  latitude.get(2) + " " + latitude.get(1) + "." + latitude.get(0));
        double s2 = convertDe(longitude.get(3) + " " +  longitude.get(2) + " " + longitude.get(1) + "." + longitude.get(0));

        latitude_decimal = s1;
        longitude_decimal = s2;

        String wkt_point = "POINT ("+s2+" "+s1+")";
        
        RDFDatatype rtype = WktLiteral.wktLiteralType; 
        TypeMapper.getInstance().registerDatatype(rtype);     
        Literal l = m.createTypedLiteral(wkt_point, rtype);
        m.getResource(wktLiteralID).addProperty(geo_asWKT, l);
//        
        iter = m.listStatements(m.getResource(wktLiteralID), geo_asWKT , (RDFNode) null ); 
        
        botModel = bot.addGeometryToBotModel(botModel, r, wktLiteralID, wkt_point, "Site");
        
        
        while ( iter.hasNext() ) 
        {
            
            Statement stmt = iter.nextStatement();
            System.out.println("WKT GEOLOCATION OF IFCSITE: " + stmt);
            
        }   

        
        return m;
    }
    
    //Author Kris McGlinn - This function takes the Model and a resources, and adds it to that resourse in the model
    //For wkt literal, a seperate class WktLiteral java is required, to add the literal datatype to the Model
    private Model addWKTGeolocationToEntity(Model original, Resource r, IFC_Entity_Geometry ifc, boolean addToFile)
    {
        original.setNsPrefix("geo", ns_geo);
        Model m = original;//ModelFactory.createDefaultModel().add(original);
        
        String wktLiteralID = "urn:geom:";
        Property geo_hasGeometry = m.createProperty( ns_geo + "hasGeometry" );      
        Property unique_guid = m.createProperty( ns_ifcowl + "globalId_IfcRoot" );
        Property unique_guid_string = m.createProperty( ns_express + "hasString" );
        
        StmtIterator iter = m.listStatements(r, unique_guid , (RDFNode) null ); 
//        System.out.println("PRINTING VALUE FOR RESOURCE: " + r);
        
        double[] coordinate = ifc.getLocation();

               
        while ( iter.hasNext() ) 
        {
            Statement stmt = iter.nextStatement(); 
            iter = m.listStatements(stmt.getResource(), unique_guid_string , (RDFNode) null );             
            
            while ( iter.hasNext() ) 
            {

                wktLiteralID = wktLiteralID + iter.nextStatement().getLiteral().toString();
//                System.out.println("Entity WKT Literal ID: " + wktLiteralID);

            }
            
        }
        
        Resource rr = m.createResource(wktLiteralID);
        
        m.getResource(r.toString()).addProperty(geo_hasGeometry, rr);
           

        Property geo_asWKT = m.createProperty( ns_geo + "asWKT" );

        double[] d2_coordinate = ndsf.xy2ll(longitude_decimal, latitude_decimal, 1, coordinate[1], coordinate[0], 0, 0, 0, 0);

        //Have to swith long and lat for WKT
        if (Double.isNaN(d2_coordinate[1])) {
//            System.out.println("Longitude was NaN");
            d2_coordinate[1] = longitude_decimal;
        }
        if (Double.isNaN(d2_coordinate[0])) {
//            System.out.println("Latitude was NaN");
            d2_coordinate[0] = latitude_decimal;
        }
        
        String wkt_point = "POINT ("+d2_coordinate[1]+" "+d2_coordinate[0]+")";
        
//        global_entity_coordinate = d2_coordinate;
        
        if(addToFile)
        {
            RDFDatatype rtype = WktLiteral.wktLiteralType; 
            TypeMapper.getInstance().registerDatatype(rtype);     
            Literal l = m.createTypedLiteral(wkt_point, rtype);
            m.getResource(wktLiteralID).addProperty(geo_asWKT, l);  
        }
        
        return m;
    }
    
    //Author Kris McGlinn - This function takes the Model and a resources, and adds it to that resourse in the model
    //For wkt literal, a seperate class WktLiteral java is required, to add the literal datatype to the Model
    private Model addWKTPolylineGeolocationToEntity(Model original, Resource r, List l, IFC_Entity_Geometry ifc)
    {
        original.setNsPrefix("geo", ns_geo);
        Model m = original;//ModelFactory.createDefaultModel().add(original);
        
        String wktLiteralID = "urn:geom:";
        Property geo_hasGeometry = m.createProperty( ns_geo + "hasGeometry" );      
        Property unique_guid = m.createProperty( ns_ifcowl + "globalId_IfcRoot" );
        Property unique_guid_string = m.createProperty( ns_express + "hasString" );
        
        StmtIterator iter = m.listStatements(r, unique_guid , (RDFNode) null );       
               
        while ( iter.hasNext() ) 
        {
            Statement stmt = iter.nextStatement(); 
            iter = m.listStatements(stmt.getResource(), unique_guid_string , (RDFNode) null );             
            
            while ( iter.hasNext() ) 
            {

                wktLiteralID = wktLiteralID + iter.nextStatement().getLiteral().toString();
//                System.out.println("Entity WKT Literal ID: " + wktLiteralID);

            }
            
        }
        
        Resource rr = m.createResource(wktLiteralID);
        
        m.getResource(r.toString()).addProperty(geo_hasGeometry, rr);
        
        Property geo_asWKT = m.createProperty( ns_geo + "asWKT" );
    
        int mult = 1;
        String wkt_point = "LINESTRING (";
        String multistring = "(";
        List coord;
        coord = (List) l.get(0);
        
        // Each polyline magnitude has a start coord (eg. [0.0, 0.0, 0.0]) and an end coord (eg. [0.0, 12.0, 0.0])
        // This would represent a polyline with a magnitude of 12 'units'
        // polylineCoordinateStart and polylineCoordinateEnd hold the start and end coordinates of the polyline
        double[] polylineCoordinateStart = new double[3];	// a zero'ed array of 3 doubles
        double[] polylineCoordinateEnd = new double[3];       
        polylineCoordinateEnd[0] = (double)coord.get(0)/metric_adjustment;
        polylineCoordinateEnd[1] = (double)coord.get(1)/metric_adjustment;
        polylineCoordinateEnd[2] = 0.0;
        
        // direction is how the polyline magnitude should be rotated
        double[] direction = ifc.getDirection(); 
        
        System.out.println("POLYLINE MAGNITUDE:" + Arrays.toString(polylineCoordinateStart) + " --> " + Arrays.toString(polylineCoordinateEnd));
        System.out.println("DIRECTION OF POLYLINE: " + Arrays.toString(direction));
       
        // rotate_coordintate is called to rotate the 'polyline end coord' to the specified direction
        // There is no need to rotate the 'polyline start coord' as it is all zeros
        polylineCoordinateEnd = rotate_coordinate(polylineCoordinateEnd, direction);
        System.out.println("POLYLINE AFTER ROTATION: " + Arrays.toString(polylineCoordinateStart) + " --> " + Arrays.toString(polylineCoordinateEnd));
        
        // Here we get the placement of the polyline relative to the rest of the model
        // The rotated polyline has to be translated to the correct position in the global space
        double[] translationCoord = ifc.getLocation();
    	System.out.println("TRANSLATION COORDINATE: " + Arrays.toString(translationCoord));
        
        // Translate both the start and end polyline coords by using the translation coordinate
        polylineCoordinateStart[0] = polylineCoordinateStart[0] + translationCoord[0];
        polylineCoordinateStart[1] = polylineCoordinateStart[1] + translationCoord[1];
        polylineCoordinateEnd[0] = polylineCoordinateEnd[0] + translationCoord[0];
        polylineCoordinateEnd[1] = polylineCoordinateEnd[1] + translationCoord[1];
    	
    	System.out.println("POLYLINE AFTER GLOBAL ROTATION AND TRANSLATION: " + Arrays.toString(polylineCoordinateStart) + " --> " + Arrays.toString(polylineCoordinateEnd));
    	
    	// 'scale' is used to scale down the resulting WKT and OBJ models 
        // Some building walls are length of 1000's, as in millimetres, whereas the output models are in metres
    	polylineCoordinateStart[0] = polylineCoordinateStart[0]/scale;
        polylineCoordinateStart[1] = polylineCoordinateStart[1]/scale;
        polylineCoordinateEnd[0] = polylineCoordinateEnd[0]/scale;
        polylineCoordinateEnd[1] = polylineCoordinateEnd[1]/scale;
    	
    	System.out.println("POLYLINE AFTER SCALING: " + Arrays.toString(polylineCoordinateStart) + " --> " + Arrays.toString(polylineCoordinateEnd));

    	// *** Start of OBJ stuff ***
        double[] objWallCoord1 = new double[2];
        double[] objWallCoord2 = new double[2];
        objWallCoord1[0] = polylineCoordinateStart[0];
        objWallCoord1[1] = polylineCoordinateStart[1];
        objWallCoord2[0] = polylineCoordinateEnd[0];
        objWallCoord2[1] = polylineCoordinateEnd[1];
        
    	double wallHeight = getWallHeight(ifc.getResourceURI(), original);

        List temp = (List)storeyPlacement.get(0);
		double storeyHeight = ((double)temp.get(0));
		
    	obj.addToPointList(objWallCoord1, objWallCoord2, storeyHeight/scale, wallHeight/scale);
    	// *** End of OBJ Stuff ***
    	
    	
    	// Rotate both polyline coords to the true north direction
    	polylineCoordinateStart =  rot.rotate(trueNorthRotationAngle, polylineCoordinateStart);
    	polylineCoordinateEnd = rot.rotate(trueNorthRotationAngle, polylineCoordinateEnd);
    	
    	System.out.println("POLYLINE AFTER TRUE NORTH ROTATION: " + Arrays.toString(polylineCoordinateStart) + " --> " + Arrays.toString(polylineCoordinateEnd));

        double[] location = ifc.getLocation();
        geometry_store = geometry_store + "[[" + location[0] + ", " + location[1] +"], " +"[" + (location[0] + polylineCoordinateEnd[0]) + ", " + (location[1] + polylineCoordinateEnd[1]) +"]] ,||**||";
        
        //double[] d2_coordinate = ndsf.xy2ll(longitude_decimal, latitude_decimal, 1, location[0], location[1], 0, 0, 0, 0);
        //double[] d2_coordinate = ndsf.xy2ll(longitude_decimal, latitude_decimal, 1, polylineCoordinateStart[0], polylineCoordinateStart[1], 0, 0, 0, 0);
        
        // Add the polyline x values to the building geolocation (in epsg3857) and convert to lat/long
    	double[] d2_coordinate = epsg3857_to_epsg4326(geolocation_x_epsg3857 + polylineCoordinateStart[0], geolocation_y_epsg3857 + polylineCoordinateStart[1]);
    	        
        //Have to swith long and lat for WKT
        if (Double.isNaN(d2_coordinate[1])) {
//            System.out.println("Longitude was NaN");
            d2_coordinate[1] = longitude_decimal;
        }
        if (Double.isNaN(d2_coordinate[0])) {
//            System.out.println("Latitude was NaN");
            d2_coordinate[0] = latitude_decimal;
        }
        wkt_point = wkt_point + d2_coordinate[1] + " " + d2_coordinate[0];
        multistring = multistring + d2_coordinate[1] +" " + d2_coordinate[0];
                
        //d2_coordinate = ndsf.xy2ll(longitude_decimal, latitude_decimal, 1, (location[0] + coordinate[0]), (location[1] + coordinate[1]), 0, 0, 0, 0);
        //d2_coordinate = ndsf.xy2ll(longitude_decimal, latitude_decimal, 1, polylineCoordinateEnd[0], polylineCoordinateEnd[1], 0, 0, 0, 0);
        d2_coordinate = epsg3857_to_epsg4326(geolocation_x_epsg3857 + polylineCoordinateEnd[0], geolocation_y_epsg3857 + polylineCoordinateEnd[1]);
        
        //Have to swith long and lat for WKT
        if (Double.isNaN(d2_coordinate[1])) {

            d2_coordinate[1] = longitude_decimal;
        }
        if (Double.isNaN(d2_coordinate[0])) {

            d2_coordinate[0] = latitude_decimal;
        }
        wkt_point = wkt_point + ", " + d2_coordinate[1]+" "+d2_coordinate[0] + ") ";
        wktPoints.add(d2_coordinate[1]+" "+d2_coordinate[0]);
        
        multistring = multistring + ", " +  d2_coordinate[1]+" "+d2_coordinate[0] + ") ";        
        
        System.out.println("Linestring: " + wkt_point);
        geometry_wkt_store = geometry_wkt_store + multistring + ", ";
        
        String height = String.format("%.1f", storeyHeight);
        addToStoreyMultiString(height, multistring);
        
        RDFDatatype rtype = WktLiteral.wktLiteralType; 
        TypeMapper.getInstance().registerDatatype(rtype);     
        Literal lit = m.createTypedLiteral(wkt_point, rtype);
        m.getResource(wktLiteralID).addProperty(geo_asWKT, lit);
//        
        iter = m.listStatements(m.getResource(wktLiteralID), geo_asWKT , (RDFNode) null ); 
        
        
        while ( iter.hasNext() ) {
            Statement stmt = iter.nextStatement();
            System.out.println("WKT GEOLOCATIONS OF ENTITY: " + r + " = " + stmt);
                        
        }   
        
        botModel = bot.addGeometryToBotModel(botModel, r, wktLiteralID, wkt_point, "Element");
        
        return m;
    }
    
    // Lat/ long to xy projection
    private static void epsg4326_to_epsg3857(double latitude, double longitude) {
    	geolocation_x_epsg3857 = longitude * 20037508.34 / 180;
    	geolocation_y_epsg3857 = Math.log(Math.tan((90 + latitude) * Math.PI / 360)) / (Math.PI / 180);
    	geolocation_y_epsg3857 = geolocation_y_epsg3857 * 20037508.34 / 180;
    	
    	System.out.println("CONVERTED long/lat [" + longitude + ", " + latitude + "] to xy [" + geolocation_x_epsg3857 + ", " + geolocation_y_epsg3857 + "]");
    }
    
    // xy to lat/long projection
    private static double[] epsg3857_to_epsg4326(double x, double y) {
    	double lon = x *  180 / 20037508.34 ;
    	double lat = Math.atan(Math.exp(y * Math.PI / 20037508.34)) * 360 / Math.PI - 90; 
    	
    	System.out.println("CONVERTED xy [" + x + ", " + y + "] to long/lat [" + lon + ", " + lat + "]");

    	return new double[] {lat, lon};
    }
    
    public Map<String, Resource> findStoreys(Model m) {
		Map<String, Resource> storeyMap = new HashMap<String, Resource>();
		Property ifcBuildingStoreyProperty = m.createProperty(ns_ifcowl + "IfcBuildingStorey");
		Property elevationProperty = m.createProperty(ns_ifcowl + "elevation_IfcBuildingStorey");
		Property expressHasDouble = m.createProperty(ns_express + "hasDouble");
		
		StmtIterator iter1 = m.listStatements(null, RDF.type, ifcBuildingStoreyProperty);		
		while(iter1.hasNext()) {
			Statement stmt1 = iter1.next();
			
			StmtIterator iter2 = m.listStatements(stmt1.getSubject(), elevationProperty, (RDFNode)null);
			while(iter2.hasNext()) {
				Statement stmt2 = iter2.next();
				
				StmtIterator iter3 = m.listStatements((Resource)stmt2.getObject(), expressHasDouble, (RDFNode)null);
				while(iter3.hasNext()) {
					Statement stmt3 = iter3.next();
					
					double height = stmt3.getLiteral().getDouble();
					String storeyHeight = String.format("%.1f", height);
					System.out.println("STOREY HEIGHT: " + storeyHeight + ", RESOURCE: " + stmt1.getSubject());
					storeyMap.put(storeyHeight, stmt1.getSubject());
				}

			}

		}	
		
		return storeyMap;
	}
    
    // Add wall coords to the WKT MULTILINESTRING() of the current IfcBuildingStorey
    private void addToStoreyMultiString(String height, String multistring) {
    	Resource storey = storeyResourceMap.get(height);
    	
    	System.out.println("STOREY MAP: " + storey.toString());
    	
    	if(!storeyMultistringMap.containsKey(storey.toString())) {
    		storeyMultistringMap.put(storey.toString(), "MULTILINESTRING( " + multistring + " , ");
    	} else {
    		String currentMultistring = storeyMultistringMap.get(storey.toString());
    		currentMultistring = currentMultistring + multistring;
    		storeyMultistringMap.put(storey.toString(), currentMultistring + " , ");
    	}	
    }
    
    // Determine the height of the current wall - this is used in the OBJ vertices
    private double getWallHeight(Resource wallResource, Model m) {
		
    	// Relating properties
    	Property productProperty = m.createProperty(ns_ifcowl + "representation_IfcProduct");
    	Property productRepresentationProperty = m.createProperty(ns_ifcowl + "representations_IfcProductRepresentation");
    	Property hasContentsProperty = m.createProperty(ns_list + "hasContents");
    	Property hasNextProperty = m.createProperty(ns_list + "hasNext");
    	Property itemsRepresentationProperty = m.createProperty(ns_ifcowl + "items_IfcRepresentation");
    	Property boundingBoxZProperty = m.createProperty(ns_ifcowl + "zDim_IfcBoundingBox");
    	Property depthAreaExtrudedSolidProperty = m.createProperty(ns_ifcowl + "depth_IfcExtrudedAreaSolid");
    	Property firstOperandProperty = m.createProperty(ns_ifcowl + "firstOperand_IfcBooleanResult");
    	Property hasDoubleProperty = m.createProperty(ns_express + "hasDouble");
    	
    	// IFC type properties
    	Property booleanClipping = m.createProperty(ns_ifcowl + "IfcBooleanClippingResult");
    	Property boundingBox = m.createProperty(ns_ifcowl + "IfcBoundingBox");
    	Property extrudedAreaSolid = m.createProperty(ns_ifcowl + "IfcExtrudedAreaSolid");
    	
    	System.out.println("CALCULATING WALL HEIGHT FOR: " + wallResource);
    	
    	StmtIterator iter1 = m.listStatements(wallResource, productProperty, (RDFNode)null);	
    	while(iter1.hasNext()) {
    		Statement stmt1 = iter1.next();
    		Resource productShape = (Resource)stmt1.getObject();
    		//System.out.println("1 - " + stmt1.getObject());
    		
        	StmtIterator iter2 = m.listStatements(productShape, productRepresentationProperty, (RDFNode)null);
        	while(iter2.hasNext()) {
        		Statement stmt2 = iter2.next();
        		//System.out.println("2 - " + stmt2.getObject());

        		StmtIterator iterNext = m.listStatements((Resource)stmt2.getObject(), hasNextProperty, (RDFNode)null);
        		while(iterNext.hasNext()) {
            		Statement stmtNext = iterNext.next();
            		//System.out.println("3 - " + stmtNext.getObject());
            	
	        		StmtIterator iter3 = m.listStatements((Resource)stmtNext.getObject(), hasContentsProperty, (RDFNode)null);
	            	while(iter3.hasNext()) {
	            		Statement stmt3 = iter3.next();
	            		//System.out.println("4 - " + stmt3.getObject());
	            		
	            		StmtIterator iter4 = m.listStatements((Resource)stmt3.getObject(), itemsRepresentationProperty, (RDFNode)null);
	            		while(iter4.hasNext()) {
	                		Statement stmt5 = iter4.next();
	
	                		Resource representedItem = (Resource)stmt5.getObject();
	                		//System.out.println("5 - " + representedItem);
	                		
	                		Resource lengthMeasureResource = null;
	                		StmtIterator iterTemp;
	                		
	                		if(representedItem.hasProperty(RDF.type, boundingBox)) {
	                			iterTemp = m.listStatements(representedItem, boundingBoxZProperty, (RDFNode)null);
	                			while(iterTemp.hasNext()) {
	                				Statement stmt6 = iterTemp.next();
	                				lengthMeasureResource = (Resource)stmt6.getObject();
	                			}
	                			
	                		} else if(representedItem.hasProperty(RDF.type, extrudedAreaSolid)) {
	                			iterTemp = m.listStatements(representedItem, depthAreaExtrudedSolidProperty, (RDFNode)null);
                        		while(iterTemp.hasNext()) {
                            		Statement stmt6 = iterTemp.next();
                            		lengthMeasureResource = (Resource)stmt6.getObject();
                        		}
	                			
	                		} else if(representedItem.hasProperty(RDF.type, booleanClipping)) {
	                			
	                			iterTemp = m.listStatements(representedItem, firstOperandProperty, (RDFNode)null);
	                			while(iterTemp.hasNext()) {
	                				Statement stmt6 = iterTemp.next();
	                        		//System.out.println("5a - " + stmt6.getObject());
	
	                				iterTemp = m.listStatements((Resource)stmt6.getObject(), depthAreaExtrudedSolidProperty, (RDFNode)null);
	                        		while(iterTemp.hasNext()) {
	                            		Statement stmt7 = iterTemp.next();
	                            		lengthMeasureResource = (Resource)stmt7.getObject();
	                        		}
	                			}
	                		} 
	
	                		if(lengthMeasureResource == null) {
	                			System.out.println("LengthMeasureResource is null... Error with getting wall height");
	                			break;
	                		}
	                		
	        				//System.out.println("6 - " + lengthMeasureResource);
	                		
	                		StmtIterator iter5 = m.listStatements(lengthMeasureResource, hasDoubleProperty, (RDFNode)null);
	                		while(iter5.hasNext()) {
	                    		Statement stmtHeight = iter5.next();
	                    		//System.out.println("7 - Wall Height Literal = " + stmtHeight.getLiteral().getDouble());
	                    		System.out.println("WALL HEIGHT FOR: " + wallResource + " IS " + stmtHeight.getLiteral().getDouble());
	                    		return stmtHeight.getLiteral().getDouble();                            		
	                		}
	
	            		}
	                   	            		
            		}
	
            	}

        	}

    	}
    	
    	System.out.println("***EXTRUDED AREA SOLID HEIGHT ERROR");
    	return 0.0;
    }
    
    private void setLengthUnitScaleValue(Model m) {
    	Property namedUnit = m.createProperty(ns_ifcowl + "unitType_IfcNamedUnit");
    	Resource lengthUnitResource = m.createResource(ns_ifcowl + "LENGTHUNIT");
    	Property unitPrefix = m.createProperty(ns_ifcowl + "prefix_IfcSIUnit");
    	Resource millimetresResource = m.createResource(ns_ifcowl + "MILLI");
    	Resource decimetresResource = m.createResource(ns_ifcowl + "DECI");
    	
    	StmtIterator iter1 = m.listStatements(null, namedUnit, lengthUnitResource);
    	while(iter1.hasNext()) {
    		Statement stmt1 = iter1.next();
    		Resource unitResource = stmt1.getSubject();
    		
    		if(unitResource.hasProperty(unitPrefix, millimetresResource)) {
    			scale = 1000;
    		} else if(unitResource.hasProperty(unitPrefix, decimetresResource)) {
    			scale = 10;
    		} else {
    			scale = 1;
    		}
    		
    	}
    	
    	System.out.println("LENGTH UNIT SCALE: " + scale);
    }
    
    private void addWKTMultilinestringToModel(Model original , String multilinestring) {
        original.setNsPrefix("geo", ns_geo);        
    	Model m = original;
    	
        Property ifcBuildingProperty = model.createProperty( ns_ifcowl + "IfcBuilding" ); 
        StmtIterator iter = m.listStatements( null, RDF.type, ifcBuildingProperty );

        String wktLiteralID = "urn:geom:";
        Property unique_guid = m.createProperty( ns_ifcowl + "globalId_IfcRoot" );
        Property unique_guid_string = m.createProperty( ns_express + "hasString" );
        
        Resource buildingResource = null;

        while ( iter.hasNext() ) {
            Statement stmt = iter.nextStatement();
	        buildingResource = stmt.getSubject();
	        StmtIterator iter2 = m.listStatements(stmt.getSubject(), unique_guid , (RDFNode) null );       
	               
	        while ( iter2.hasNext() ) {
	        	Statement stmt2 = iter2.nextStatement(); 
	            StmtIterator iter3 = m.listStatements(stmt2.getResource(), unique_guid_string , (RDFNode) null );             
	            
	            while ( iter3.hasNext() ) {
	                wktLiteralID = wktLiteralID + iter3.nextStatement().getLiteral().toString();
	                //System.out.println("Entity WKT Literal ID: " + wktLiteralID);
	            }
	        }
        }
        
        Property geo_hasGeometry = m.createProperty( ns_geo + "hasGeometry" );
        Resource wktLiteralIDResource = m.createResource(wktLiteralID);        
        m.getResource(buildingResource.toString()).addProperty(geo_hasGeometry, wktLiteralIDResource);
        
        Property geo_asWKT = m.createProperty( ns_geo + "asWKT" );		
        Literal lit = m.createTypedLiteral(multilinestring);
        m.getResource(wktLiteralID).addProperty(geo_asWKT, lit);

        System.out.println("MULTILINESTRING successfully added to model");
        
        botModel = bot.addGeometryToBotModel(botModel, buildingResource, wktLiteralID, multilinestring, "Building");
    }

    private double returnTrueNorthRotationAngle(List trueNorthList) {
    	double angle = -90;
    	if(trueNorthList.size() == 0) {
        	System.out.println("No true north angle, defaulting to -90");
        } else {
	        NumberFormat nf = NumberFormat.getInstance();
	        nf.setMaximumFractionDigits(Integer.MAX_VALUE);
	        System.out.println("Value of True North: {" +nf.format(trueNorthList.get(0)) + ", " + trueNorthList.get(1) + "]");
	        
	        // Determine angle of rotation between the point (0.0, 1.0) and the (x, y) True North point
	        double difX = (double)trueNorthList.get(0) - 0.0; 
	    	double difY = (double)trueNorthList.get(1) - 1.0;
	    	angle = -(Math.toDegrees(Math.atan2(difX,-difY)));	// angle of rotation is saved to a global variable trueNorthRotationAngle
	    	System.out.println("True North angle of rotation: " + angle + " degrees");
        }
    	
    	return angle;
    }
    
    private double[] rotate_coordinate(double[] coord, double[] direction)
    {
        double[] coordinate = coord;
        System.out.println("\n**Starting rotation**");
        System.out.println("ROTATION COORDINATE: " + Arrays.toString(coordinate));
        System.out.println("USING DIRECTION: " + Arrays.toString(direction));
        if(direction[0] == -1)
        {
            
//            System.out.println("ROTATING 270 " + Arrays.toString(coordinate));
            //coordinate = rot.rotate(0, coordinate);
        	coordinate = rot.rotate(-270, coordinate);
        }
        else if(direction[0] == 1)
        {
//            System.out.println("ROTATING 90 " + Arrays.toString(coordinate));
            //coordinate = rot.rotate(-180, coordinate);
        	coordinate = rot.rotate(-90, coordinate);
            
        }
        else if(direction[1] == 1)
        {

//            System.out.println("ROTATING 0 " + Arrays.toString(coordinate));
            //coordinate = rot.rotate(-90, coordinate);
        	coordinate = rot.rotate(0, coordinate);
        }
        else if(direction[1] == -1)
        {

//            System.out.println("ROTATING 180 " + Arrays.toString(coordinate));
            //coordinate = rot.rotate(-270, coordinate);
        	coordinate = rot.rotate(-180, coordinate);
            
        }
        else 
        {
            System.out.println("NO ROTATION DIRECTION GIVEN, FALLING BACK TO DEFAULT");
//            System.out.println("ROTATING 270 " + Arrays.toString(coordinate));
            coordinate = rot.rotate(-90, coordinate);
//            System.out.println("AFTER ROTATION " + Arrays.toString(coordinate));
        }
        System.out.println("COORDINATE AFTER ROTATION: " + Arrays.toString(coordinate));
        System.out.println("**Finished rotation**\n");
        return coordinate;
    }
    
    
    //Author Kris McGlinn - This method changes the sign of the longitude or latitude values in a List
    private List longLatNegativeConvert(List l)
    {
        //
        String s = (String)l.get(l.size()-1);
        int x = Integer.parseInt(s);
        if(x<0)
        {
            
            for(int i = 0; i <l.size()-1; i++)
            {
                s = (String)l.get(i);
                l.set(i, s.substring(1));
                
            }

        }
        
        return l;
    }
    
    //Author Kris McGlinn - This method traverses the RDF express list and recursively adds values to a Java list
    private Statement traverseList(Model original, Statement stmt, boolean lat)
    {
        
        Model m = ModelFactory.createDefaultModel().add(original);
        Property listHasContents = m.createProperty( ns_list + "hasContents" );
        Property listHasNext = m.createProperty( ns_list + "hasNext" );
        boolean moreInList = false;
        String s[];

                  
        StmtIterator iter = m.listStatements( stmt.getObject().asResource(), null, (RDFNode) null );
        
        while ( iter.hasNext() ) 
        {
            Statement stmt1 = iter.nextStatement();

            if(stmt1.getPredicate().equals(listHasContents))
            {
                StmtIterator iter2 = m.listStatements( stmt1.getObject().asResource(), null, (RDFNode) null );
                while ( iter2.hasNext() ) 
                    {
                        Statement stmt2 = iter2.nextStatement();

                        if(stmt2.getObject().isLiteral())
                        {
                            if(lat)
                            {
//                                System.out.println("Lat value "+count+" is: " + stmt2.getObject());
                                s = stmt2.getObject().toString().split("\\^\\^http");                               
                                latitude.add(s[0]);
                                
                            }
                            else {
//                                System.out.println("Long value "+count+" is: " + stmt2.getObject());
                                s = stmt2.getObject().toString().split("\\^\\^http");
                                longitude.add(s[0]);

                            }
                        }

                        
                    }
            }
            else if(stmt1.getPredicate().equals(listHasNext))
            {
//                System.out.println("Adding 1 to count: " + (count+1));
                //count++;
                moreInList = true;
                traverseList(original, stmt1, lat);
//                System.out.println("Has next item in list");
            }

        }
        
        if(!moreInList)
        {
//            System.out.println("List is at end");
            stmt = null;
            return stmt;
        }
        
        return stmt;
    }
    
    //Author Kris McGlinn - This method returns a direction (which is defined by a list of coordianates in a placement list)
    private List returnDirection(Model original, Resource r){
        
        Model m = ModelFactory.createDefaultModel().add(original);
        List direction = new ArrayList();
        
        Property directionRatios_IfcDirection = m.createProperty( ns_ifcowl + "directionRatios_IfcDirection" );
        
        StmtIterator iter = m.listStatements( r, directionRatios_IfcDirection, (RDFNode) null );
        
        while ( iter.hasNext() ) {
            Statement stmt = iter.nextStatement();
            direction = traversePlacementList(m, stmt.getResource(), direction);
        }

        return direction;
    }
    
    //Author Kris McGlinn - This method returns True North (makes use of returnDirection)
    private List returnTrueNorth(Model original){
        
        Model m = ModelFactory.createDefaultModel().add(original);
        List trueNorth = new ArrayList();
                
        Property ref_trueNorth = m.createProperty( ns_ifcowl + "trueNorth_IfcGeometricRepresentationContext" );
 
        StmtIterator iter = m.listStatements( null, ref_trueNorth, (RDFNode) null );

        while ( iter.hasNext() ) {
            Statement stmt = iter.nextStatement();
            trueNorth = returnDirection(m, stmt.getResource());
        }

        return trueNorth;
    }
    
    //Author Kris McGlinn - This method returns the longitude and latitude by making use of traverseList()
    private Resource returnLongLat(Model original){
        
        Model m = ModelFactory.createDefaultModel().add(original);
        Resource r = null;
                
        Property refLatitude_IfcSite = m.createProperty( ns_ifcowl + "refLatitude_IfcSite" );
        Property refLongitude_IfcSite = m.createProperty( ns_ifcowl + "refLongitude_IfcSite" );
        
        StmtIterator iter = m.listStatements( null, RDF.type, ifcSiteProperty );

        while ( iter.hasNext() ) {
            Statement stmt = iter.nextStatement();
            //System.out.println( stmt);
            StmtIterator iter2 = m.listStatements( stmt.getSubject(), refLatitude_IfcSite, (RDFNode) null );
            r = stmt.getSubject();
            while ( iter2.hasNext() ) 
            {
                
                stmt = iter2.nextStatement();    
                //System.out.println( stmt);
                traverseList(m, stmt, true);
                
            }
        }

  
        iter = m.listStatements( null, RDF.type, ifcSiteProperty );

        while ( iter.hasNext() ) {
            Statement stmt = iter.nextStatement();

            StmtIterator iter2 = m.listStatements( stmt.getSubject(), refLongitude_IfcSite, (RDFNode) null );
            while ( iter2.hasNext() ) 
            {
                
                stmt = iter2.nextStatement();    

                traverseList(m, stmt, false);
                
            }
        }

        return r;
    }
    
    private IFC_Entity_Geometry returnCartesianLocation(List ifcEntity_localPlacement) {
    	IFC_Entity_Geometry ifc = new IFC_Entity_Geometry();

        double[] localDirection = new double[]{0.0, 0.0, 0.0};
        double[] localCoordinate = new double[]{0.0, 0.0, 0.0};
 
    	List entityPlacement = (List)ifcEntity_localPlacement.get(0);
    	localCoordinate[0] = (double)entityPlacement.get(0);
    	localCoordinate[1] = (double)entityPlacement.get(1);
        
    	
        List entityDirection = (List)ifcEntity_localPlacement.get(1);
        if(entityDirection.isEmpty()) {
    		System.out.println("No direction given for entity");
    	} else {
	    	localDirection[0] = (double)entityDirection.get(0);
	    	localDirection[1] = (double)entityDirection.get(1);
    	}   
        
        ifc.setLocation(localCoordinate);
        ifc.setDirection(localDirection);
        return ifc;
    }
    
    //Author Kris McGlinn - This method returns the longitude and latitude by making use of traverseList()
    private Model returnEntityGeometry(Model original, Resource r, IFC_Entity_Geometry ifc)
    { 
                
        Model m = original;//ModelFactory.createDefaultModel().add(original);              
//        StmtIterator iter = m.listStatements( null, RDF.type, r );
        
        int count = 0;
//        while ( iter.hasNext() ) 
//        {
//            Statement stmt = iter.nextStatement();
            List ifcEntity_representatinList; //A list of the representations of the entity  
            List ifcEntity_geometry_check = null; //A list of the geometry of a representation
            List ifcEntity_geometry = null; //A list of the geometry of a representation
            
            ifcEntity_representatinList = returnRepresentationsOfEntity(m, r);
//            System.out.println("LIST OF REPRESENTATION OF ENTITY: " + stmt.getSubject() + " is "+ ifcEntity_representatinList);
            int i = 0;
            while(i<ifcEntity_representatinList.size())
            {
                ifcEntity_geometry_check = returnGeometryOfRepresentation(m, (Resource)ifcEntity_representatinList.get(i));
                if(!ifcEntity_geometry_check.isEmpty())
                {
                    ifcEntity_geometry = ifcEntity_geometry_check;
                }
                i++;
            }
            if(ifcEntity_geometry.isEmpty())
                {
                    System.out.println("NO POLYLINE FOUND FOR ENTITY");
                }
            else System.out.println("PRINTING LIST OF POLYLINE POINTS: " + ifcEntity_geometry.toString());
//            double[] d = returnCartesianLocation(ifcEntity_geometry, false); 
            
            if(ifcEntity_geometry!=null&&ifcEntity_geometry.size()>0)
            {
                addWKTPolylineGeolocationToEntity(m, r, ifcEntity_geometry, ifc);
            }
            count++;
//            
//        }       
        
//        System.out.println("Number of resource found = " + count + " for resource: " + r.toString());
        return m;
    }
    
    
    
        //Author Kris McGlinn - This method returns the localplacement of an entity by making use of traverseList()
    private List returnRepresentationsOfEntity(Model original, Resource r)
    {
        
//        System.out.println("RETURING GEOMETRY FOR ENTITY: " + r);
        Model m = ModelFactory.createDefaultModel().add(original);   
        List geometry_List = new ArrayList();
        
        Property objectPlacement_IfcProduct = m.createProperty( ns_ifcowl + "representation_IfcProduct" );    
        Property representations_IfcProductRepresentation = m.createProperty( ns_ifcowl + "representations_IfcProductRepresentation" ); 
        
        StmtIterator iter = m.listStatements( r, objectPlacement_IfcProduct, (RDFNode) null );
        
        while ( iter.hasNext() ) 
        {
            Statement stmt = iter.nextStatement();
//            System.out.println("PRINTING STATEMENT " + stmt);
            StmtIterator iter2 = m.listStatements( stmt.getResource(), representations_IfcProductRepresentation, (RDFNode) null );
            while ( iter2.hasNext() ) 
            {
                Statement stmt1 = iter2.nextStatement();
//                System.out.println("PRINTING SUBJECT " + stmt2.getResource());
                geometry_List = returnEntityRepresentationList(m, stmt1.getResource(), geometry_List);

            }
//            geometry_List = returnEntityRepresentationList(m, stmt.getResource());
        }
        
        return geometry_List;
    }
    
    //Author Kris McGlinn - This method returns the localplacement of an entity by making use of traverseList()
    private List returnLocalPlacementOfEntity(Model original, Resource r)
    {
        
//        System.out.println("RETURING LOCAL PLACEMENT FOR ENTITY: " + r.toString());
        Model m = ModelFactory.createDefaultModel().add(original);   
        List coordinate_List = new ArrayList();
        
        Property objectPlacement_IfcProduct = m.createProperty( ns_ifcowl + "objectPlacement_IfcProduct" );       
        StmtIterator iter = m.listStatements( r, objectPlacement_IfcProduct, (RDFNode) null );
        
        while ( iter.hasNext() ) 
        {
            Statement stmt = iter.nextStatement();
            coordinate_List = returnLocalPlacement(m, stmt.getResource());
        }
        return coordinate_List;
    }
    
    //Author Kris McGlinn - This method returns the representations of an entity by making use of traverseList()
    private List returnEntityRepresentationList(Model original, Resource r, List l){
        
//        System.out.println("RESOURCE " + r);
        List representation_List = l;
        Model m = ModelFactory.createDefaultModel().add(original);
        Property listHasContents = m.createProperty( ns_list + "hasContents" );
        Property listHasNext = m.createProperty( ns_list + "hasNext" );
        boolean moreInList = false;
//        String s[];
      
        StmtIterator iter = m.listStatements( r, null, (RDFNode) null );
//        System.out.println("T: " + r);
        while ( iter.hasNext() ) 
        {
            Statement stmt1 = iter.nextStatement();
//            System.out.println("Test: " + stmt1);
            if(stmt1.getPredicate().equals(listHasContents))
            {
//                System.out.println("ADDING RESOURCE TO LIST " + stmt1.getObject());
                representation_List.add(stmt1.getObject());
//                            System.out.println("Here + "+s[0]);

            }
            else if(stmt1.getPredicate().equals(listHasNext))
            {
//                System.out.println("Adding 1 to count: " + (count+1));
//                System.out.println("THERE IS ANOTHER ITEM IN THE LIST: " + stmt1.getResource());
                moreInList = true;
                representation_List = returnEntityRepresentationList(m, stmt1.getResource(), representation_List);
                
            }

        }
//        
        if(!moreInList)
        {
//            System.out.println("List is at end");
            r = null;
            return representation_List;
        }
//        System.out.println(coordinate_List.toString());
        return representation_List;
    }

	private boolean checkGroundFloorPlacement(List relativePlacements) {
		List placement, placementDirection;
		
		for(int i = 0; i < relativePlacements.size(); i++) {
			//System.out.println("CHECK GND FLOOR PLACEMENT: " + relativePlacements.get(i));
			placementDirection = (List)relativePlacements.get(i);
			placement = (List)placementDirection.get(0);
			//System.out.println("Placement: " + placementDirection.get(0) + " | Direction: " + placementDirection.get(1));
			
			if((double)placement.get(0) != 0.0) {
				return false;
			}
		}
		return true;
	}
    
    //Author Kris McGlinn - This method returns the localplacement of an entity by making use of traverseList()
    private List returnLocalPlacement(Model original, Resource r){

//        System.out.println("RETURNING LOCAL PLACEMENT FOR RESOURCE: " + r.toString());
        Model m = ModelFactory.createDefaultModel().add(original);   
        List coordinate_List = new ArrayList();
        List direction_List = new ArrayList();
        List coordinate_direction_List = new ArrayList();
        
//        Property objectPlacement_IfcProduct = m.createProperty( ns_ifcowl + "objectPlacement_IfcProduct" );
        Property relativePlacement_IfcLocalPlacement = m.createProperty( ns_ifcowl + "relativePlacement_IfcLocalPlacement" );
        Property refDirection_IfcAxis2Placement3D = m.createProperty( ns_ifcowl + "refDirection_IfcAxis2Placement3D" );
        Property location_IfcPlacement = m.createProperty( ns_ifcowl + "location_IfcPlacement" );
        Property directionRatios_IfcDirection = m.createProperty( ns_ifcowl + "directionRatios_IfcDirection" );
        
        Property coordinates_IfcCartesianPoint = m.createProperty( ns_ifcowl + "coordinates_IfcCartesianPoint" );
        
        StmtIterator iter2 = m.listStatements( r, relativePlacement_IfcLocalPlacement, (RDFNode) null );   

        while ( iter2.hasNext() ) 
        { 
            Statement stmt2 = iter2.nextStatement();
            StmtIterator iter3 = m.listStatements( stmt2.getResource(), location_IfcPlacement, (RDFNode) null );   
//                System.out.println(stmt2.getResource());
            while ( iter3.hasNext() ) 
            { 
                Statement stmt3 = iter3.nextStatement();
                StmtIterator iter4 = m.listStatements( stmt3.getResource(), coordinates_IfcCartesianPoint, (RDFNode) null );   
//                    System.out.println(stmt3.getResource());
                while ( iter4.hasNext() ) 
                { 
                    Statement stmt4 = iter4.nextStatement();
//                        System.out.println(stmt4.getResource());
                    coordinate_List = traversePlacementList(m, stmt4.getResource(), coordinate_List);
                }
            }
        }
        coordinate_direction_List.add(coordinate_List);
        iter2 = m.listStatements( r, relativePlacement_IfcLocalPlacement, (RDFNode) null );   

        while ( iter2.hasNext() ) 
        { 
            Statement stmt2 = iter2.nextStatement();
            StmtIterator iter3 = m.listStatements( stmt2.getResource(), refDirection_IfcAxis2Placement3D, (RDFNode) null );   
//                System.out.println(stmt2.getResource());
            while ( iter3.hasNext() ) 
            { 
                Statement stmt3 = iter3.nextStatement();
                StmtIterator iter4 = m.listStatements( stmt3.getResource(), directionRatios_IfcDirection, (RDFNode) null );   
//                    System.out.println(stmt3.getResource());
                while ( iter4.hasNext() ) 
                { 
                    Statement stmt4 = iter4.nextStatement();
//                        System.out.println(stmt4.getResource());
                    direction_List = traversePlacementList(m, stmt4.getResource(), direction_List);
                }
            }
        }
        coordinate_direction_List.add(direction_List);
        
        return coordinate_direction_List;
    }
    
    //Author Kris McGlinn - This method recursively extracts the values of the list
    private List traverseRelativePlacements(Model original, Statement s, Property p, List l){
        

        Statement stmt = null;
        Model m = ModelFactory.createDefaultModel().add(original);  
        StmtIterator iter = m.listStatements( s.getResource(), p, (RDFNode) null );  
        List coordinates_List;  
                
//        System.out.println(s.getResource());
        while ( iter.hasNext() ) 
        {	
        
            stmt = iter.nextStatement();
            //Passing the same reference to list object
            traverseRelativePlacements(m, stmt, p, l);
            coordinates_List = new ArrayList(); 
            coordinates_List = returnLocalPlacement(m, stmt.getResource());
           System.out.println("LOCAL PLACEMENT OF RESOURCE: " + stmt.getResource() + " = " + coordinates_List.toString());
            l.add(coordinates_List);
//            System.out.println("PRINTING ALL LIST OF LISTS: " + l.toString());

        }
    
        return l;
    }
    
    //Author Kris McGlinn - This method returns the longitude and latitude by making use of traverseList()
    private List returnRelativePlacement(Model original, Resource r){
        
//        System.out.println("RETURNING RELATIVE PLACEMENT");
        List coordinates_List = new ArrayList();
        List relative_Coordinates_List = new ArrayList(); 
        Model m = ModelFactory.createDefaultModel().add(original);          
        Property objectPlacement_IfcProduct = m.createProperty( ns_ifcowl + "objectPlacement_IfcProduct" );
        Property placementRelTo_IfcLocalPlacement = m.createProperty( ns_ifcowl + "placementRelTo_IfcLocalPlacement" );
        
        StmtIterator iter = m.listStatements( r, objectPlacement_IfcProduct, (RDFNode) null );

        while ( iter.hasNext() ) 
        {
            Statement stmt = iter.nextStatement();
//            System.out.println(stmt.getResource());
            //We pass a reference here as coordinates list. So keep an eye on it ;)
            relative_Coordinates_List = traverseRelativePlacements(m, stmt, placementRelTo_IfcLocalPlacement, coordinates_List);
            System.out.println("PRINTING LIST OF ALL RELATIVE PLACEMENTS: " + relative_Coordinates_List.toString());
        }

        return relative_Coordinates_List;
    } 
    
       //Author Kris McGlinn - This method returns the longitude and latitude by making use of traverseList()
    private List returnGeometryOfRepresentation(Model original, Resource r){
        
//        System.out.println("RETURNING GEOMETRY OF REPRESENTATIONS FOR RESOURCE: " + r);
        List geometry_List = new ArrayList(); //This will contain a 2D list of polylines, each containing a list of coordinates (may only ever be one polyline)
        
        Model m = ModelFactory.createDefaultModel().add(original);          
        Property items_IfcRepresentation = m.createProperty( ns_ifcowl + "items_IfcRepresentation" );
        Property points_IfcPolyline = m.createProperty( ns_ifcowl + "points_IfcPolyline" ); //FOR POLYLINE GEOMETRY
        
        
        StmtIterator iter = m.listStatements( r, items_IfcRepresentation, (RDFNode) null );

        while ( iter.hasNext() ) 
        {
            Statement stmt = iter.nextStatement();
            StmtIterator iter2 = m.listStatements( stmt.getResource(), points_IfcPolyline, (RDFNode) null );
            
            while ( iter2.hasNext() ) 
            {
                Statement stmt2 = iter2.nextStatement();
                System.out.println(stmt2);
                //We pass a reference here as coordinates list. So keep an eye on it ;)
                geometry_List = traversePolylineCoordinateList(m, stmt2.getResource(), geometry_List);
                System.out.println("PRINTING FOUND COORDINATES FOR THE POLYLINE: " + geometry_List.toString());
            }
        }
        return geometry_List;
    } 

    //Author Kris McGlinn - This method returns a list of IfcCartesianPoints, each of which contains a list of coordinates.
    private List traversePolylineCoordinateList(Model original, Resource r, List coordinates_List)
    {
        
        List coordinate_List = new ArrayList(); //This holds a 2D/3D coordinate, derived from an IfcCartesianPoint_List in the traverseCoordinateList() method

        Model m = ModelFactory.createDefaultModel().add(original);
        Property listHasContents = m.createProperty( ns_list + "hasContents" );
        Property listHasNext = m.createProperty( ns_list + "hasNext" );
        boolean moreInList = false;
//        int count = 0;
        
        StmtIterator iter = m.listStatements( r, null, (RDFNode) null );
        
        while ( iter.hasNext() ) 
        {
            Statement stmt1 = iter.nextStatement();
            
            if(stmt1.getPredicate().equals(listHasContents))
            {
//                System.out.println(stmt1);
                Property coordinates_IfcCartesianPoint = m.createProperty( ns_ifcowl + "coordinates_IfcCartesianPoint" );
                StmtIterator iter1 = m.listStatements( stmt1.getResource(), coordinates_IfcCartesianPoint, (RDFNode) null );
        
                while ( iter1.hasNext() ) 
                {
                    Statement stmt2 = iter1.nextStatement();
                    coordinates_List.add(traverseCoordinateList( m, stmt2.getObject().asResource(), coordinate_List));
                }
            }
            else if(stmt1.getPredicate().equals(listHasNext))
            {
//                System.out.println("Adding 1 to count: " + (count+1));
//                count++;
                moreInList = true;
                traversePolylineCoordinateList(m, stmt1.getResource(), coordinates_List);
//                System.out.println("Has next item in list");
            }

        }
//        
        if(!moreInList)
        {
//            System.out.println("List is at end");
            r = null;
            return coordinates_List;
        }
        
//        System.out.println("NUMBER OF COORDINATES IN POLYLINE " + coordinates_List.toString());
        
        return coordinates_List;
    }
        //Author Kris McGlinn - This method traverses the RDF express list and recursively adds values to a Java list
    private List traverseCoordinateList(Model original, Resource r, List coordinate_List)
    {
//        List polyline_List = new ArrayList();
        Model m = ModelFactory.createDefaultModel().add(original);
        
        
        Property listHasContents = m.createProperty( ns_list + "hasContents" );
        Property listHasNext = m.createProperty( ns_list + "hasNext" );
        boolean moreInList = false;
        String s[];

        StmtIterator iter = m.listStatements( r, null, (RDFNode) null );

        while ( iter.hasNext() ) 
        { 
            Statement stmt2 = iter.nextStatement();

            if(stmt2.getPredicate().equals(listHasContents))
            {
                StmtIterator iter2 = m.listStatements( stmt2.getObject().asResource(), null, (RDFNode) null );

                while ( iter2.hasNext() ) 
                    {

                        Statement stmt3 = iter2.nextStatement();

                        if(stmt3.getObject().isLiteral())
                        {    
                            s = stmt3.getObject().toString().split("\\^\\^http");                               
                            coordinate_List.add(Double.parseDouble(s[0]));
//                            System.out.println("COORDINATE "+s[0]);
                        }


                    }
            }
            else if(stmt2.getPredicate().equals(listHasNext))
            {
//                System.out.println("Adding 1 to count: " + (count+1));
                //count++;
                moreInList = true;
                traverseCoordinateList(original, stmt2.getResource(), coordinate_List);
//                System.out.println("Has next item in list");
            }
        }


//        
        if(!moreInList)
        {
//            System.out.println("List is at end");
            r = null;
            return coordinate_List;
        }
//        System.out.println(coordinate_List.toString());
        return coordinate_List;
    }
    
    //Author Kris McGlinn - This method traverses the RDF express list and recursively adds values to a Java list
    private List traversePlacementList(Model original, Resource r, List coordinate_List)
    {
//        List coordinate_List = new ArrayList();
        Model m = ModelFactory.createDefaultModel().add(original);
        Property listHasContents = m.createProperty( ns_list + "hasContents" );
        Property listHasNext = m.createProperty( ns_list + "hasNext" );
        boolean moreInList = false;
        String s[];
      
        StmtIterator iter = m.listStatements( r, null, (RDFNode) null );
        
        while ( iter.hasNext() ) 
        {
            Statement stmt1 = iter.nextStatement();
            
            if(stmt1.getPredicate().equals(listHasContents))
            {
                StmtIterator iter2 = m.listStatements( stmt1.getObject().asResource(), null, (RDFNode) null );
                while ( iter2.hasNext() ) 
                    {
                        
                        Statement stmt2 = iter2.nextStatement();

                        
                        if(stmt2.getObject().isLiteral())
                        {    
                            s = stmt2.getObject().toString().split("\\^\\^http");                               
                            coordinate_List.add(Double.parseDouble(s[0]));
//                            System.out.println("Here + "+Double.parseDouble(s[0]));
                        }

                        
                    }
            }
            else if(stmt1.getPredicate().equals(listHasNext))
            {
//                System.out.println("Adding 1 to count: " + (count+1));
                //count++;
                moreInList = true;
                coordinate_List = traversePlacementList(original, stmt1.getResource(), coordinate_List);
//                System.out.println("Has next item in list");
            }

        }
//        
        if(!moreInList)
        {
//            System.out.println("List is at end");
            r = null;
            return coordinate_List;
        }
//        System.out.println(coordinate_List.toString());
        return coordinate_List;
    }
    


    
    //Original author Pieter Pauwels - This method writes the model to a turtle file (additional code K. McGlinn to output to an output directory
    private Model writeModel(Model m){
        try 
        {
            String s1 = outputfile;

            javaCreateDirectory();

            OutputStreamWriter char_output = new OutputStreamWriter(
                            new FileOutputStream("output\\"+s1), Charset.forName(
                                            "UTF-8").newEncoder());
            long size = m.size();
            BufferedWriter out = new BufferedWriter(char_output);
            m.write(out, "TTL");
            System.out.println("Successfully generated " + "TTL"
                            + " file at " + outputfile + " : triple count = " + size);
        } catch (IOException e) {
                System.out.println("Unable to generate " + "TTL"
                                + " file at " + outputfile);
                e.printStackTrace();
        } return m;
    }
        
    //Original author Pieter Pauwels - This method loads the turtle input file. 
    private Model loadFile(){
        Model m = null;
        try {
            m = FileManager.get().loadModel(inputfile, "TTL");
            //infmodel = ModelFactory.createRDFSModel(m);
            long size = m.size();
            System.out.println("Opened " + "TTL"
                            + " file at " + inputfile + " : triple count = " + size);
        } catch (org.apache.jena.riot.RiotException e) {
            System.out.println("Unable to parse " + "TTL" + " file at "
                            + inputfile);
            System.out.println("Unable to generate " + "TTL" + " file at "
                            + outputfile);
            System.out.println("RiotException " + e);
        }	
        return m;
    }
   

    //This method creates a directory
    public static void javaCreateDirectory()
    {

        File dir = new File("output");

        // attempt to create the directory here
        boolean successful = dir.mkdir();
        if (successful)
        {
          // creating the directory succeeded
          System.out.println("directory was created successfully");
        }
        else
        {
          // creating the directory failed
//              System.out.println("failed trying to create the directory");
        }

    }
    
      private void checkArguments(String[] a)
    {
        System.out.println("Checking arguments");
        if(a.length == 0)
        {
            String s[] = inputfile.split("\\.");
            outputfile = s[0] + "_geoloc."+s[1];        
        }
        else
        {           
            inputfile = a[0];  
        }
        if(a.length == 1)
        {
            inputfile = a[0]; 
            String s[] = inputfile.split("\\.");
            outputfile = s[0] + "_geoloc."+s[1];      
        }
        if(a.length == 2)
        {
            inputfile = a[0]; 
            outputfile = a[1]; ;
        }
        
        System.out.println("Inputfile: " + inputfile);
        System.out.println("Outputfile: " + outputfile);
        
    }
    
}

