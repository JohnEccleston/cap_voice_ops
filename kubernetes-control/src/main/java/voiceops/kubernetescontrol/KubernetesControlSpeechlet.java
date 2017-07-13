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

import io.fabric8.kubernetes.api.model.DeleteOptions;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Scale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import voiceops.kubernetescontrol.model.*;
import voiceops.kubernetescontrol.process.deployment.DeploymentProcess;
import voiceops.kubernetescontrol.process.routing.RoutingProcess;
import voiceops.kubernetescontrol.process.service.ServiceProcess;

import javax.net.ssl.*;
import javax.ws.rs.core.MediaType;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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
    private static final String SLOT_FROM_CLOUD_PROVIDER = "fromCloudProvider";
    private static final String SLOT_TO_CLOUD_PROVIDER = "toCloudProvider";
    
    private String host_aws;
    private String host_azure;
//    private static final String HOST_AWS = "api.k8sdemo.capademy.com";
//    private static final String HOST_AZURE = "k8democluster-techchallengerg-24ed28.eastus.cloudapp.azure.com";
    private static String host;
    private static String token;
    private String token_azure;

    //TODO sort these out
		private ServiceProcess serviceProcess = new ServiceProcess();
		private DeploymentProcess deploymentProcess = new DeploymentProcess();
		private RoutingProcess routingProcess = new RoutingProcess();

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
        
        log.info("token when first entering intent = " + token);
        
        //probably remove this, but session doesn't seem to close when testing

				log.info("entering initialize");
        initialize();

        log.info("exited initialize");

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
        log.info("token = " + token);

        if ("CreateCluster".equals(intentName)) {
            return getCreateClusterResponse();
        }else if("GetPodStatus".equals(intentName)) {
        	return getPodStatusResponse(request.getIntent(), session);
        }else if("Confirm".equals(intentName)) {
        	return getConfirmResponse(request.getIntent(), session);
        }else if("ScalePod".equals(intentName)) {
        	return scalePod(request.getIntent(), session);
        }else if("DeleteDeployment".equals(intentName)) {
        	return deleteDeployment(request.getIntent(), session);
		} else if("DeployDeployment".equals(intentName)) {
        	return deployDeployment(request.getIntent(), session);
        } else if("MigrateDeployment".equals(intentName)) {
        	return migrateDeployment(request.getIntent(), session);
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            return getHelpResponse();
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            return getTellSpeechletResponse("Goodbye");
        }else if ("AMAZON.CancelIntent".equals(intentName)) {
        	 return getTellSpeechletResponse("Goodbye");
        }else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    private SpeechletResponse migrateDeployment(Intent intent, Session session) {

    	String podName = intent.getSlot(SLOT_POD_NAME).getValue();

    	if(podName == null) {
   		 String speechText = "Sorry, I did not hear the deployment name. Please say again?" +
   				 	"For example, You can say - Migrate deployment from aws to azure";
            return getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("podName = " + podName);
    	
    	String from = intent.getSlot(SLOT_FROM_CLOUD_PROVIDER).getValue();

    	if(from == null) {
   		 String speechText = "Sorry, I did not hear where you wanted to migrate from. Please say again?" +
   				 	"For example, You can say - Migrate deployment from aws to azure";
            return getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("from = " + from);
    	
    	String to = intent.getSlot(SLOT_TO_CLOUD_PROVIDER).getValue();

    	if(to == null) {
   		 String speechText = "Sorry, I did not hear where you wanted to migrate to. Please say again?" +
   				 	"For example, You can say - Migrate deployment from aws to azure";
            return getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("to = " + podName);
		return null;
	}

	private SpeechletResponse deployDeployment(Intent intent, Session session) {
			String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();
			if (nameSpace == null) {
				nameSpace = (String)session.getAttribute("namespace");
				if (nameSpace == null) {
					String speechText = "Sorry, I did not hear the name space. Please say again?" +
							"For example, You can say - deploy image to name Space with pod Name";
					return getAskSpeechletResponse(speechText, speechText);
				}
			}
			log.info("nameSpace = " + nameSpace);

			String podName = intent.getSlot(SLOT_POD_NAME).getValue();
			if (podName == null) {
				podName = (String)session.getAttribute("podName");
				if (podName == null) {
					String speechText = "Sorry, I did not hear the pod name. Please say again?" +
							"For example, You can say - deploy image to name Space with pod Name";
					return getAskSpeechletResponse(speechText, speechText);
				}
			}
			log.info("podName = " + podName);

			String deployType = intent.getSlot(SLOT_DEPLOY_TYPE).getValue();

			if (deployType == null) {
				session.setAttribute("namespace", nameSpace);
				session.setAttribute("podName", podName);
				String speechText = "OK. What deployment type would you like? " +
												"You can have engine ex, serve, or postgress";
				return getAskSpeechletResponse(speechText, speechText);
			}

			log.info("deployType = " + deployType);

			try {
				Deployment dep;
				podName = podName.toLowerCase();
				nameSpace = nameSpace.toLowerCase();
				deployType = deployType.toLowerCase();

				if (deployType.contains("ngin")) {
					NginxModel nginxModel = new NginxModel(podName);
					dep = nginxModel.getDeployment();
					CallResponse response =  deploymentProcess.createDeployment(client, host, token, podName, nameSpace, dep);
					if (!response.getSuccess()) {
						return response.getSpeechletResponse();
					}
					return serviceProcess.createService(client, host, token, podName, nameSpace);
				} else 	if (deployType.contains("gemini")) {
					CapgeminiModel capgeminiModel = new CapgeminiModel(podName);
					dep = capgeminiModel.getDeployment();
					CallResponse response = deploymentProcess.createDeployment(client, host, token, podName, nameSpace, dep);
					if (!response.getSuccess()) {
						return response.getSpeechletResponse();
					}
					return serviceProcess.createService(client, host, token,  podName, nameSpace);
				} else if (deployType.equalsIgnoreCase("serve")) {
					ServeHostnameModel serveHostnameModel = new ServeHostnameModel(podName);
					dep = serveHostnameModel.getDeployment();
					deploymentProcess.createDeployment(client, host, token, podName, nameSpace, dep);
				} else if (deployType.equalsIgnoreCase("postgres")) {
					PostgresModel postgresModel = new PostgresModel(podName);
					dep = postgresModel.getDeployment();
					deploymentProcess.createDeployment(client, host, token, podName, nameSpace, dep);
				} else {
					return getTellSpeechletResponse(String.format("No images found for %s, please check and try again", deployType));
				}

			} catch (Exception ex) {
				log.error("Exception when calling deploy deployment api");
				log.error(ex.getMessage());
				ex.printStackTrace();
				return getTellSpeechletResponse("Problem when talking to kubernetes API. No deployment was made.");
			}
			return getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace));
		}


//	private CallResponse createDeployment(Client client, String host, String podName, String nameSpace, Deployment dep) {
//		String depPath =
//				String.format("/apis/apps/v1beta1/namespaces/%s/deployments", nameSpace.toLowerCase());
//
//		WebResource deployment = client.resource("https://" + host + depPath);
//
//		Gson gson = new Gson();
//
//		String deploymentPost = gson.toJson(dep);
//		System.out.println(deploymentPost);
//
//		ClientResponse response = deployment.header("Authorization", token)
//				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, deploymentPost);
//
//		if (response.getStatus() != 201) {
//			if(response.getStatus() == 409) {
//				CallResponse callResponse = new CallResponse(getTellSpeechletResponse(
//						String.format("Cannot create %s deployment as a deployment with that name already exists.", podName)),
//						false);
//				return callResponse;
//			}
//			log.error("Failed in call to create deployment : HTTP error code : "
//					+ response.getStatus());
//			CallResponse callResponse = new CallResponse(
//					getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been created"),
//					false);
//			return callResponse;
//		}
//
//		CallResponse callResponse = new CallResponse(
//				getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace)),
//				true);
//		return callResponse;
//
//	}

//	private SpeechletResponse createService(Client client, String host, String podName, String nameSpace) {
//		Service service;
//		NginxServiceModel nginxServiceModel = new NginxServiceModel(podName, nameSpace);
//
//		service = nginxServiceModel.getService();
//
//		String servicePath =
//				String.format("/api/v1/namespaces/%s/services", nameSpace.toLowerCase());
//
//		WebResource deployment = client.resource("https://" + host + servicePath);
//
//		Gson gson = new Gson();
//
//		String servicePost = gson.toJson(service);
//		System.out.println(servicePost);
//
//		ClientResponse response = deployment.header("Authorization", token)
//				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, servicePost);
//
//		if (response.getStatus() != 201) {
//			if(response.getStatus() == 409) {
//				return getTellSpeechletResponse("Cannot create service " + podName + " as it already exists. Deployment hasn't been created.");
//			}
//			log.error("Failed in call to create deployment : HTTP error code : "
//					+ response.getStatus());
//
//			return getTellSpeechletResponse("Problem when talking to kubernetes API. Service has not been created, but deployment may have been.");
//		}
//
//		return getTellSpeechletResponse("Service " + podName + " has been deployed to " + nameSpace);
//
//	}

    private SpeechletResponse deleteDeployment(Intent intent, Session session) {
    	String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();

    	if(nameSpace == null) {
    		 String speechText = "Sorry, I did not hear the name space name. Please say again?" +
    				 	"For example, You can say - Delete pod Name from name Space";
             return getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("nameSpace = " + nameSpace);

    	String podName = intent.getSlot(SLOT_POD_NAME).getValue();

    	if(podName == null) {
   		 String speechText = "Sorry, I did not hear the pod name. Please say again?" +
   				 	"For example, You can say - Delete pod Name from name Space";
            return getAskSpeechletResponse(speechText, speechText);
    	}
    	log.info("podName = " + podName);

    	String speech = "Are you sure you want to delete this deployment?";

		session.setAttribute("delete", nameSpace + ":" + podName);
		return getAskSpeechletResponse(speech, speech);

	}
    
	private SpeechletResponse deleteAfterConfirm(String nameSpace, String podName) {

		SpeechletResponse response = deploymentProcess.deleteDeployment(client, host, token, podName, nameSpace);

		CallResponse serviceResponse = serviceProcess.getService(client, host, token, podName, nameSpace);

		if(serviceResponse.getSuccess()) {
			CallResponse routingResponse = routingProcess.createRouting(serviceResponse.getIp(), serviceResponse.getHost());
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
             return getAskSpeechletResponse(speechText, speechText);
    	}
    	
    	log.info("nameSpace = " + nameSpace);
    	
    	String podName = intent.getSlot(SLOT_POD_NAME).getValue();
    	
    	if(podName == null) {
   		 String speechText = "Sorry, I did not hear the pod name. Please say again?" +
   				 	"For example, You can say - Scale pod name in name space to 5, or, " +
   				     "You can say - Scale up/down pod name in name space";
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
       				 	"For example, You can say - Scale pod name in name space to 5, or, " +
       				     "You can say - Scale up/down pod name in name space";
                return getAskSpeechletResponse(speechText, speechText);
            }
    		return scaleByNumber(nameSpace, podName, scaleNumberInt, depScaleIn);
    	}
    	else if (scaleDir != null) {
    		return scaleUpDown(nameSpace, podName, scaleDir, depScaleIn);
    	}

		String speechText = "Sorry, I did not hear how you wanted to scale. Please say again?" +
			 	"For example, You can say - Scale pod name in name space to 5, or, " +
			     "You can say - Scale up/down pod name in name space";
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
	  			 	"For example, You can say - Scale pod name in name space to 5, or, " +
	  			     "You can say - Scale up/down pod name in name space";
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
			    String.format("/apis/extensions/v1beta1/namespaces/%s/deployments/%s/scale", nameSpace.toLowerCase(), podName.toLowerCase());
				WebResource hpaPut = client
				          .resource("https://" + host + depPath);
				String depScaleOut = gson.toJson(depScaleIn);
				hpaPut.header("Authorization", token)
				.type(MediaType.APPLICATION_JSON).put(depScaleOut);
		}
		catch(Exception ex) {
			return getTellSpeechletResponse("Problem when talking to kubernetes API.");
		}

		return getTellSpeechletResponse(String.format( "%s has been scaled to %s", podName, scaleNumber));
	}
	
	private Scale getdepScaleIn(String nameSpace, String podName) {
		
		Scale depScaleIn = null;
		 
		try {
				Gson gson = new Gson();
		      String depPath =
		          String.format("/apis/extensions/v1beta1/namespaces/%s/deployments/%s/scale", nameSpace.toLowerCase(), podName.toLowerCase());
		      log.info("depPath = " + depPath);
		      WebResource hpaGet = client
		          .resource("https://" + host + depPath);
		      ClientResponse depGetResponse = hpaGet.header("Authorization", token)
		    		  .accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
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
	
	private SpeechletResponse getConfirmResponse(Intent intent, Session session) {
		
		Map<String, Object> map = session.getAttributes();
		
		if(map.containsKey("pods")) {
			 Object obj = session.getAttribute("pods");
	    	 ObjectMapper mapper = new ObjectMapper();
	    	 List<Pod> pods = mapper.convertValue(obj, new TypeReference<List<Pod>>() { });
	    	 return getPodStatusSpeech(pods);
		}
		else if(map.containsKey("delete")) {
			String deleteStr = (String)session.getAttribute("delete");
			String[] deleteParams = deleteStr.split(":");
			return deleteAfterConfirm(deleteParams[0], deleteParams[1]);
		}
		return getTellSpeechletResponse("Sorry, I'm not sure what action you are trying to confirm.");
	}

//	private SpeechletResponse getlistAllPodStatusResponse(Intent intent, Session session) {
//    	 Object obj = session.getAttribute("pods"); 	 
//    	 ObjectMapper mapper = new ObjectMapper();
//    	 List<Pod> pods = mapper.convertValue(obj, new TypeReference<List<Pod>>() { });
//    	 return getPodStatusSpeech(pods);
//	}

	private SpeechletResponse getPodStatusResponse(Intent intent, Session session) {
    	
    	//String host = "api.k8sdemo.capademy.com";
    	//String path = "/api/v1/namespaces/kube-system/pods";
    	List<Pod> pods = new ArrayList<Pod>();
    	
    	String nameSpace = intent.getSlot(SLOT_NAME_SPACE).getValue();
    	
    	log.info("nameSpace = " + intent.getSlot(SLOT_NAME_SPACE).getValue());
    	
        if (nameSpace == null) {
            String speechText = "OK. For what name space?";
            return getAskSpeechletResponse(speechText, speechText);
        }
        
        String path = "/api/v1/namespaces/" + nameSpace.toLowerCase() + "/pods";
    	
    	try {
	    	WebResource webResource = client
	                .resource("https://" + host + path);
	    	
	    	 ClientResponse r1 = webResource.header("Authorization", token)
	    			 .accept(MediaType.APPLICATION_JSON)
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

	public static SpeechletResponse getTellSpeechletResponse(String speechText) {
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
