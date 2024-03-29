package com.amazonaws.samples.chimevoiceconnector;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.CDL;
import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.pricing.AWSPricing;
import com.amazonaws.services.pricing.AWSPricingClientBuilder;
import com.amazonaws.services.pricing.model.Filter;
import com.amazonaws.services.pricing.model.GetProductsRequest;
import com.amazonaws.services.pricing.model.GetProductsResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;

public class LambdaFunctionHandler implements RequestHandler<S3Event, String> {

    private AmazonS3 s3;
    private AWSPricing pricingClient = AWSPricingClientBuilder.defaultClient();
    
    private final String TARGET_BUCKET_NAME = System.getenv("TARGET_BUCKET_NAME");
    private final String OUTPUT_FORMAT=System.getenv("OUTPUT_FORMAT");
    private final String CHIME_VOICE_CONNECTOR_SERVICE_CODE = "AmazonChimeVoiceConnector";
    private static final String DEFAULT_FORMAT_VERISION ="aws_v1";
    private static final String CDR_USAGE_TYPE = "UsageType";
    private static final String TERM_MATCH_FILTER="TERM_MATCH";
    
    private Context lambdaContext;
    
    public LambdaFunctionHandler() 
    {
    	s3 = AmazonS3ClientBuilder.standard().build();
    }

    // Test purpose only.
    LambdaFunctionHandler(AmazonS3 s3) {
        this.s3 = s3;
       
    }

    @Override
    public String handleRequest(S3Event event, Context context) {
        context.getLogger().log("Received event: " + event.toJson());
     
        this.lambdaContext = context;
       
     
        // Get the object from the event 
        String bucket = event.getRecords().get(0).getS3().getBucket().getName();
        String key = event.getRecords().get(0).getS3().getObject().getKey();
        
        //only process Amazon Chime Voice Connector call records
        if (key.contains("Amazon-Chime-Voice-Connector-CDRs"))
        {
	        try {
	        	
	            S3Object s3Object = s3.getObject(new GetObjectRequest(bucket, key));
	            String contentType = s3Object.getObjectMetadata().getContentType();
	            lambdaContext.getLogger().log("Retrieved notification for object with key " + key + " CONTENT TYPE: " + contentType);
	           
	            List<JSONObject> cdrRecords = parseCDRRecord(s3Object);
	            
	            if (cdrRecords != null)
	            {
	            	for(JSONObject crdRecord : cdrRecords)
	            	{
	            		//The usage type contains source and destination country, as well as the call type and unit
	            		//i.e. USE1-US-PA-outbound-minutes
	            		String usage_type = crdRecord.getString(CDR_USAGE_TYPE);
	            		
	            		if (usage_type != null)
	            		{
		            		//get the cost
		            		JSONObject enrichedCDR = addCostToCDR( crdRecord, usage_type);
		            		
		            		//check to see if desired output is in CSV
		            		if (OUTPUT_FORMAT != null && OUTPUT_FORMAT.equalsIgnoreCase("CSV"))
		            		{
		            			File csvFile = convertJsonToCSV(enrichedCDR, key);
		            			if (csvFile != null)
		            			{
		            				String csvKey = key + ".csv";
		            				uploadCSVFIleToS3(csvFile, csvKey);
		            			}
		            			else
		            			{
		            				lambdaContext.getLogger().log("CSV File not generated");
		            			}
		            		
		            			
		            		}
		            		else
		            		{
		            			uploadCDRToS3(enrichedCDR, key);
		            		}
		            		
		            		
		            		return String.valueOf(enrichedCDR.getFloat("CostUSD"));
		            		
		            		//lambdaContext.getLogger().log("cost of service is " + cost);
	            		}
	            		else
	            		{
	            			lambdaContext.getLogger().log("Unable to get the usage type for CDR record");
	            			return null;
	            		}
	            	}
	            }
	           
	            
	            return null;
	        } catch (Exception e) {
	            e.printStackTrace();
	            lambdaContext.getLogger().log(String.format(
	                "Error getting object %s from bucket %s. Make sure they exist and"
	                + " your bucket is in the same region as this function.", key, bucket));
	            throw e;
	        }
        }
        else
        {
        	lambdaContext.getLogger().log("Incoming Event object is not an Amazon Chime Voice Connector CDR" );
        	lambdaContext.getLogger().log("CDR Records must be in a Amazon-Chime-Voice-Connector-CDRs directory" );
        	return null;
        }
      
    }
    
