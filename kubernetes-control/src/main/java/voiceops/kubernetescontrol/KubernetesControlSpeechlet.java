/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package voiceops.kubernetescontrol;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.*;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import io.fabric8.kubernetes.api.model.PodTemplate;
import io.fabric8.kubernetes.api.model.PodTemplateList;
import io.fabric8.kubernetes.api.model.extensions.Scale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import javax.net.ssl.*;
import javax.ws.rs.core.MediaType;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This sample shows how to create a simple speechlet for handling speechlet requests.
 */
public class KubernetesControlSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(KubernetesControlSpeechlet.class);
    
    Client client = Client.create();
    TrustManager[] trustAllCerts;
    SSLContext sc;
    HostnameVerifier allHostsValid;
    private static final String SLOT_NAME_SPACE = "nameSpace";
    private static final String SLOT_SCALE = "scale";
    private static final String SLOT_SCALE_NUMBER = "scaleNumber";
    private static final String SLOT_POD_NAME = "podName";
    private static final String HOST = "api.k8sdemo.capademy.com";
    
   //@Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        initialize();
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
        
        //probably remove this, but session doesn't seem to close when testing
        initialize();

        Intent intent = request.getIntent();
        
        String intentName = (intent != null) ? intent.getName() : null;

        if ("CreateCluster".equals(intentName)) {
        	callKubernetesApi();
            return getCreateClusterResponse();
        } else if("GetPodStatus".equals(intentName)) {
        	return getPodStatusResponse(request.getIntent(), session);
        } else if("ListAllPodStatuses".equals(intentName)) {
        	return getlistAllPodStatusResponse(request.getIntent(), session);
        } else if("ScalePod".equals(intentName)) {
        	return scalePod(request.getIntent(), session);
        }else if ("AMAZON.HelpIntent".equals(intentName)) {
            return getHelpResponse();
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            return getTellSpeechletResponse("Goodbye");
        }else if ("AMAZON.CancelIntent".equals(intentName)) {
        	 return getTellSpeechletResponse("Goodbye");
        }else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    private SpeechletResponse scalePod(Intent intent, Session session) {
    	String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();
    	
    	if(nameSpace == null) {
    		 String speechText = "Sorry, I did not hear the name space name. Please say again?" +
    				 	"For example, You can say - Scale podName in nameSpace to 5, or, " +
    				     "You can say - Scale up/down podName in nameSpace";
             return getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("nameSpace = " + nameSpace);
    	
    	String podName = intent.getSlot(SLOT_POD_NAME).getValue();
    	
    	if(podName == null) {
   		 String speechText = "Sorry, I did not hear the pod name. Please say again?" +
   				 	"For example, You can say - Scale podName in nameSpace to 5, or, " +
   				     "You can say - Scale up/down podName in nameSpace";
            return getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("podName = " + podName);
    	
    	String scaleDir = null;
    	if(intent.getSlot(SLOT_SCALE) != null) {
    		scaleDir = intent.getSlot(SLOT_SCALE).getValue();
    		log.info("scaleDir = " + scaleDir);
    	}
    	String scaleNumber = null;
    	if(intent.getSlot(SLOT_SCALE_NUMBER) != null) {
    		scaleNumber = intent.getSlot(SLOT_SCALE_NUMBER).getValue();
    		log.info("scaleNumber = " + scaleNumber);
    	}
    	
    	Scale depScaleIn = getdepScaleIn(nameSpace, podName);
    	
    	if(scaleNumber != null) {
    		int scaleNumberInt;
    		try {
                scaleNumberInt = Integer.parseInt(intent.getSlot(SLOT_SCALE_NUMBER).getValue());
            } catch (NumberFormatException e) {
            	String speechText = "Sorry, I did not hear the number you wanted to scale by. Please say again?" +
       				 	"For example, You can say - Scale podName in nameSpace to 5, or, " +
       				     "You can say - Scale up/down podName in nameSpace";
                return getAskSpeechletResponse(speechText, speechText);
            }
    		return scaleByNumber(nameSpace, podName, scaleNumberInt, depScaleIn);
    	}
    	else if (scaleDir != null) {
    		return scaleUpDown(nameSpace, podName, scaleDir, depScaleIn);
    	}

		String speechText = "Sorry, I did not hear how you wanted to scale. Please say again?" +
			 	"For example, You can say - Scale podName in nameSpace to 5, or, " +
			     "You can say - Scale up/down podName in nameSpace";
        return getAskSpeechletResponse(speechText, speechText);
	}

	private SpeechletResponse scaleUpDown(String nameSpace, String podName, String scaleDir, Scale depScaleIn) {
		
		  if(depScaleIn == null) {
			  return getTellSpeechletResponse("Problem when talking to kubernetes API.");
		  }
      
	      if (scaleDir.equalsIgnoreCase("up")) {
	          depScaleIn.getSpec().setReplicas(depScaleIn.getSpec().getReplicas() + 1);
	      } 
	      else if (scaleDir.equalsIgnoreCase("down")) {
	          if (depScaleIn.getSpec().getReplicas() > 0) {
	            depScaleIn.getSpec().setReplicas(depScaleIn.getSpec().getReplicas() - 1);
	          }
	      }
	      else {
	    	  String speechText = "Sorry, I did not hear if you wanted to scale up or down. Please say again?" +
	  			 	"For example, You can say - Scale podName in nameSpace to 5, or, " +
	  			     "You can say - Scale up/down podName in nameSpace";
	          return getAskSpeechletResponse(speechText, speechText);
	      }
		return scaleByNumber(nameSpace, podName, depScaleIn.getSpec().getReplicas(), depScaleIn);
	}

	private SpeechletResponse scaleByNumber(String nameSpace, String podName, int scaleNumber, Scale depScaleIn) {
		
		 if(depScaleIn == null) {
			  return getTellSpeechletResponse("Problem when talking to kubernetes API.");
		  }
		 depScaleIn.getSpec().setReplicas(scaleNumber);
		try {
				Gson gson = new Gson();
				String depPath =
			    String.format("/apis/extensions/v1beta1/namespaces/%s/deployments/%s/scale", nameSpace, podName);
				WebResource hpaPut = client
				          .resource("https://" + HOST + depPath);
				String depScaleOut = gson.toJson(depScaleIn);
				hpaPut.type(MediaType.APPLICATION_JSON).put(depScaleOut);
		}
		catch(Exception ex) {
			return getTellSpeechletResponse("Problem when talking to kubernetes API.");
		}
		return getTellSpeechletResponse(podName + " has been scaled to " + scaleNumber);
	}
	
	private Scale getdepScaleIn(String nameSpace, String podName) {
		
		Scale depScaleIn = null;
		 
		try {
				Gson gson = new Gson();
		      String depPath =
		          String.format("/apis/extensions/v1beta1/namespaces/%s/deployments/%s/scale", nameSpace, podName);
		      log.info("depPath = " + depPath);
		      WebResource hpaGet = client
		          .resource("https://" + HOST + depPath);
		      ClientResponse depGetResponse = hpaGet.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
		      if (depGetResponse.getStatus() != 200) {
		    	  log.error("Failed in call to scale : HTTP error code : "
			              + depGetResponse.getStatus());
			        	
			      
		      }
		      else {
		    	  depScaleIn = gson.fromJson(depGetResponse.getEntity(String.class), Scale.class);
		      }
		}
		catch(Exception ex) {
			log.error("Exception when calling scale api");
    		log.error(ex.getMessage());
    		ex.printStackTrace();
		}
		
		return depScaleIn;
	}

	private SpeechletResponse getlistAllPodStatusResponse(Intent intent, Session session) {
    	 
    	 Object obj = session.getAttribute("pods");
    	     	 
    	 ObjectMapper mapper = new ObjectMapper();
    	 
    	 List<Pod> pods = mapper.convertValue(obj, new TypeReference<List<Pod>>() { });

    	 return getPodStatusSpeech(pods);
	}

	private SpeechletResponse getPodStatusResponse(Intent intent, Session session) {
    	
    	//String host = "api.k8sdemo.capademy.com";
    	//String path = "/api/v1/namespaces/kube-system/pods";
    	List<Pod> pods = new ArrayList<Pod>();
    	
    	String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();
    	
    	log.info("nameSpace = " + intent.getSlot(SLOT_NAME_SPACE).getValue());
    	log.info("Slot size = " + intent.getSlots().size());
    	log.info("Slot 1 = " + intent.getSlots().get(1));
    	
        if (nameSpace == null) {
            String speechText = "OK. For what name space?";
            return getAskSpeechletResponse(speechText, speechText);
        }
        
        String path = "/api/v1/namespaces/" + nameSpace + "/pods";
    	
    	try {
	    	WebResource webResource = client
	                .resource("https://" + HOST + path);
	    	
	    	 ClientResponse r1 = webResource.accept(MediaType.APPLICATION_JSON)
	    	            .get(ClientResponse.class);
	    	        log.info("Called the Client");
	
	
	        if (r1.getStatus() != 200) {
	          log.error("Failed in call to pods : HTTP error code : "
	              + r1.getStatus());
	        	
	        	return getTellSpeechletResponse("Problem when talking to kubernetes API.");
	        }
	    	
	        String output = r1.getEntity(String.class);
	        
	        JsonObject jsonObject = new JsonParser().parse(output).getAsJsonObject();
	        
	        JsonArray items = jsonObject.get("items").getAsJsonArray();
	        
	        //pods = new ArrayList<Pod>();
	        
	        for (JsonElement item : items) {
	        	Pod pod = new Pod();
	      	  	pod.setName(item.getAsJsonObject().get("metadata").getAsJsonObject().get("name").getAsString());
	      	  	pod.setStatus(item.getAsJsonObject().get("status").getAsJsonObject().get("phase").getAsString());
	      	    pods.add(pod);
	      	}
    	}
    	catch(Exception ex) {
    		//error caught when calling web service
    		//respond appropriately
    		log.error("Exception when calling pods api");
    		log.error(ex.getMessage());
    		ex.printStackTrace();
    		return getTellSpeechletResponse("Problem when talking to kubernetes API.");
    	}
		
    	if(pods.isEmpty()) {
    		return getTellSpeechletResponse("No pods found for that name space.");
    	}
    	
    	if(pods.size() < 6) {
    		return getPodStatusSpeech(pods);
    	}
    	return getMoreThan5Speech(pods, session);
	}
    
    private SpeechletResponse getMoreThan5Speech(List<Pod> pods, Session session) {
   	
		StringBuilder sb = new StringBuilder("This enviroment has " + pods.size() + " pods, would" +
				" you like me to list them all and their statuses. ");
		
		session.setAttribute("pods", pods);
		return getAskSpeechletResponse(sb.toString(), sb.toString());
	}

	private SpeechletResponse getTellSpeechletResponse(String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Session");
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        return SpeechletResponse.newTellResponse(speech, card);
    }
    
    private SpeechletResponse getAskSpeechletResponse(String speechText, String repromptText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Session");
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText(repromptText);
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptSpeech);

        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

	private SpeechletResponse getPodStatusSpeech(List<Pod> pods) {
		
		StringBuilder sb = new StringBuilder("I will now list the pods for this environment " +
				"and their statuses. ");
		
		for(Pod pod : pods) {
			sb.append(pod.getName() + ", " + pod.getStatus() + ". ");
		}
		sb.append(" Thanks");
		
		return getTellSpeechletResponse(sb.toString());
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
    	//String host = "ec2-34-250-241-111.eu-west-1.compute.amazonaws.com";
    	//String host = "api.k8sdemo.capademy.com";
    	String port = "443";
//    	String path = "/api";
      String path = "/api/v1/namespaces/kube-system/pods";
    	
    	log.info(SDKGlobalConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toString());
    	
    	try{

//        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
//            .withRegion(Regions.EU_WEST_1)
//            .build();
//        S3Object object = s3Client.getObject(new GetObjectRequest("k8sdemo-store", "access/creds.yml"));
//
//        Yaml yaml = new Yaml();
//        @SuppressWarnings("unchecked")
//        HashMap<Object, Object> yamlParsers = (HashMap<Object, Object>) yaml.load(object.getObjectContent());
//        final String user = yamlParsers.get("user").toString();
//        final String password = yamlParsers.get("password").toString();
//
//
//        // Create a trust manager that does not validate certificate chains
//        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
//          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
//            return null;
//          }
//          public void checkClientTrusted(X509Certificate[] certs, String authType) {
//          }
//          public void checkServerTrusted(X509Certificate[] certs, String authType) {
//          }
//        }
//        };
//
//        // Install the all-trusting trust manager
//        SSLContext sc = SSLContext.getInstance("SSL");
//        sc.init(null, trustAllCerts, new java.security.SecureRandom());
//        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
//
//        // Create all-trusting host name verifier
//        HostnameVerifier allHostsValid = new HostnameVerifier() {
//          public boolean verify(String hostname, SSLSession session) {
//            return true;
//          }
//        };
//
//        // Install the all-trusting host verifier
//        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

//        Authenticator.setDefault (new Authenticator() {
//          protected PasswordAuthentication getPasswordAuthentication() {
//            return new PasswordAuthentication(user, password.toCharArray());
//          }
//        });

        log.info("About to create the client");

        //Client client = Client.create();
        //client.addFilter(new HTTPBasicAuthFilter(user, password));

        log.info("Created Client");

        WebResource webResource = client
            .resource("https://" + HOST + path);

        log.info("Created WebResource");

//        PodList response = webResource.accept(MediaType.APPLICATION_JSON)
//            .get(PodList.class);

        ClientResponse r1 = webResource.accept(MediaType.APPLICATION_JSON)
            .get(ClientResponse.class);
        log.info("Called the Client");


        if (r1.getStatus() != 200) {
          throw new RuntimeException("Failed : HTTP error code : "
              + r1.getStatus());
        }

        log.info("Status is " + r1.getStatus());

//        String output = response.getApiVersion();
//        String output = r1.getEntity(String.class);
        
        Gson gson = new Gson();
        PodTemplateList podList = gson.fromJson(r1.getEntity(String.class), PodTemplateList.class );

        log.info(podList.getApiVersion());
        List<PodTemplate> pt = podList.getItems();
        
        PodTemplate pod = pt.get(1);

            
		} catch (Exception e) {
			// TODO Auto-generated catch block
        log.error("I have gone bang! " + e.getMessage());
			e.printStackTrace();
		}
    }
    
    private void initialize() {
    	 try{

             AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                 .withRegion(Regions.EU_WEST_1)
                 .build();
             S3Object object = s3Client.getObject(new GetObjectRequest("k8sdemo-store", "access/creds.yml"));

             Yaml yaml = new Yaml();
             @SuppressWarnings("unchecked")
             HashMap<Object, Object> yamlParsers = (HashMap<Object, Object>) yaml.load(object.getObjectContent());
             final String user = yamlParsers.get("user").toString();
             final String password = yamlParsers.get("password").toString();
         
             client.addFilter(new HTTPBasicAuthFilter(user, password));
             
             /*TrustManager[]*/ trustAllCerts = new TrustManager[] {new X509TrustManager() {
                 public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                   return null;
                 }
                 public void checkClientTrusted(X509Certificate[] certs, String authType) {
                 }
                 public void checkServerTrusted(X509Certificate[] certs, String authType) {
                 }
               }
               };
             
             // Install the all-trusting trust manager
             /*SSLContext*/ sc = SSLContext.getInstance("SSL");
             sc.init(null, trustAllCerts, new java.security.SecureRandom());
             HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

             // Create all-trusting host name verifier
             /*HostnameVerifier*/ allHostsValid = new HostnameVerifier() {
               public boolean verify(String hostname, SSLSession session) {
                 return true;
               }
             };
         }
         catch(Exception ex) {
         	log.error("Failure getting creds! " + ex.getMessage());
 			ex.printStackTrace();
         }
    }
}
