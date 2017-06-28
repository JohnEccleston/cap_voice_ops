/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package voiceops.kubernetescontrol;

import com.amazon.speech.slu.Intent;
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
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodTemplate;
import io.fabric8.kubernetes.api.model.PodTemplateList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import javax.net.ssl.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    
    
    
    //String accountToken = getEnvOrDefault("K8S_ACCOUNT_TOKEN", "/var/run/secrets/kubernetes.io/serviceaccount/token");

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
        log.info("full request = ", request);

        Intent intent = request.getIntent();
        
        String intentName = (intent != null) ? intent.getName() : null;
        
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
            
            TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
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
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
              public boolean verify(String hostname, SSLSession session) {
                return true;
              }
            };
        }
        catch(Exception ex) {
        	log.error("Failure getting creds! " + ex.getMessage());
			ex.printStackTrace();
        }

        if ("CreateCluster".equals(intentName)) {
        	callKubernetesApi();
            return getCreateClusterResponse();
        } else if("GetPodStatus".equals(intentName)) {
        	return getPodStatusResponse(request.getIntent());
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            return getHelpResponse();
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    private SpeechletResponse getPodStatusResponse(Intent intent) {
    	
    	String host = "api.k8sdemo.capademy.com";
    	String path = "/api/v1/namespaces/kube-system/pods";
    	
    	WebResource webResource = client
                .resource("https://" + host + path);
    	
    	 ClientResponse r1 = webResource.accept(MediaType.APPLICATION_JSON)
    	            .get(ClientResponse.class);
    	        log.info("Called the Client");


        if (r1.getStatus() != 200) {
          throw new RuntimeException("Failed : HTTP error code : "
              + r1.getStatus());
        }
    	
        String output = r1.getEntity(String.class);

        Gson gson = new Gson();
        
        JsonObject jsonObject = new JsonParser().parse(output).getAsJsonObject();
        
        JsonArray items = jsonObject.get("items").getAsJsonArray();
        
        List<Pod> pods = new ArrayList<Pod>();
        
        for (JsonElement item : items) {
        	Pod pod = new Pod();
      	  	pod.setName(item.getAsJsonObject().get("metadata").getAsJsonObject().get("name").getAsString());
      	  	pod.setStatus(item.getAsJsonObject().get("status").getAsJsonObject().get("phase").getAsString());
      	    pods.add(pod);
      	}
		
		return getPodStatusSpeech(pods);
	}

	private SpeechletResponse getPodStatusSpeech(List<Pod> pods) {
		
		StringBuilder sb = new StringBuilder("I will now list the pods for this environment " +
				"and their statuses. ");
		
		for(Pod pod : pods) {
			sb.append(pod.getName() + ", " + pod.getStatus() + ". ");
		}
		sb.append(" Thanks");
		 // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("VoiceOps");
        card.setContent(sb.toString());
        
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(sb.toString());
        
		return SpeechletResponse.newTellResponse(speech, card);
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
    	String host = "api.k8sdemo.capademy.com";
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
            .resource("https://" + host + path);

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
    
    private static String getEnvOrDefault(String var, String def) {
        String val = System.getenv(var);
        if (val == null) {
            val = def;
        }
        return val;
    }

    private static String getServiceAccountToken(String file)  {
        try {
            return new String(Files.readAllBytes(Paths.get(file)));
        } catch (IOException e) {
            log.error("unable to load service account token" + file);
            throw new RuntimeException("Unable to load services account token " + file);
        }
    }
}