    /**
     * This function converst the enriched CDR from JSON to CSV format
     * @param enrichedCDR
     */
    private File convertJsonToCSV(JSONObject enrichedCDR, String key )
    {
    	lambdaContext.getLogger().log("Converting enriched CDR to CSV format");
    	
    	JSONArray jsonArray = new JSONArray();
    	jsonArray.put(enrichedCDR);
    	
    	//convert file to a comma deliminated file in string
    	String csv = CDL.toString(jsonArray);
    	
    	lambdaContext.getLogger().log("CSV data is " + csv);
    	
    	ByteArrayOutputStream stream = new ByteArrayOutputStream();
    	FileWriter filewriter = null;
    	try
    	{
			stream.write(key.getBytes());
	
			
			String s3ObjectName = key.substring(key.lastIndexOf('/') + 1);
			//remove the .json extension
			String csvFileName = FilenameUtils.removeExtension(s3ObjectName);
			System.out.println("Removing original extension and adding new csv extension");
			File csvFile = new File("/tmp/"+csvFileName +".csv");
			
			filewriter = new FileWriter(csvFile);
		
			filewriter.write(csv);
		
			filewriter.close();
		
			 
			return csvFile;
    	}
	
    	catch(IOException ioe)
    	{
    		System.out.println("IO Excpetion while writing CSV file ");
    		System.out.println(ioe.getMessage());
    		
    	}
    	finally {
			 
            try {
            	
            	
            	if (filewriter != null )
            	{
            		lambdaContext.getLogger().log("csv file writer: Closing file stream ");
            		filewriter.close();
            	}
            	
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
               
            }
		}
       return null;
       
   
    	
    }
 
    /**
     * This method calls the AWS API Pricing and enriches the given CDR record with the price
     * @param usage_type
     * @return
     */
    public JSONObject  addCostToCDR(JSONObject cdrRecord, String usage_type )
    {
    	
    	lambdaContext.getLogger().log("Getting cost for CDR record with usage type "
    			+ usage_type );
    	
    	GetProductsRequest getProductsRequest = new GetProductsRequest();
    	getProductsRequest.setFormatVersion(DEFAULT_FORMAT_VERISION);
    	getProductsRequest.setServiceCode(CHIME_VOICE_CONNECTOR_SERVICE_CODE);
    	
    	List<Filter> filters = new Vector<Filter>();
    	
    	
    
    	Filter usageTypeFilter = new Filter();
    	//set the filter for usage Type - The usage type also includes the region,country and call type info.
    	usageTypeFilter.setField(CDR_USAGE_TYPE);
    	usageTypeFilter.setValue(usage_type);
    	
    	//TERM_MATCH returns only products that match both the given filter field and the given value.
    	usageTypeFilter.setType(TERM_MATCH_FILTER);
    	filters.add(usageTypeFilter);
    	
    	//set the filter
    	getProductsRequest.setFilters(filters);
    	
    	//get the API call result
    	GetProductsResult productResult = pricingClient.getProducts(getProductsRequest);
    	
    	
    	List<String> priceList = productResult.getPriceList();
    	
    	
    	if (priceList == null || priceList.isEmpty())
    	{
    		lambdaContext.getLogger().log("Unable to get the price list for usage type " + usage_type);
    		lambdaContext.getLogger().log("Please make sure the calling region is valid" );
    	}
    	else
    	{
    	
    		lambdaContext.getLogger().log("Price List is " + priceList.toString());
    		
	    	for(String price: priceList)
	    	{
	    		
	    		JSONObject jsonPrice = new JSONObject(price);
	    		
	    		JSONObject ondemand = (JSONObject) jsonPrice.query("/terms/OnDemand");
	    		String demandKey = ondemand.keys().next();
	    		JSONObject pricedemensions = (JSONObject)((JSONObject)ondemand.get(demandKey)).get("priceDimensions");
	    		String pricedemnsionkey = pricedemensions.keys().next();
	    		
	    		JSONObject priceUnit = (JSONObject) ((JSONObject)pricedemensions.get(pricedemnsionkey)).get("pricePerUnit");
	    		lambdaContext.getLogger().log("price per unit is " + priceUnit.toString());
	    		
	    		String priceUSD = (String) priceUnit.get("USD");
	    		
	    		//TODO - get the time period to match the CDR record
	    
	    		
	    		cdrRecord.put("PricePerUnitUSD", priceUSD);
	    		//calcualtes the total cost
	    		BigDecimal bprice = new BigDecimal(priceUSD);
	    		calculateCost(cdrRecord, bprice);
	    		
	    		
	    		//jsonPriceList.add(cdrJson);
	    	}
    	}
	    	
    	return cdrRecord;	
    	
    }
    
