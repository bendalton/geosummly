package it.unito.geosummly.evaluation;

import it.unito.geosummly.BoundingBox;
import it.unito.geosummly.CoordinatesNormalizationType;
import it.unito.geosummly.FoursquareDataObject;
import it.unito.geosummly.FoursquareSearchVenues;
import it.unito.geosummly.Grid;
import it.unito.geosummly.InformationType;
import it.unito.geosummly.TransformationMatrix;
import it.unito.geosummly.TransformationTools;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import fi.foyt.foursquare.api.FoursquareApiException;


public class ClusteringOutputValidation  {
    public static Logger logger = Logger.getLogger(ClusteringOutputValidation.class.toString());
    
	public static void main(String[] args) throws FoursquareApiException, UnknownHostException{
		
		/******************************CREATE THE BOUNDING BOX**********************************/
		double north=45.08200587145192; //north coordinate of the bounding box
		double south=45.05218065994234;
		double west=7.661247253417969;
		double east=7.70416259765625;
		int cells_number=20; //Number N of cells
		
		BoundingBox bbox=new BoundingBox(north, south, west, east); //Initialize the bounding box
		ArrayList<BoundingBox> data=new ArrayList<BoundingBox>(); //Data structure
		
		//Create a N*N grid based on the bounding box
		Grid grid=new Grid();
		grid.setCellsNumber(cells_number);
		grid.setBbox(bbox);
		grid.setStructure(data);
		grid.createCells();
		logger.log(Level.INFO, "GRID CREATED");
		
		/*******************************COLLECT ALL THE GEOPOINTS********************************/
		//Get the tools class and its support variables
		TransformationTools tools=new TransformationTools();
		ArrayList<ArrayList<Double>> supportMatrix=new ArrayList<ArrayList<Double>>();
		ArrayList<Double> bboxArea=new ArrayList<Double>();
		FoursquareSearchVenues fsv=new FoursquareSearchVenues();
		ArrayList<FoursquareDataObject> cellVenue;
		InformationType infoType=InformationType.SINGLE;
		
		//Download venues informations
		for(BoundingBox b: data){
			cellVenue=fsv.searchVenues(b.getRow(), b.getColumn(), b.getNorth(), b.getSouth(), b.getWest(), b.getEast());
			supportMatrix=tools.getInformationsWithFocalPts(infoType, b.getCenterLat(), b.getCenterLng(), supportMatrix, cellVenue);
			bboxArea.add(b.getArea());
		}
		supportMatrix=tools.fixRowsLength(tools.getTotal()+2, supportMatrix); //update rows length for consistency (+2 because of venue lat and lng)
		logger.log(Level.INFO, "GEOPOINTS COLLECTED");
		
		//write down the matrix to file
		printResult(supportMatrix, tools.getFeaturesForSinglesEvaluation(tools.sortFeatures(tools.getMap())), "output/evaluation/clustering output validation/singles-matrix.csv");
			
		/***********CREATE K MATRICES WITH N/K RANDOM VENUES FOR EACH MATRIX*************/
		int k=10; //fold number
		ArrayList<ArrayList<ArrayList<Double>>> allMatrices=new ArrayList<ArrayList<ArrayList<Double>>>();
		ArrayList<ArrayList<Double>> lastMatrix=new ArrayList<ArrayList<Double>>(supportMatrix);
		ArrayList<ArrayList<Double>> ithMatrix;
		int dimension=supportMatrix.size()/k;
		int randomValue;
		Random random = new Random();
		for(int i=0;i<k-1;i++) {
			ithMatrix=new ArrayList<ArrayList<Double>>();
			for(int j=0;j<dimension;j++) {
				randomValue=random.nextInt(lastMatrix.size()); //random number between 0 (included) and lastFold.size() (excluded)
				ithMatrix.add(lastMatrix.get(randomValue));
			}
			allMatrices.add(ithMatrix);
			lastMatrix.removeAll(ithMatrix);
		}
		allMatrices.add(lastMatrix);
		
		logger.log(Level.INFO, "ALL K MATRICES CREATED");
		
		/***********DIVIDE ALL THE MATRICES IN 400 CELLS AND GROUP THE VENUES*************/
		ArrayList<ArrayList<ArrayList<Double>>> allGrouped=new ArrayList<ArrayList<ArrayList<Double>>>();
		ArrayList<ArrayList<Double>> ithGrouped;
		
		for(ArrayList<ArrayList<Double>> matrix: allMatrices) {
			ithGrouped=new ArrayList<ArrayList<Double>>();
			for(BoundingBox b: data) {
				ithGrouped.add(tools.groupSinglesToCell(b, matrix));
			}
			allGrouped.add(ithGrouped);
		}
		logger.log(Level.INFO, "ALL K MATRICES GROUPED");
		
		/****************CREATE THE TRANSFORMATION MATRICES AND SERIALIZE THEM TO FILE******************/
		infoType=InformationType.CELL;
		
		TransformationMatrix ithTm;
		ArrayList<ArrayList<Double>> ithFrequency;
		ArrayList<ArrayList<Double>> ithDensity;
		ArrayList<ArrayList<Double>> ithNormalized;
		int index=0; //used for file name
		for(ArrayList<ArrayList<Double>> grouped: allGrouped) {
			ithTm=new TransformationMatrix();
			ithFrequency=tools.sortMatrix(grouped, tools.getMap());
			ithTm.setFrequencyMatrix(ithFrequency);
			if(infoType.equals(InformationType.CELL)) {
				ithDensity=tools.buildDensityMatrix(ithFrequency, bboxArea);
				ithTm.setDensityMatrix(ithDensity);
				ithNormalized=tools.buildNormalizedMatrix(CoordinatesNormalizationType.NORM, ithDensity);
				ithTm.setNormalizedMatrix(ithNormalized);
			}
			ithTm.setHeader(tools.sortFeatures(tools.getMap()));
			
			//write down the transformation matrices to file
			index++; //just for file name
			printResult(ithTm.getFrequencyMatrix(), tools.getFeaturesLabel("f", ithTm.getHeader()), "output/evaluation/clustering output validation/frequency-transformation-matrix-fold"+index+".csv");
			if(infoType.equals(InformationType.CELL)) {
				printResult(ithTm.getDensityMatrix(), tools.getFeaturesLabel("density", ithTm.getHeader()), "output/evaluation/clustering output validation/density-transformation-matrix-fold"+index+".csv");
				printResult(ithTm.getNormalizedMatrix(), tools.getFeaturesLabel("normalized_density", ithTm.getHeader()), "output/evaluation/clustering output validation/normalized-transformation-matrix-fold"+index+".csv");
			}
		}
		logger.log(Level.INFO, "TRANSFORMATION MATRICES PRINTED");
	}
	
	public static void printResult(ArrayList<ArrayList<Double>> matrix, ArrayList<String> features, String output) {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		OutputStreamWriter osw = new OutputStreamWriter(bout);
        try {
            CSVPrinter csv = new CSVPrinter(osw, CSVFormat.DEFAULT);
            
            //print the header of the matrix
            for(String f: features) {
            	csv.print(f);
            }
            csv.println();
            
            //iterate per each row of the matrix
            for(ArrayList<Double> a: matrix) {
            	for(Double d: a) {
            		csv.print(d);
            	}
            	csv.println();
            }
            csv.flush();
            csv.close();
        } catch (IOException e1) {
    		e1.printStackTrace();
        }
        OutputStream outputStream;
        try {
            outputStream = new FileOutputStream (output);
            bout.writeTo(outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
}
