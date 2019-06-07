package tcd;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;

public class BuildingTopologyOntology {
	
    private String botPrefix;
    private String geoPrefix;
    private String ifcowlPrefix;   
    
    public Model addGeometryToBotModel(Model botModel, Resource ifcResource, String wktLiteralID, String wktString, String entityType) {
    	
    	// Initialize prefixes
    	botModel.setNsPrefix("bot", botPrefix);
    	botModel.setNsPrefix("geo", geoPrefix);
    	
    	// Create bot sub-pred-obj for the current entity
    	// entityType can be Site, Building, Element
    	Resource botResource = botModel.createResource(botPrefix + entityType);
    	botModel.getResource(ifcResource.toString()).addProperty(RDF.type, botResource);
    	
    	// Add the hasGeometry Property to the resource
        Property geo_hasGeometry = botModel.createProperty( geoPrefix + "hasGeometry" );      	
    	Resource wktResource = botModel.createResource(wktLiteralID);
    	botModel.getResource(ifcResource.toString()).addProperty(geo_hasGeometry, wktResource);
    	
    	// Add the asWKT string to the geometry
    	Property geo_asWKT = botModel.createProperty(geoPrefix + "asWKT" );
    	RDFDatatype rtype = WktLiteral.wktLiteralType; 
        TypeMapper.getInstance().registerDatatype(rtype);     
        Literal l = botModel.createTypedLiteral(wktString, rtype);
        botModel.getResource(wktLiteralID).addProperty(geo_asWKT, l);
            	
    	return botModel;
    }
    
    public Model createBotHasRelationships(Model ifcOwlModel, Model botModel) {    	
    	
    	// Find the bot:Site
    	Property botSiteProperty = botModel.createProperty(botPrefix + "Site");
		StmtIterator iterSite = botModel.listStatements(null, RDF.type, botSiteProperty);
		Statement stmtSite = iterSite.nextStatement();
		Resource botSite = stmtSite.getSubject();
		System.out.println("BOT SITE: " + botSite);
		
		// Find the bot:Building
		Property botBuildingProperty = botModel.createProperty(botPrefix + "Building");
		StmtIterator iterBuilding = botModel.listStatements(null, RDF.type, botBuildingProperty);
		Statement stmtBuilding = iterBuilding.nextStatement();
		Resource botBuilding = stmtBuilding.getSubject();
		System.out.println("BOT BUILDING: " + botBuilding);
    			
		// Add bot:hasBuilding relationship to the bot:Site
		botModel = addHasRelationshipToBotModel(botModel, botSite, "hasBuilding", botBuilding);
	
		// Find the building storeys from the Ifc Model
    	Property ifcBuildingStoreyProperty = ifcOwlModel.createProperty(ifcowlPrefix + "IfcBuildingStorey");
		StmtIterator iter1 = ifcOwlModel.listStatements(null, RDF.type, ifcBuildingStoreyProperty);

		// Need to check if ifcowl version, the relationship definitions change between IFC4_ADD1 and IFC2x3_TC1
		Property relatingObjectProperty;
		Property relatedObjectsProperty;
		if(ifcowlPrefix.contains("IFC4_ADD1")) {
			// Relating a Storey to a Space (IFC4_ADD1)
			relatingObjectProperty = ifcOwlModel.createProperty( ifcowlPrefix + "relatingObject_IfcRelAggregates" );
			relatedObjectsProperty = ifcOwlModel.createProperty( ifcowlPrefix + "relatedObjects_IfcRelAggregates" );
		} else {
			// Relating a Storey to a Space (IFC2x3_TC1)
			relatingObjectProperty = ifcOwlModel.createProperty( ifcowlPrefix + "relatingObject_IfcRelDecomposes" );
			relatedObjectsProperty = ifcOwlModel.createProperty( ifcowlPrefix + "relatedObjects_IfcRelDecomposes" );
		}
		
		// Relating a Space to a wall
		Property relatingSpaceToBoundary = ifcOwlModel.createProperty(ifcowlPrefix + "relatingSpace_IfcRelSpaceBoundary");
		Property relatedBoundaryElement = ifcOwlModel.createProperty(ifcowlPrefix + "relatedBuildingElement_IfcRelSpaceBoundary");
		Property ifcWallProperty = botModel.createProperty(ifcowlPrefix + "IfcWallStandardCase");

		while (iter1.hasNext()) {
			Statement stmt = iter1.nextStatement();
			Resource buildingStorey = stmt.getSubject();
			System.out.println("STOREY: " + buildingStorey);
			
			// Add bot:hasStorey to the bot:Building
			Resource botStorey = botModel.createResource(botPrefix + "Storey");
			botModel.getResource(buildingStorey.toString()).addProperty(RDF.type, botStorey);
			botModel = addHasRelationshipToBotModel(botModel, botBuilding, "hasStorey", buildingStorey);
			
			// Find the object relationships (relates building storey to room spaces)
	        StmtIterator iter2 = ifcOwlModel.listStatements(null, relatingObjectProperty ,buildingStorey);       
	        while ( iter2.hasNext() ) {
	        	Statement stmt2 = iter2.nextStatement();
				
				// Find all spaces relating to the current building storey
				StmtIterator iter3 = ifcOwlModel.listStatements(stmt2.getSubject(), relatedObjectsProperty , (RDFNode)null);  
				
				while(iter3.hasNext()) {
					Statement stmt3 = iter3.nextStatement();
					Resource buildingSpace = (Resource)stmt3.getObject();
					System.out.println("\tSPACE: " + buildingSpace);

					// Create a bot:Space for each IfcSpace
					Resource botSpace = botModel.createResource(botPrefix + "Space");
					botModel.getResource(buildingSpace.toString()).addProperty(RDF.type, botSpace);
					
					// Add bot:hasSpace to the bot:Storey's
					botModel = addHasRelationshipToBotModel(botModel, buildingStorey, "hasSpace", buildingSpace);
					
					StmtIterator iter4 = ifcOwlModel.listStatements(null, relatingSpaceToBoundary, buildingSpace);
					while(iter4.hasNext()) {
						Statement stmt4 = iter4.next();
						
						// Get the elements that are related to the space
						StmtIterator iter5 = ifcOwlModel.listStatements(stmt4.getSubject(), relatedBoundaryElement, (RDFNode)null);
						
						while(iter5.hasNext()) {
							Statement stmt5 = iter5.next();
							Resource buildingElement = (Resource)stmt5.getObject();
							//System.out.println("\tBUILDING ELEMENT: " + buildingElement);
							
							if(buildingElement.hasProperty(RDF.type, ifcWallProperty)) {
								System.out.println("\t\tELEMENT: " + buildingElement);
								
								// Add bot:hasElement to the bot:Space
								botModel = addHasRelationshipToBotModel(botModel, buildingSpace, "hasElement", buildingElement);
							}									
						}	
					}
				}
	        }
		}
		
		return botModel;
	}
    