    private JSONObject calculateCost(JSONObject cdrRecord, BigDecimal pricePerCall)
    {
    	lambdaContext.getLogger().log("price per call is " + pricePerCall);
    	lambdaContext.getLogger().log("Billable duration in minutes is  " + cdrRecord.getFloat("BillableDurationMinutes"));
    	float cost = cdrRecord.getFloat("BillableDurationMinutes") * pricePerCall.floatValue();
		
		lambdaContext.getLogger().log("Cost for the phone call is " + cost);
				
		//enrich CDR with price per unit cost
		cdrRecord.put("CostUSD", cost);
		return cdrRecord;
    }
    
    public List<JSONObject> parseCDRRecord( S3Object s3object)
    {
    	List<JSONObject> jsonCDRs = new Vector<JSONObject>();
    	try
    	{
	    	InputStream inputSream = s3object.getObjectContent();
		
	        BufferedReader reader = new BufferedReader(new InputStreamReader(inputSream));
	        String line = null;
	        
	        String cdr_json = reader.readLine();
	        while ((line = reader.readLine()) != null) {
	        	//build the CDR Json Object
	        	 cdr_json = cdr_json + line;
	       
	        }
	        if (cdr_json !=null)
	        {
	        	lambdaContext.getLogger().log("JSON CDR record is " + cdr_json);
	        	JSONObject jsonCDR = new JSONObject(cdr_json);
	        	jsonCDRs.add(jsonCDR);
	        }
    	}
    	catch(IOException ioe)
		{
    		lambdaContext.getLogger().log("Unable to process the input stream " + ioe.getMessage());
		}
		finally 
		{
			try
			{
				// To ensure that the network connection doesn't remain open, close any open input streams.
	            if (s3object != null) {
	            	s3object.close();
	            }
			}
			catch (IOException e) {
				lambdaContext.getLogger().log("Cannot close the S3object stream " + e.getMessage() );
            // TODO Auto-generated catch block
            e.printStackTrace();
       
			}
           
        }
        return jsonCDRs;
    }
    /**
     * This API uploads the given CSV file to S3
     * @param csvFile
     * @param key
     * @return
     */
    private boolean uploadCSVFIleToS3(File csvFile, String key)
    {
    	try
    	{
	    	
	    	PutObjectRequest s3ObjectRequest = new PutObjectRequest(TARGET_BUCKET_NAME, key, csvFile);
	
			ObjectMetadata metadata = new ObjectMetadata();
	        metadata.setContentType("csv");
	       metadata.setContentLength(csvFile.length());
	        
	       
	       lambdaContext.getLogger().log("Uploading CSV file to S3 to bucket " + TARGET_BUCKET_NAME + " with key " + key);
	        PutObjectResult result =  s3.putObject(s3ObjectRequest);
	       
		    return true;
    	}
    	catch (AmazonServiceException e) {
			lambdaContext.getLogger().log("Amazon Service Exception : " +  e.getErrorMessage());
		}
    	catch (SdkClientException sdke) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
    		lambdaContext.getLogger().log("Amazon SDK Exception : " +  sdke.getMessage());
    		sdke.printStackTrace();
    	}
		return false;
    }
    /**
     * This method uploads the given CDR to the target bucket on S3. 
     * If the desired file type is CSV, the CSV record is uploaded, otherwise, JSON file is uploaded.
     * @param crdRecord
     * @param filename
     * @return
     */
    private boolean uploadCDRToS3(JSONObject crdRecord, String key )
    {
    	FileWriter filewriter = null;
    	String s3ObjectName = key.substring(key.lastIndexOf('/') + 1);
		try {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			stream.write(key.getBytes());

			lambdaContext.getLogger().log("S3 object name is " + s3ObjectName);
			
			File file = new File("/tmp/"+s3ObjectName);
			
			filewriter = new FileWriter(file);
			
			filewriter.write(crdRecord.toString());
			
			filewriter.close();
			PutObjectRequest s3ObjectRequest = new PutObjectRequest(TARGET_BUCKET_NAME, key, file);

			ObjectMetadata metadata = new ObjectMetadata();
	        metadata.setContentType("application/json");
	        metadata.setContentLength(crdRecord.length());
	        
		    s3.putObject(s3ObjectRequest);
		    return true;
		    
		} 
		catch (AmazonServiceException e) {
			lambdaContext.getLogger().log("Amazon Service Exception : " +  e.getErrorMessage());
		}
		catch (SdkClientException sdke) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
    		lambdaContext.getLogger().log("Amazon SDK Exception : " +  sdke.getMessage());
    		sdke.printStackTrace();
    	}
		catch(IOException ioe)
		{
			lambdaContext.getLogger().log("IO Exception : " +  ioe.getMessage());
		}
		finally {
			 
            try {
            	
            	lambdaContext.getLogger().log("Closing file stream");
            	if (filewriter != null)
            	{
            		filewriter.close();
            	}
            	
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return false;
            }
		}
		return false;
    }
  
    
}
