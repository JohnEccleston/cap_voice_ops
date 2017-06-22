/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package voiceops.kubernetescontrol;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

/**
 * This sample shows how to create a simple speechlet for handling speechlet requests.
 */
public class KubernetesControlSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(KubernetesControlSpeechlet.class);

   //@Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        // any initialization logic goes here
    }

    //@Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        return getWelcomeResponse();
    }

    //@Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        log.info("full request = ", request.toString());

        Intent intent = request.getIntent();
        //Slot slot = intent.getSlot("name");
        
        
        
        String intentName = (intent != null) ? intent.getName() : null;

        if ("CreateCluster".equals(intentName)) {
        	callKubernetesApi();
            return getCreateClusterResponse();
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            return getHelpResponse();
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    //@Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        // any cleanup logic goes here
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getWelcomeResponse() {
        String speechText = "Welcome to Voice Ops, I can launch a cluster and do much more";

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("VoiceOps");
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getCreateClusterResponse() {
        String speechText = "Cluster created, if you believe that, you'd believe anything.";

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("VoiceOps");
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);
        
       

        return SpeechletResponse.newTellResponse(speech, card);
    }

    /**
     * Creates a {@code SpeechletResponse} for the help intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHelpResponse() {
    	String speechOutput =
                "With Voice Ops, you can create"
                        + " a kubernetes cluster."
                        + " For example, you could say create,"
                        + " me a cluster?";

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("VoiceOps");
        card.setContent(speechOutput);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechOutput);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }
    
    private void callKubernetesApi() {
    	String host = "ec2-34-253-3-47.eu-west-1.compute.amazonaws.com";
    	String port = "443";
    	String path = "/healthz";
    	
    	log.info(SDKGlobalConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toString());
    	
    	AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
    			.withRegion(Regions.EU_WEST_1)
    			.build();
    	S3Object object = s3Client.getObject(new GetObjectRequest("k8sdemo-store", "config.yml"));
    	
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        HashMap<Object, Object> yamlParsers = (HashMap<Object, Object>) yaml.load(object.getObjectContent());
        log.info(Arrays.toString(yamlParsers.entrySet().toArray()));
    	
    	
    	String PROTO = "https://";
        try {
			URL url = new URL(PROTO + host + ":" + port + path);
			log.info("Getting endpoints from " + url);
            //HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
            
            
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
}
