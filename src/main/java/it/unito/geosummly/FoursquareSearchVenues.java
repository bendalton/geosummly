package it.unito.geosummly;

import it.unito.geosummly.utils.PropFactory;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import fi.foyt.foursquare.api.FoursquareApi;
import fi.foyt.foursquare.api.FoursquareApiException;
import fi.foyt.foursquare.api.Result;
import fi.foyt.foursquare.api.entities.CompactVenue;
import fi.foyt.foursquare.api.entities.VenuesSearchResult;


/**
 * @author Giacomo Falcone
 *
 * Download venue informations from 4square 
 */

public class FoursquareSearchVenues {
	private FoursquareApi foursquareApi;
	
	public static Logger logger = Logger.getLogger(FoursquareSearchVenues.class.toString());
	
	public FoursquareSearchVenues() {
		//Initialize FoursquareApi
		foursquareApi = new FoursquareApi(
		        PropFactory.config.getProperty("it.unito.geosummly.foursquare.clientID"), 
		        PropFactory.config.getProperty("it.unito.geosummly.foursquare.clientSecret"), 
		        "http://www.foursquare.com");
	}
	
	/**Search venues informations. Row and column informations are included*/
	public ArrayList<FoursquareDataObject> searchVenues(int row, int column, double north, double south, double west, double east) throws FoursquareApiException, UnknownHostException {
		String ne=north+","+east;
		String sw=south+","+west;
		Map<String, String> searchParams = new HashMap<String, String>(); 
		searchParams.put("intent", "browse");
		searchParams.put("ne", ne); 
		searchParams.put("sw", sw);
		ArrayList<FoursquareDataObject> doclist=new ArrayList<FoursquareDataObject>(); 
	    
	    //After client has been initialized we can make queries.
	    Result<VenuesSearchResult> result = foursquareApi.venuesSearch(searchParams);
	    if(result.getMeta().getCode() == 200) {  	
    		FoursquareDataObject dataobj;
    		long timestamp=System.currentTimeMillis();
	    	for(CompactVenue venue : result.getResult().getVenues()) {
	    		dataobj=new FoursquareDataObject(row, column, venue.getId(), venue.getName(), venue.getLocation().getLat(),
	    				venue.getLocation().getLng(), venue.getCategories(), venue.getContact().getEmail(),
	    				venue.getContact().getPhone(), venue.getContact().getFacebook(), venue.getContact().getTwitter(), 
	    				venue.getVerified(), venue.getStats().getCheckinsCount(), venue.getStats().getUsersCount(), 
	    				venue.getUrl(), venue.getHereNow().getCount(), timestamp);
	    		doclist.add(dataobj);
	    	}
	    	return doclist;
    	} 
    	else {
    			logger.log(Level.INFO, "Error occurred:\ncode: "+result.getMeta().getCode()+"\ntype: "+result.getMeta().getErrorType()+"\ndetail: "+result.getMeta().getErrorDetail());
    			return doclist;
	    }
	}
	
	/**Search venues informations. Row and column informations are not included*/
	public ArrayList<FoursquareDataObject> searchVenues(double north, double south, double west, double east) throws FoursquareApiException, UnknownHostException {
		String ne=north+","+east;
		String sw=south+","+west;
		Map<String, String> searchParams = new HashMap<String, String>(); 
		searchParams.put("intent", "browse");
		searchParams.put("ne", ne); 
		searchParams.put("sw", sw);
		ArrayList<FoursquareDataObject> doclist=new ArrayList<FoursquareDataObject>(); 
	    
	    //After client has been initialized we can make queries.
	    Result<VenuesSearchResult> result = foursquareApi.venuesSearch(searchParams);
	    if(result.getMeta().getCode() == 200) {  	
    		FoursquareDataObject dataobj;
    		long timestamp=System.currentTimeMillis();
	    	for(CompactVenue venue : result.getResult().getVenues()) {
	    		dataobj=new FoursquareDataObject(venue.getId(), venue.getName(), venue.getLocation().getLat(),
	    				venue.getLocation().getLng(), venue.getCategories(), venue.getContact().getEmail(),
	    				venue.getContact().getPhone(), venue.getContact().getFacebook(), venue.getContact().getTwitter(), 
	    				venue.getVerified(), venue.getStats().getCheckinsCount(), venue.getStats().getUsersCount(), 
	    				venue.getUrl(), venue.getHereNow().getCount(), timestamp);
	    		doclist.add(dataobj);
	    	}
	    	return doclist;
    	} 
    	else {
    			logger.log(Level.INFO, "Error occurred:\ncode: "+result.getMeta().getCode()+"\ntype: "+result.getMeta().getErrorType()+"\ndetail: "+result.getMeta().getErrorDetail());
    			return doclist;
	    }
	}
 }