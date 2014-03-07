package it.unito.geosummly;

import it.unito.geosummly.clustering.subspace.GEOSUBCLU;
import it.unito.geosummly.clustering.subspace.InMemoryDatabase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;

import de.lmu.ifi.dbs.elki.algorithm.clustering.subspace.SUBCLU;
import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.model.SubspaceModel;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.datasource.ArrayAdapterDatabaseConnection;
import de.lmu.ifi.dbs.elki.datasource.filter.FixedDBIDsFilter;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.utilities.ClassGenericsUtil;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;

public class ClusteringOperator {

	public static Logger logger = Logger.getLogger(ClusteringOperator.class.toString());

	private Double SUBCLU_esp = 0.01;
    private int SUBCLU_minpts = 20;

	public void execute(String inDens, String inNorm, String inDeltad, String inSingles, String out, String method) throws IOException {
		
		//Read all the csv files
		CSVDataIO dataIO=new CSVDataIO();
		List<CSVRecord> listDens=dataIO.readCSVFile(inDens);
		List<CSVRecord> listNorm=dataIO.readCSVFile(inNorm);
		List<CSVRecord> listDeltad=dataIO.readCSVFile(inDeltad);
		List<CSVRecord> listSingles=dataIO.readCSVFile(inSingles);
	
		//fill in the matrix of normalized values
		ArrayList<ArrayList<Double>> normMatrix=new ArrayList<ArrayList<Double>>();
		for(CSVRecord r: listNorm) {
			//we exclude the header
			if(!r.get(0).contains("Timestamp")) {
				ArrayList<Double> record=new ArrayList<Double>();
				//we don't have to consider timepstamp values, so i=1
				for(int i=1;i<r.size();i++)
					record.add(Double.parseDouble(r.get(i)));
				normMatrix.add(record);
			}
		}
		
		//build the database from the normalized matrix
		Database db=buildFromMatrix(normMatrix);
		
		//fill in the feature hashmap only with single features and only if the corresponding value is greater than 0
		HashMap<Integer, String> featuresMap=new HashMap<Integer, String>();
		for(CSVRecord r: listDeltad) {
			String feature=(String) r.get(0).replace("deltad", "").replaceAll("\\(", "").replaceAll("\\)", ""); //take only feature name
			if(!feature.contains("AND") && Math.floor(Double.parseDouble(r.get(1)))>0) {
				int mSize=featuresMap.size();
				featuresMap.put(mSize+2, feature);
			}
		}
		
		//fill in the deltad hashmap with that values which are greater than 0 and whose feature is in the features hashmap
	    HashMap<String, Double> deltadMap=new HashMap<String, Double>();
	    ArrayList<String> toExclude=new ArrayList<String>();
	    boolean excluded=false;
	    boolean isFound=false;
		for(CSVRecord r: listDeltad) {
			String feature=(String) r.get(0).replace("deltad", "").replaceAll("\\(", "").replaceAll("\\)", ""); //take only feature name
			excluded=false;
			isFound=false;
			for(int i=0;i<toExclude.size() && !isFound;i++) {
				if(feature.contains(toExclude.get(i))) {
					toExclude.add(feature);
					excluded=true;
					isFound=true;
				}
			}
			double d=Math.floor(Double.parseDouble(r.get(1))); //floor of deltad value
			if(d > 0 && !excluded) {
				deltadMap.put(feature, d);
			}
			else if(!excluded)
				toExclude.add(feature);
		}
	    
		//Run GEOSUBCLU algorithm and get the clustering result
	    Clustering<?> result = runGEOSUBCLU(db, featuresMap, deltadMap);
	    ArrayList<Clustering<?>> cs = ResultUtil.filterResults(result, Clustering.class);
	    HashMap<Integer, String> clustersName=new HashMap<Integer, String>(); //key, cluster name
	    HashMap<Integer, ArrayList<Integer>> cellsOfCluster=new HashMap<Integer, ArrayList<Integer>>(); //key, cell_ids 
	    HashMap<Integer, ArrayList<String>> venuesOfCell=new HashMap<Integer, ArrayList<String>>(); //cell_id, venue_ids
	    
	    for(Clustering<?> c: cs) {
	    	//get all the clusters
	    	for(Cluster<?> cluster: c.getAllClusters()) {
	    		int index=clustersName.size();
	    		//put the cluster name in the map
	    		clustersName.put(index, cluster.getName());
	    		ArrayList<Integer> cells=new ArrayList<Integer>();
	    		//get all the cell_ids for the selected cluster
	    		for(DBIDIter iter=cluster.getIDs().iter(); iter.valid(); iter.advance()) {
	    			int cellId=Integer.parseInt(DBIDUtil.toString(iter));
	    			cells.add(cellId);
	    			ArrayList<String> venueIdRec=new ArrayList<String>();
	    			boolean found=false;
	    			boolean added=false;
	    			//get all the single venues for the selected cell
	    			for(int i=0;i<listSingles.size() && !found;i++) {
	    				CSVRecord r=listSingles.get(i); //venue information
	    				//we don't have to consider the header
	    				if(!r.get(0).contains("Timestamp")) {
	    					String lat=listDens.get(cellId).get(1); //focal latitude
	    					String lng=listDens.get(cellId).get(2); //focal longitude
	    					//check if the venue belong to the cell
	    					if(r.get(5).equals(lat) && r.get(6).equals(lng)) {
	    						venueIdRec.add(r.get(2)); //add the id_venue
	    						added=true;
	    					} else if(added) found=true; //since venues of the same cell are consecutive, we stop the loop once we found different coordinate values
	    				}
	    			}
	    			//add venue_id only if the venue exists in the cell
	    			if(added)
	    				venuesOfCell.put(cellId, venueIdRec);
	    		}
	    		cellsOfCluster.put(index, cells);
	    	}
	    }
	    
	    StringBuffer sb = new StringBuffer();
	    for(int i=0;i<cellsOfCluster.size(); i++) {
	    	ArrayList<Integer> i_rec=cellsOfCluster.get(i);
	    	sb.append(clustersName.get(i)+" ");
	    	for(Integer integer: i_rec) {
	    		sb.append(integer+" ");
	    	}
	    	sb.append("\n");
	    }
	    
	    Set<Integer> keys=venuesOfCell.keySet();
	    for(Integer i: keys) {
	    	sb.append("\nVenues for cell "+i+": \n");
	    	for(String s: venuesOfCell.get(i))
	    		sb.append(s+" ");
	    	sb.append("\n");
	    }
	
	    FileUtils.writeStringToFile(new File("output.txt"), sb.toString());   
	}
    
