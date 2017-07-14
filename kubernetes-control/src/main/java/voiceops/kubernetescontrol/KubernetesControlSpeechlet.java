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
import com.amazonaws.regions.Regions;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import voiceops.kubernetescontrol.model.*;
import voiceops.kubernetescontrol.process.deployment.DeploymentProcess;
import voiceops.kubernetescontrol.process.routing.RoutingProcess;
import voiceops.kubernetescontrol.process.scale.ScaleProcess;
import voiceops.kubernetescontrol.process.service.ServiceProcess;
import voiceops.kubernetescontrol.process.speech.SpeechProcess;
import voiceops.kubernetescontrol.process.status.StatusProcess;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final String SLOT_DEPLOY_TYPE = "deployType";
    private static final String SLOT_CLOUD_PROVIDER = "cloudProvider";
    private static final String SLOT_FROM_CLOUD_PROVIDER = "from";
    private static final String SLOT_TO_CLOUD_PROVIDER = "to";
    
    private String host_aws;
    private String host_azure;
    private static String host;
    private static String token;
    private String token_azure;

    //TODO sort these out
	private ServiceProcess serviceProcess = new ServiceProcess();
	private DeploymentProcess deploymentProcess = new DeploymentProcess();
	private RoutingProcess routingProcess = new RoutingProcess();
	private ScaleProcess scaleProcess = new ScaleProcess();
	private StatusProcess statusProcess = new StatusProcess();
	//private SpeechProcess speechProcess = new SpeechProcess();

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
        
        host = host_aws;
        token = null;
        
        String cloudProvider = (String) session.getAttribute("provider");
        
        if(cloudProvider == null) {
        	cloudProvider = intent.getSlot(SLOT_CLOUD_PROVIDER).getValue();
        }
        
        log.info("Cloud Provider = " + cloudProvider);
        if(cloudProvider != null && cloudProvider.equalsIgnoreCase("azure")) {
        	log.info("Connecting to AZURE API's");
        	session.setAttribute("provider", "azure");
        	host = host_azure;
        	token = token_azure;
        }
        else {
        	session.setAttribute("provider", "aws");
        }
        
        log.info("host = " + host);

        if ("CreateCluster".equals(intentName)) {
            return getCreateClusterResponse();
        } else if("GetDeploymentStatus".equals(intentName)) {
        	return getDeploymentStatusResponse(request.getIntent(), session);
        } else if("Confirm".equals(intentName)) {
        	return getConfirmResponse(request.getIntent(), session);
        } else if("ScalePod".equals(intentName)) {
        	return scalePod(request.getIntent(), session);
        } else if("DeleteDeployment".equals(intentName)) {
        	return deleteDeployment(request.getIntent(), session);
	    } else if("DeployDeployment".equals(intentName)) {
        	return deployDeployment(request.getIntent(), session);
        } else if("MigrateDeployment".equals(intentName)) {
        	return migrateDeployment(request.getIntent(), session);
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            return getHelpResponse();
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            return SpeechProcess.getTellSpeechletResponse("Goodbye");
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
        	 return SpeechProcess.getTellSpeechletResponse("Goodbye");
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    private SpeechletResponse migrateDeployment(Intent intent, Session session) {
    	
    	String toHost;
    	String toToken;
    	String fromHost;
    	String fromToken; 
    	
    	String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();
		if (nameSpace == null) {
			nameSpace = (String) session.getAttribute("namespace");
			if (nameSpace == null) {
				String speechText = "Sorry, I did not hear the name space. Please say again?" +
						"For example, You can say - Migrate deployment in name space from aws to azure";
				return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
			}
		}

    	String podName = intent.getSlot(SLOT_POD_NAME).getValue();

    	if(podName == null) {
   		 String speechText = "Sorry, I did not hear the deployment name. Please say again?" +
   				 	"For example, You can say - Migrate deployment in name space from aws to azure";
            return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("podName = " + podName);
    	
    	String from = intent.getSlot(SLOT_FROM_CLOUD_PROVIDER).getValue();

    	if(from == null) {
   		 String speechText = "Sorry, I did not hear where you wanted to migrate from. Please say again?" +
   				 	"For example, You can say - Migrate deployment in name space from aws to azure";
            return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("from = " + from);
    	
    	String to = intent.getSlot(SLOT_TO_CLOUD_PROVIDER).getValue();

    	if(to == null) {
   		 String speechText = "Sorry, I did not hear where you wanted to migrate to. Please say again?" +
   				 	"For example, You can say - Migrate deployment in name space from aws to azure";
            return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("to = " + to);
    	
    	if(from.toLowerCase().equals("azure") && to.toLowerCase().equals("aws")) {
    		fromHost = host_azure;
    		fromToken = token_azure;
    		toHost = host_aws;
    		toToken = null;
    	}
    	else if(from.toLowerCase().equals("aws") && to.toLowerCase().equals("azure")) {
    		fromHost = host_aws;
    		fromToken = null;
    		toHost = host_azure;
    		toToken = token_azure;
    	}
    	else{
    		return SpeechProcess.getTellSpeechletResponse("Cannot migrate from " + from + "to " + to);
    	}
    	
    	CallResponse serviceResponse = deploymentProcess.getDeployment(client, fromHost, fromToken, podName, nameSpace);
    	
    	if(! serviceResponse.getSuccess()) {
    		return serviceResponse.getSpeechletResponse();
    	}
    	
    	log.info("deployment = " + serviceResponse.getDeployment().getMetadata().getName() );
    	//log.info("nameSpace = " + serviceResponse.getDeployment().getMetadata().getNamespace());
    	log.info("image = " + serviceResponse.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    	
    	CallResponse response = deploymentProcess.deploy(client, toHost, toToken, podName, nameSpace, serviceResponse.getDeployment().getSpec().getTemplate().getSpec().getContainers().get(0).getImage(), serviceResponse.getDeployment().getSpec().getReplicas());
    	
    	//log.info("speech = " + speech.getOutputSpeech().toString());
    	
    	if(!response.getSuccess()) {
    		return SpeechProcess.getTellSpeechletResponse(String.format("Failed to deploy %s to %s on %s. Migration has failed", podName, nameSpace, to));
    	}
    	
    	response = deploymentProcess.deleteDeployment(client, fromHost, fromToken, podName, nameSpace);
    	
    	//log.info("speech = " + speech.getOutputSpeech().toString());
    	
    	if(!response.getSuccess()) {
    		return SpeechProcess.getTellSpeechletResponse(String.format("Failed to delete %s from %s on %. Migration has not fully completed", podName, nameSpace, to));
    	}
    	
    	return SpeechProcess.getTellSpeechletResponse(String.format("Migrated %s from %s to %s", podName, from, to));
	}

	private SpeechletResponse deployDeployment(Intent intent, Session session) {
			String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();
			if (nameSpace == null) {
				nameSpace = (String) session.getAttribute("namespace");
				if (nameSpace == null) {
					String speechText = "Sorry, I did not hear the name space. Please say again?" +
							"For example, You can say - deploy image to name Space with pod Name";
					return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
				}
			}
			log.info("nameSpace = " + nameSpace);

			String podName = intent.getSlot(SLOT_POD_NAME).getValue();
			if (podName == null) {
				podName = (String) session.getAttribute("podName");
				if (podName == null) {
					String speechText = "Sorry, I did not hear the pod name. Please say again?" +
							"For example, You can say - deploy image to name Space with pod Name";
					return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
				}
			}
			log.info("podName = " + podName);

			String deployType = intent.getSlot(SLOT_DEPLOY_TYPE).getValue();

			if (deployType == null) {
				session.setAttribute("namespace", nameSpace);
				session.setAttribute("podName", podName);
				String speechText = "OK. What deployment type would you like? " +
						"You can have engine ex, serve, postgress, or capgemini";
				return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
			}

			return deploymentProcess.deploy(client, host, token, podName, nameSpace, deployType, 3).getSpeechletResponse();
		}

    private SpeechletResponse deleteDeployment(Intent intent, Session session) {
    	String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();

    	if(nameSpace == null) {
    		 String speechText = "Sorry, I did not hear the name space name. Please say again?" +
    				 	"For example, You can say - Delete pod Name from name Space";
             return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("nameSpace = " + nameSpace);

    	String podName = intent.getSlot(SLOT_POD_NAME).getValue();

    	if(podName == null) {
   		 String speechText = "Sorry, I did not hear the pod name. Please say again?" +
   				 	"For example, You can say - Delete pod Name from name Space";
            return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("podName = " + podName);

    	String speech = "Are you sure you want to delete this deployment?";

		session.setAttribute("delete", nameSpace + ":" + podName);
		return SpeechProcess.getAskSpeechletResponse(speech, speech);

	}
    
	private SpeechletResponse deleteAfterConfirm(String nameSpace, String podName) {

		SpeechletResponse response = deploymentProcess.deleteDeployment(client, host, token, podName, nameSpace).getSpeechletResponse();

		CallResponse serviceResponse = serviceProcess.getService(client, host, token, podName, nameSpace);

		if(serviceResponse.getSuccess()) {
			CallResponse routingResponse = routingProcess.route(ChangeAction.DELETE, serviceResponse.getIp(), serviceResponse.getHost());
		} else {
			return serviceResponse.getSpeechletResponse();
		}

		if(serviceResponse.getSuccess()) {
			CallResponse deleteResponse = serviceProcess.deleteService(client, host, token, podName, nameSpace);

			if(!deleteResponse.getSuccess()) {
				return deleteResponse.getSpeechletResponse();
			}
		}
		return response;
	}

	private SpeechletResponse scalePod(Intent intent, Session session) {
    	String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();
    	
    	if(nameSpace == null) {
    		 String speechText = "Sorry, I did not hear the name space name. Please say again?" +
    				 	"For example, You can say - Scale pod name in name space to 5, or, " +
    				     "You can say - Scale up/down pod name in name space";
             return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
    	}
    	
    	log.info("nameSpace = " + nameSpace);
    	
    	String podName = intent.getSlot(SLOT_POD_NAME).getValue();
    	
    	if(podName == null) {
   		 String speechText = "Sorry, I did not hear the pod name. Please say again?" +
   				 	"For example, You can say - Scale pod name in name space to 5, or, " +
   				     "You can say - Scale up/down pod name in name space";
            return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
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
    	
    	CallResponse response = scaleProcess.getdepScaleIn(client, host, token, nameSpace, podName);
    	
    	if(response.getSuccess()) {
	    	if(scaleNumber != null) {
	    		int scaleNumberInt;
	    		try {
	                scaleNumberInt = Integer.parseInt(intent.getSlot(SLOT_SCALE_NUMBER).getValue());
	            } catch (NumberFormatException e) {
	            	String speechText = "Sorry, I did not hear the number you wanted to scale by. Please say again?" +
	       				 	"For example, You can say - Scale pod name in name space to 5, or, " +
	       				     "You can say - Scale up/down pod name in name space";
	                return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
	            }
	    		return scaleProcess.scaleByNumber(client, host, token, nameSpace, podName, scaleNumberInt, response.getScale());
	    	}
	    	else if (scaleDir != null) {
	    		return scaleProcess.scaleUpDown(client, host, token, nameSpace, podName, scaleDir, response.getScale());
	    	}
	
			String speechText = "Sorry, I did not hear how you wanted to scale. Please say again?" +
				 	"For example, You can say - Scale pod name in name space to 5, or, " +
				     "You can say - Scale up/down pod name in name space";
	        return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
    	}
    	return response.getSpeechletResponse();
	}
	
	private SpeechletResponse getConfirmResponse(Intent intent, Session session) {
		
Map<String, Object> map = session.getAttributes();
		
		if(map.containsKey("deployments")) {
			 Object obj = session.getAttribute("deployments");
	    	 ObjectMapper mapper = new ObjectMapper();
	    	 List<DeploymentAlexa> deployments = mapper.convertValue(obj, new TypeReference<List<DeploymentAlexa>>() { });
	    	 return SpeechProcess.getDeploymentStatusSpeech(deployments);
		}
		else if(map.containsKey("delete")) {
			String deleteStr = (String)session.getAttribute("delete");
			String[] deleteParams = deleteStr.split(":");
			return deleteAfterConfirm(deleteParams[0], deleteParams[1]);
		}
		return SpeechProcess.getTellSpeechletResponse("Sorry, I'm not sure what action you are trying to confirm.");
	}

	private SpeechletResponse getDeploymentStatusResponse(Intent intent, Session session) {

		String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();

		log.info("nameSpace = " + intent.getSlot(SLOT_NAME_SPACE).getValue());

		return statusProcess.getDeploymentStatus(client, host, token, nameSpace, session);
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
        String speechText = "Welcome to Voice Ops!";

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
    	
    	String speechOutput = "I can get the statuses of clusters, "
        		+ "scale clusters, "
        		+ "make deployements, "
        		+ "delete deployments,  "
        		+ "and hopefully in the future take over the world!";

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


    private void initialize() {
    	 try{

					log.info("Entered initialize");
					AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
						 .withRegion(Regions.EU_WEST_1)
						 .build();
					S3Object object = s3Client.getObject(new GetObjectRequest("k8sdemo-store", "access/creds.yml"));

					Yaml yaml = new Yaml();

					@SuppressWarnings("unchecked")
					HashMap<Object, Object> yamlParsers = (HashMap<Object, Object>) yaml.load(object.getObjectContent());
					final String user = yamlParsers.get("awsuser").toString();
					final String password = yamlParsers.get("awspassword").toString();
					final String azuretoken = yamlParsers.get("azuretoken").toString();
					token_azure = "Bearer " + azuretoken;
					host_aws = yamlParsers.get("awshost").toString();
					host_azure = yamlParsers.get("azurehost").toString();

					client.addFilter(new HTTPBasicAuthFilter(user, password));

					/*TrustManager[]*/ trustAllCerts = new TrustManager[] {
							new X509TrustManager() {
								public java.security.cert.X509Certificate[] getAcceptedIssuers() {
								 return null;
								}
								public void checkClientTrusted(X509Certificate[] certs, String authType) {}
								public void checkServerTrusted(X509Certificate[] certs, String authType) {}
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
