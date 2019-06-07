Documentation for ifcowl to WKT, BOT and OBJ

Each time this project is executed it will create 3 new output files in the following folders (* = inputfilename):
    output - a new file called *_geoloc.ttl, it is the updated ifcOWL file with WKT geometries
    bot_output - a new file called *_bot_geo.ttl, it is the new BOT file with WKT geometries
    obj_output - a new file called *_obj.obj, it is the 3D OBJ wall structure model of the input ifcOWL building


-------------------- 1. ifcOWL to WKT Geometries --------------------
1.1)
ifcOWL building file is selected before running the program
This is selected as the "inputfile" variable at the top of IFC_Geolocation class

1.2)
ifcOWL building loaded into the program
The ifcOWL version is determined and set for all subsequent interactions with the file

1.3)
True north vector of the ifcOWL building is extracted from returnTrueNorth()
This is converted to an angle in returnTrueNorthAngleRotation()

1.4)
Extract geolocation of the site with returnLongLat()
Represent it as WKT POINT() and add it to the ifcOWL model with addWKTGeolocationToModel()

1.5)
Determine the length units used to represent the building in setLengthUnitScaleValue()
The "scale" variable is used as the value to scale items down  
scale = 1000 for millimetres
scale = 1 for metres

1.6)
Determine the height of all storeys in the building
Create a HashMap of the height->IfcBuildingStorey in findStoreys()

1.7)
addAllEntities() kicks off the process to extract all the IfcWallStandardCase walls contained in the ifcOWL model
returnEntityLocalPlacement() loops through all walls, extracting their coordinates...
    
1.8)
Inside returnEntityLocalPlacement():
    Local placement of a wall is calculated from returnLocalPlacementOfEntity()
    Relative placement of a wall is calculated from returnRelativePlacement()
    The storeyPlacement list stores the relative placement of a wall relative to its building storey
    Check if the wall is an external wall, this is done in checkExternalWall()
    Some buildings do not have ifcowl:EXTERNAL properties defined, in this case all building walls are processed
    The Location and Direction of a wall are determined in returnCartesianLocation(), these are set as attributes of an IFC_Entity_Geometry
    returnEntityGeometry() uses the wall Location, Direction, local placement and relative placement to form the wall WKT

1.9)
Inside returnEntityGeometry():
    The xy polyline points of the wall are extracted as two points, the start and end of the wall
    The polyline, Location, Direction and placements are passed to addWKTPolylineGeolocationToEntity()
    
1.10)
Inside addWKTPolylineGeolocationToEntity():
    2 XYZ coords are created for a wall
        The wall's start point: polylineCoordinateStart
        The wall's end point: polylineCoordinateEnd 
    The wall coords are rotated to the wall Direction with rotate_coordinate()
    The wall coords are tranlasted to the wall Location by adding the Location coord to the rotated wall coords
    The wall coords are scaled down to the extracted length unit value determined in (1.5) above
    The wall coords are rotated to the true north rotation angle as determined in (1.3) above
    The 2 transformed wall coords (still in XYZ) are added to the XYZ geolocation coord, positioning them relative to the geolocation of the building
    The resulting coords are converted back to lat/long (using EPSG mapping projections)
    A WKT LINESTRING() is created in the form: LINESTRING(wall_LONG wall_LAT)
    The lat/long wall coords are also saved into the overall building geometry_wkt_store variable
    The geometry_wkt_store variable forms the overall building WKT MULTILINESTRING() geometry
    addToStoreyMultiString() adds the wall lat/long coords to the MULTILINESTRING() of the wall's building storey
    The wall WKT LINESTRING() is added to the ifcOWL model as a GeoSPARQL hasGeometry property of the IfcWallStandardCase resource URI

1.11)
Once the while loop in returnEntityLocalPlacement() has finished processing all walls...
The IfcBuildingStorey WKT MULTILINESTRING() geometries that are stored in storeyMultistringMap are printed 
The IfcBuilding WKT MULTILINESTRING() is printed
In addWKTMultilinestringToModel(), the WKT MULTILINESTRING() of the IfcBuilding is stored as a GeoSPARQL hasGeometry property of the IfcBuilding resource URI

1.12)
writeModel() writes the processed ifcOWL model to the output *_geoloc.tll file in the output folder

-----------------------------------------------------------------

--------------- 2. ifcOWL to BOT with WKT Geometries ---------------
2.1)
The namespace prefixes to be used in the BOT model are first defined
These are the same as used in the ifcOWL model

