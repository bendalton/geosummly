package it.unito.geosummly;

import it.unito.geosummly.io.CSVDataIO;
import it.unito.geosummly.io.GeoJSONReader;
import it.unito.geosummly.tools.CoordinatesNormalizationType;
import it.unito.geosummly.tools.InformationType;
import it.unito.geosummly.tools.TransformationTools;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;

import fi.foyt.foursquare.api.FoursquareApiException;


public class SamplingOperator {
    public static Logger logger = Logger.getLogger(SamplingOperator.class.toString());
    
    public SamplingOperator() {}
       
    public void executeWithInput(String in, String out, InformationType vtype, CoordinatesNormalizationType ltype, long sleep) throws IOException, JSONException, FoursquareApiException, InterruptedException {
    	
    	//Create the grid
    	GeoJSONReader gjd=new GeoJSONReader();
		ArrayList<BoundingBox> data=gjd.decode(in);
		double bigNorth=data.get(data.size()-1).getNorth();
		double bigEast=data.get(data.size()-1).getEast();
		double bigSouth=data.get(0).getSouth();
		double bigWest=data.get(0).getWest();
		BoundingBox global=new BoundingBox(bigNorth, bigEast, bigSouth, bigWest);
		Grid grid=new Grid();
		grid.setCellsNumber((int) Math.sqrt(data.size()));
		grid.setBbox(global);
		grid.setStructure(data);
		
		collectAndTransform(data, out, vtype, ltype, sleep);
    }
    
    public void executeWithCoord(ArrayList<Double> coord, String out, int gnum, int rnum, InformationType vtype, CoordinatesNormalizationType ltype, long sleep) throws IOException, FoursquareApiException, InterruptedException {
    	
    	//Create the grid
    	BoundingBox bbox=new BoundingBox(coord.get(0), coord.get(1), coord.get(2), coord.get(3));
    	ArrayList<BoundingBox> data=new ArrayList<BoundingBox>();
    	Grid grid=new Grid();
    	grid.setCellsNumber(gnum);
    	grid.setBbox(bbox);
    	grid.setStructure(data);
    	if(rnum>0)
    		grid.createRandomCells(rnum);
    	else
    		grid.createCells();
    	
    	collectAndTransform(data, out, vtype, ltype, sleep);
    }
    
