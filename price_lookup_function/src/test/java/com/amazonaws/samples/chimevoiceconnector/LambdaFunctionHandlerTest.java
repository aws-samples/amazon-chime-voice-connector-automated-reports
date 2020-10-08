package com.amazonaws.samples.chimevoiceconnector;

import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

/**
 * A simple test harness for invoking Lambda function handler with sample CDR.
 */
@RunWith(MockitoJUnitRunner.class)
public class LambdaFunctionHandlerTest {

    private final String CONTENT_TYPE = "application/json";
    private S3Event event;

    @Mock
    private AmazonS3 s3Client;
    @Mock
    private S3Object s3Object;

    @Captor
    private ArgumentCaptor<GetObjectRequest> getObjectRequest;

    @Before
    public void setUp() throws IOException {
        event = TestUtils.parse("/s3-event.put.json", S3Event.class);
        
        File sampleCDR =  new File("src/test/resources/cdr_sample.json");
        InputStream targetStream = new FileInputStream(sampleCDR);
        S3ObjectInputStream s3ObjectInputStream = new S3ObjectInputStream(targetStream, null);
        // TODO: customize your mock logic for s3 client
       
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(CONTENT_TYPE);
        when(s3Object.getObjectMetadata()).thenReturn(objectMetadata);
        when(s3Object.getObjectContent()).thenReturn(s3ObjectInputStream);
        when(s3Client.getObject(getObjectRequest.capture())).thenReturn(s3Object);
    }

    private Context createContext() {
        TestContext ctx = new TestContext();

        // TODO: customize your context here if needed.
        ctx.setFunctionName("price lookup function");

        return ctx;
    }

    @Test
    public void testLambdaFunctionHandler() {
    	
        LambdaFunctionHandler handler = new LambdaFunctionHandler(s3Client);
        Context ctx = createContext();
        
      
        String output = handler.handleRequest(event, ctx);
    
       
        // TODO: validate output here if needed.
        Assert.assertEquals("0.0192", output);
    }
}