    public Model addHasRelationshipToBotModel(Model m, Resource ifcResource, String relationship, Resource relationshipResource) {
    	
    	//System.out.println("Added bot:" + relationship + " to " + ifcResource);
    	
    	Property bot_hasRelationship = m.createProperty(botPrefix + relationship );      	
    	m.getResource(ifcResource.toString()).addProperty(bot_hasRelationship, relationshipResource);
            	
    	return m;
    }
    
    
        
	// Original author Pieter Pauwels - This method writes the model to a turtle file (additional code K. McGlinn to output to an output directory)
	public Model writeModel(Model m, String filePrefix) {
		String outputFile = filePrefix + "_bot_geo.ttl";
		
		try {
			File dir = new File("output");

			OutputStreamWriter char_output = new OutputStreamWriter(new FileOutputStream("output\\" + outputFile),
					Charset.forName("UTF-8").newEncoder());
			long size = m.size();
			BufferedWriter out = new BufferedWriter(char_output);
			m.write(out, "TTL");
			System.out.println("Successfully generated " + "TTL" + " file at " + outputFile + " : triple count = " + size);
			
		} catch (IOException e) {
			System.out.println("Unable to generate " + "TTL" + " file at " + outputFile);
			e.printStackTrace();
		}
		return m;
	}


	public String getGeoPrefix() {
		return geoPrefix;
	}


	public void setGeoPrefix(String geoPrefix) {
		this.geoPrefix = geoPrefix;
	}


	public String getIfcowlPrefix() {
		return ifcowlPrefix;
	}


	public void setIfcowlPrefix(String ifcowlPrefix) {
		this.ifcowlPrefix = ifcowlPrefix;
	}

	public String getBotPrefix() {
		return botPrefix;
	}

	public void setBotPrefix(String botPrefix) {
		this.botPrefix = botPrefix;
	}

}