    public void collectAndTransform(ArrayList<BoundingBox> data, String out, InformationType vtype, CoordinatesNormalizationType ltype, long sleep) throws UnknownHostException, FoursquareApiException, InterruptedException {
    	//Cache system
		/*MongoClient mongoClient=new MongoClient("localhost"); //MongoDB instance
		DB db=mongoClient.getDB("VenueDB");
		DBCollection coll=db.getCollection("ResultVenues");
		Gson gson=new Gson();
		BasicDBObject doc; //document which will contain the JSON results for MongoDB*/
    	
    	//Get the tools class and its support variables
		TransformationTools tools=new TransformationTools();
		ArrayList<ArrayList<Double>> venuesMatrix=new ArrayList<ArrayList<Double>>();
		ArrayList<ArrayList<Double>> venuesMatrixSecondLevel=new ArrayList<ArrayList<Double>>();
		ArrayList<Double> bboxArea=new ArrayList<Double>();
		FoursquareSearchVenues fsv=new FoursquareSearchVenues();
		ArrayList<FoursquareDataObject> cellVenue;
		CSVDataIO dataIO=new CSVDataIO();
		
		//Collect the geopoints
		for(BoundingBox b: data){
		    logger.log(Level.INFO, "Fetching 4square metadata of the cell: " + b.toString());
			cellVenue=fsv.searchVenues(b.getRow(), b.getColumn(), b.getNorth(), b.getEast(), b.getSouth(), b.getWest());
			//Copy to cache
			/*for(FoursquareDataObject fdo: cellVenue){
				String obj=gson.toJson(fdo); //Serialize with Gson
				doc=(BasicDBObject) JSON.parse(obj); //initialize the document with the JSON result parsed for MongoDB
				coll.insert(doc); //insert the document into MongoDB collection
			}*/
			
			venuesMatrix=tools.getInformations(vtype, b.getCenterLat(), b.getCenterLng(), venuesMatrix, cellVenue);
			bboxArea.add(b.getArea());
			Thread.sleep(sleep);
		}
		
		//Group single venues to cells if necessary
		switch (vtype) {
		case SINGLE:
			//two more columns if I've to build singles matrix
			venuesMatrix=tools.fixRowsLength(tools.getTotal()+2, venuesMatrix); //update rows length for consistency
			venuesMatrixSecondLevel=tools.fixRowsLength(tools.getTotalSecondLevel()+2, tools.getMatrixSecondLevel());
			venuesMatrix=tools.sortMatrixSingles(venuesMatrix, tools.getMap());
			venuesMatrixSecondLevel=tools.sortMatrixSingles(venuesMatrixSecondLevel, tools.getMapSecondLevel());
			dataIO.printResultSingles(tools.getSinglesTimestamps(), tools.getBeenHere(), tools.getSinglesId(), venuesMatrix, tools.getFeaturesForSingles(tools.sortFeatures(tools.getMap())), out+"/singles-matrix.csv");
			dataIO.printResultSingles(tools.getSinglesTimestamps(), tools.getBeenHere(), tools.getSinglesId(), venuesMatrixSecondLevel, tools.getFeaturesForSingles(tools.sortFeatures(tools.getMapSecondLevel())), out+"/singles-matrix-2nd.csv");
			ArrayList<ArrayList<Double>> auxMatrix=new ArrayList<ArrayList<Double>>();
			ArrayList<ArrayList<Double>> auxMatrixSecondLevel=new ArrayList<ArrayList<Double>>();
			for(BoundingBox b: data) {
				auxMatrix.add(tools.groupSinglesToCell(b, venuesMatrix));
				auxMatrixSecondLevel.add(tools.groupSinglesToCell(b, venuesMatrixSecondLevel));
			}
			
			venuesMatrix=new ArrayList<ArrayList<Double>>(auxMatrix);
			venuesMatrixSecondLevel=new ArrayList<ArrayList<Double>>(auxMatrixSecondLevel);
			break;
		case CELL:
			venuesMatrix=tools.fixRowsLength(tools.getTotal(), venuesMatrix); //update rows length for consistency
			venuesMatrixSecondLevel=tools.fixRowsLength(tools.getTotalSecondLevel(), tools.getMatrixSecondLevel());
			break;
		}
		
		//Build the transformation matrix
		TransformationMatrix tm=new TransformationMatrix();
		ArrayList<ArrayList<Double>> frequencyMatrix;
		if(vtype.equals(InformationType.SINGLE))
			frequencyMatrix=new ArrayList<ArrayList<Double>>(venuesMatrix);
		else
			frequencyMatrix=tools.sortMatrix(ltype, venuesMatrix, tools.getMap());
		tm.setFrequencyMatrix(frequencyMatrix);
		ArrayList<ArrayList<Double>> densityMatrix=tools.buildDensityMatrix(ltype, frequencyMatrix, bboxArea);
		tm.setDensityMatrix(densityMatrix);
		ArrayList<ArrayList<Double>> normalizedMatrix=tools.buildNormalizedMatrix(ltype, densityMatrix);
		tm.setNormalizedMatrix(normalizedMatrix);
		tm.setHeader(tools.sortFeatures(tools.getMap()));
		
		TransformationMatrix tmSecondLevel=new TransformationMatrix();
		ArrayList<ArrayList<Double>> frequencyMatrixSecondLevel=tools.sortMatrix(ltype, venuesMatrixSecondLevel, tools.getMapSecondLevel());
		tmSecondLevel.setFrequencyMatrix(frequencyMatrixSecondLevel);
		ArrayList<ArrayList<Double>> densityMatrixSecondLevel=tools.buildDensityMatrix(ltype, frequencyMatrixSecondLevel, bboxArea);
		tmSecondLevel.setDensityMatrix(densityMatrixSecondLevel);
		ArrayList<ArrayList<Double>> normalizedMatrixSecondLevel=tools.buildNormalizedMatrix(ltype, densityMatrixSecondLevel);
		tmSecondLevel.setNormalizedMatrix(normalizedMatrixSecondLevel);
		tmSecondLevel.setHeader(tools.sortFeatures(tools.getMapSecondLevel()));
		
		//Write down the transformation matrix to file
		dataIO.printResultHorizontal(tools.getCellsTimestamps(), tm.getFrequencyMatrix(), tools.getFeaturesLabel(ltype, "f", tm.getHeader()), out, "/frequency-transformation-matrix.csv");
		dataIO.printResultHorizontal(tools.getCellsTimestamps(), tm.getDensityMatrix(), tools.getFeaturesLabel(ltype, "density", tm.getHeader()), out, "/density-transformation-matrix.csv");
		dataIO.printResultHorizontal(tools.getCellsTimestamps(), tm.getNormalizedMatrix(), tools.getFeaturesLabel(ltype, "normalized_density", tm.getHeader()), out, "/normalized-transformation-matrix.csv");
		
		dataIO.printResultHorizontal(tools.getCellsTimestamps(), tmSecondLevel.getFrequencyMatrix(), tools.getFeaturesLabel(ltype, "f", tmSecondLevel.getHeader()), out, "/frequency-transformation-matrix-2nd.csv");
		dataIO.printResultHorizontal(tools.getCellsTimestamps(), tmSecondLevel.getDensityMatrix(), tools.getFeaturesLabel(ltype, "density", tmSecondLevel.getHeader()), out, "/density-transformation-matrix-2nd.csv");
		dataIO.printResultHorizontal(tools.getCellsTimestamps(), tmSecondLevel.getNormalizedMatrix(), tools.getFeaturesLabel(ltype, "normalized_density", tmSecondLevel.getHeader()), out, "/normalized-transformation-matrix-2nd.csv");
    }
}