    /**Set SUBCLU parameters and run the algorithm*/
    public Clustering<?> runSUBCLU (Database db) {
        ListParameterization params = new ListParameterization();
        params.addParameter(SUBCLU.EPSILON_ID, SUBCLU_esp);
        params.addParameter(SUBCLU.MINPTS_ID, SUBCLU_minpts);
        
        // setup algorithm
        SUBCLU<DoubleVector> subclu = ClassGenericsUtil.parameterizeOrAbort(SUBCLU.class, params);

        // run SUBCLU on database
        Clustering<SubspaceModel<DoubleVector>> result = subclu.run(db);
        return result;
    }
    
    /**Set GEOSUBCLU parameters and run the algorithm*/
    public Clustering<?> runGEOSUBCLU (Database db, HashMap<Integer, String> map, HashMap<String, Double>deltad) {
        ListParameterization params = new ListParameterization();
        
        // setup algorithm
        GEOSUBCLU<DoubleVector> geosubclu = ClassGenericsUtil.parameterizeOrAbort(GEOSUBCLU.class, params);
        geosubclu.setFeatureMapper(map);
        geosubclu.setDeltad(deltad);

        // run GEOSUBCLU on database
        Clustering<SubspaceModel<DoubleVector>> result = geosubclu.run(db);
        return result;
    }
    
    /**Build a Database from the matrix of normalized density values*/
    private <T> Database buildFromMatrix (ArrayList<ArrayList<Double>> matrix) {       
        double[][] data = new double[matrix.size()][];
        for (int i=0; i<matrix.size(); i++) {
            data[i] = new double[matrix.get(i).size()];
            for(int j=0; j<matrix.get(i).size(); j++) {
                data[i][j] = (matrix.get(i)).get(j);
            }
        }
        
        List<Class<?>> filterlist = new ArrayList<>();
        filterlist.add(FixedDBIDsFilter.class);
        Database db = new InMemoryDatabase(new ArrayAdapterDatabaseConnection(data), null);        
        db.initialize();
        return db;
    }
}