2.2)
A BOT model is created as an empty default Jena Model

2.3)
Inside addWKTGeolocationToModel():
    bot.addGeometryToBotModel() is called
    A BOT:Site resource is created from the ifcOWL IfcSite resource URI
    The BOT:Site is given a GeoSPARQL hasGeometry property with the WKT POINT() geolocation of the IfcSite

2.4)
Inside each iteration of addWKTPolylineGeolocationToEntity():
    bot.addGeometryToBotModel() is called to create a BOT:Element
    A BOT:Element resource is created from the IfcWallStandardCase resource URI
    The BOT:Element is given a GeoSPARQL hasGeometry property with the WKT LINESTRING() of the IfcWallStandardCase

2.5)
Once the WKT MULTILINESTRING() of the IfcBuilding has been created, it is added to the ifcOWL model using addWKTMultilinestringToModel() as stated in 1.11 above
But addWKTMultilinestringToModel() also creates a BOT:Building:
    bot.addGeometryToBotModel() is called to create a BOT:Building
    A BOT:Building resource is created from the IfcBuiding resource URI
    The BOT:Building is given a GeoSPARQL hasGeometry property with the WKT MULTILINESTRING() of the IfcBuilding

2.6)
Towards the end of main(), the bot.createBotHasRelationships() method is called
This defines how all BOT building components (BOT:Site, BOT:Building, BOT:Storey, BOT:Space, BOT:Element) are related
The following relationship hierarchy is created:
    BOT:Site
        BOT:Building
            BOT:Storey
                BOT:Space
                    BOT:Element
Inside bot.createBotHasRelationships():
    The BOT:Site is retrieved from the BOT model
    The BOT:Building is retrieved from the BOT model
    A BOT:hasBuilding relationship is created to relate the BOT:Site to the BOT:Building
    The ifcOWL version is checked since a different ifcOWL relationship property is used in IFC4_ADD1 to IFC2x2_TC1
    Next step is to create the BOT:Storeys
    A BOT:Storey is created for each IfcBuildingStorey present in the ifcOWL model
    A BOT:hasStorey relationship is defined between the BOT:Building and each BOT:Storey
    Next is the BOT:Spaces
    Each space is a room enclosed by a number of walls
    A BOT:Space is created for each IfcSpace present in the ifcOWL model
    A BOT:hasSpace relationship is defined between each BOT:Storey and each BOT:Space contained within that storey
    Next is the BOT:Elements
    The BOT:Elements have already been created in (2.4), created from each instance of an IfcWallStandardCase
    A BOT:hasElement relationship is defined between each BOT:Space and each BOT:Element that encloses that space
    All of these relationships are added to the overall bot model

2.7)
bot.writeModel() writes the bot model to the output *_bot_geo.tll file in the bot_output folder

-----------------------------------------------------------------

--------------- 3. ifcOWL to 3D OBJ Wall Structure Model ---------------

3.1)
The namespace prefixes to be used in creating the OBJ model are first defined
These are the same as used in the ifcOWL model

3.2)
The OBJ vertices use the xy coords of each processed wall as defined from addWKTPolylineGeolocationToEntity()
Inside addWKTPolylineGeolocationToEntity():
    2 objWallCoord vertices are created for each wall from the transformed polylineCoordinateStart and polylineCoordinateEnd coords 
    The height of each wall is determined from getWallHeight()
    The height of the building storey level that the wall sits on is determined from the wall's relative placement
    A combination of the wall height and storey height are used to determine the z-axis placement of the wall in OBJ
    obj.addToPointList() creates the 4 OBJ vertices for each wall
    
3.3) 
Each wall is defined as a plane of 4 OBJ vertices with zero thickness
Inside obj.addToPointList():
    4 OBJ vertices are created for each wall: top left corner, top right corner, bottom left corner and bottom right corner
    Bottom left and right vertices have height of building storey level
    Top left and right vertices have height of building storey level + wall height
    Vertices are ordered in 1, 2, 4, 3 formation, this ordering is needed to create the wall plane
    
3.4)
The obj.writeOBJ() method is called at the end of main()
This uses all saved OBJ vertices for all walls as generated in 3.3 above
Each set of 4 OBJ vertices has one corresponding face, defining the plane of the wall
All vertices are written to the output .obj file seqentially
The wall faces (planes) are then written to the output .obj file
A face is created for each set of 4 wall vertices

3.5)
The resulting OBJ file is written to the output *_obj.obj file in the obj_output folder

-----------------------------------------------------------------