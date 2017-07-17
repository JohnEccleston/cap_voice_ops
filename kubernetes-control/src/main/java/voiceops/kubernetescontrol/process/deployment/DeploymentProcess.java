package voiceops.kubernetescontrol.process.deployment;

import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazonaws.services.route53.model.ChangeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.fabric8.kubernetes.api.model.DeleteOptions;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import voiceops.kubernetescontrol.ServiceThread;
import voiceops.kubernetescontrol.model.*;
import voiceops.kubernetescontrol.process.routing.RoutingProcess;
import voiceops.kubernetescontrol.process.service.ServiceProcess;
import voiceops.kubernetescontrol.process.speech.SpeechProcess;

import javax.ws.rs.core.MediaType;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class DeploymentProcess {

  private static final Logger log = LoggerFactory.getLogger(DeploymentProcess.class);
  
  private ServiceProcess serviceProcess = new ServiceProcess();
  //private SpeechProcess speechProcess = new SpeechProcess();
  private RoutingProcess routingProcess = new RoutingProcess();
  
  public CallResponse getDeployment(Client client, String host, String token, String podName, String nameSpace) {
	  
	  Deployment dep;
	  
	  try {
		  String depPath =
			        String.format("/apis/apps/v1beta1/namespaces/%s/deployments/%s", nameSpace.toLowerCase(), podName.toString());
		  
		  WebResource deployment = client.resource("https://" + host.toLowerCase() + depPath);
	
		  ClientResponse response = deployment.accept(MediaType.APPLICATION_JSON)
	    		  .header("Authorization", token)
	          .get(ClientResponse.class);
		  
		  if(response.getStatus() != 200) {
		        if (response.getStatus() == 404) {
		        	 CallResponse callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse(
		        	            String.format("Cannot migrate %s deployment as deployment doesn't exist.", podName)),
		        	            false);
		        	        return callResponse;
		        }
		        log.error("Failed in call to create deployment : HTTP error code : "
		                + response.getStatus());
		            CallResponse callResponse = new CallResponse(
		          		  SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been migrated"),
		                false);
		            return callResponse;
		  }
		  
		  ObjectMapper objectMapper = new ObjectMapper();
	      JsonNode root = objectMapper.readTree(response.getEntity(String.class));
	      podName = root.path("metadata").path("name").asText();
	      nameSpace = root.path("metadata").path("name").asText();
	      String deployType = root.path("spec").path("template").path("spec").path("containers").path(0).path("image").asText();
	      int replicas = root.path("spec").path("replicas").asInt();
	      
	      if (deployType.contains("ngin")) {
	          NginxModel nginxModel = new NginxModel(podName, replicas);
	          dep = nginxModel.getDeployment();
	      } 
	      else if (deployType.contains("dougthomson")) {
	          CapgeminiModel capgeminiModel = new CapgeminiModel(podName, replicas);
	          dep = capgeminiModel.getDeployment();
	      }
	      else if (deployType.equalsIgnoreCase("serve")) {
	          ServeHostnameModel serveHostnameModel = new ServeHostnameModel(podName, replicas);
	          dep = serveHostnameModel.getDeployment();
	      }
	      else if (deployType.equalsIgnoreCase("postgres")) {
	          PostgresModel postgresModel = new PostgresModel(podName, replicas);
	          dep = postgresModel.getDeployment();
	          //createDeployment(client, host, token, podName, nameSpace, dep);
	      }
	      else {
		            CallResponse callResponse = new CallResponse(
		          		  SpeechProcess.getTellSpeechletResponse(String.format("No images found for %s, please check and try again", deployType)),
		                false);
		            return callResponse;
	    	  //return SpeechProcess.getTellSpeechletResponse(String.format("No images found for %s, please check and try again", deployType));
	      }
	      
	  }
	  catch(Exception ex){
		  ex.printStackTrace();
		  CallResponse callResponse = new CallResponse(
	    		  SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been created"),
	          false);
	      return callResponse;
	  }
	  CallResponse callResponse = new CallResponse(
      		  SpeechProcess.getTellSpeechletResponse(""),
              true);
	  callResponse.setDeployment(dep);
      return callResponse;
	  
  }

  public CallResponse createDeployment(Client client, String host, String token, String podName, String nameSpace, Deployment dep) {
    String depPath =
        String.format("/apis/apps/v1beta1/namespaces/%s/deployments", nameSpace);
    
    log.info("depPath = " + depPath);
    log.info("fullUrl = " + "https://" + host + depPath);

    WebResource deployment = client.resource("https://" + host + depPath);

    Gson gson = new Gson();

    String deploymentPost = gson.toJson(dep);
    System.out.println(deploymentPost);

    ClientResponse response = deployment.header("Authorization", token)
        .type(MediaType.APPLICATION_JSON).post(ClientResponse.class, deploymentPost);

    if (response.getStatus() != 201) {
      if(response.getStatus() == 409) {
        CallResponse callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse(
            String.format("Cannot create %s deployment as a deployment with that name already exists.", podName)),
            false);
        return callResponse;
      }
      log.error("Failed in call to create deployment : HTTP error code : "
          + response.getStatus());
      CallResponse callResponse = new CallResponse(
    		  SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been created"),
          false);
      return callResponse;
    }

    CallResponse callResponse = new CallResponse(
    		SpeechProcess.getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace)),
        true);
    return callResponse;
  }

  public CallResponse deleteDeployment(Client client, String host, String token, String podName, String nameSpace) {
    try {

      DeleteOptions deleteOptions = new DeleteOptions();
      deleteOptions.setKind("DeleteOptions");
      deleteOptions.setApiVersion("apps/v1beta1");
      deleteOptions.setGracePeriodSeconds(10L);

      Gson gson = new Gson();
      String deploymentDelete = gson.toJson(deleteOptions);
      String deleteManipulated = deploymentDelete.replace("{}", "{},\"propagationPolicy\":\"Foreground\"");

      String depPath =
          String.format("/apis/apps/v1beta1/namespaces/%s/deployments/%s", nameSpace.toLowerCase(), podName.toLowerCase());

      WebResource deployment = client.resource("https://" + host + depPath);
      ClientResponse response = deployment.header("Authorization", token)
          .type(MediaType.APPLICATION_JSON).delete(ClientResponse.class, deleteManipulated);
      if (response.getStatus() != 200) {
        if (response.getStatus() == 404) {
          //return SpeechProcess.getTellSpeechletResponse("Cannot delete " + podName + " deployment as it doesn't exist.");
        	 CallResponse callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse("Cannot delete " + podName + " deployment as it doesn't exist."), false);
        	 return callResponse;
        }
        log.error("Failed in call to delete deployment : HTTP error code : "
            + response.getStatus());

        //return SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been deleted");
        CallResponse callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been deleted"), false);
        return callResponse;
      }
    } catch(Exception ex) {
        log.error("Exception when calling delete deployment api");
        log.error(ex.getMessage());
        ex.printStackTrace();
        //return SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API.");
        CallResponse callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API."),false);
        return callResponse;
      }
      //return SpeechProcess.getTellSpeechletResponse(podName + " has been deleted from " + nameSpace);
      CallResponse callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse(podName + " has been deleted from " + nameSpace), true);
      return callResponse;
    }
  
  public CallResponse migrate(Client client, String host, String token, String podName, String nameSpace, String deployType, int replicas, String fromHost, String fromToken) {
	  try {
	      Deployment dep;
	      podName = podName.toLowerCase();
	      nameSpace = nameSpace.toLowerCase();
	      deployType = deployType.toLowerCase();

	      log.info("deployType = " + deployType);

	      if (deployType.contains("ngin")) {
	        NginxModel nginxModel = new NginxModel(podName, replicas);
	        dep = nginxModel.getDeployment();
	        CallResponse response = createDeployment(client, host, token, podName, nameSpace, dep);
	        if (!response.getSuccess()) {
	          //return response.getSpeechletResponse();
	        	return response;
	        }
	        CallResponse  serviceResponse = serviceProcess.createService(client, host, token, podName, nameSpace);
	      } else if (deployType.contains("gemini") || deployType.contains("dougthomson")) {
	    	log.info("Capgemini / Doug deployment");
	        CapgeminiModel capgeminiModel = new CapgeminiModel(podName, replicas);
	        dep = capgeminiModel.getDeployment();
	        log.info("Creating deployment " + podName);
	        CallResponse response = createDeployment(client, host, token, podName, nameSpace, dep);       
	        if (!response.getSuccess()) {
	        	log.info("Failed to create deployment " + podName);
	          //return response.getSpeechletResponse();
	        	return response;
	        }
	        log.info("Successfully Created deployment " + podName);
	        log.info("Creating creating service for " + podName);
	        CallResponse serviceResponse = serviceProcess.createService(client, host, token, podName, nameSpace);
	        if(serviceResponse.getSuccess()) {
	        log.info("Successfully Created service for " + podName);
	        
	        ServiceThread thread = new ServiceThread(serviceProcess, routingProcess, client, 
	        		host, token, podName, nameSpace, this, fromHost, fromToken);
	        
	        (new Thread(thread)).start();
	        //log.info("Retrieving service details for " + podName);
	          //CallResponse serviceDetails = serviceProcess.getService(client, host, token, podName, nameSpace);
	          //if(serviceDetails.getSuccess()) {
	        	  //log.info("Successfully got service details for " + podName);
	        	  //log.info("Creating routes for " + podName);
	            //CallResponse routingResponse = routingProcess.route(ChangeAction.CREATE, serviceDetails.getIp(), serviceDetails.getHost());
	            //if(routingResponse.getSuccess()) {
	            	//log.info("Successfully created route for " + podName);
	              //return response.getSpeechletResponse();
	            	//return response;
	           // } else {
	            	//log.error("Failed to create route for " + podName);
	              //return routingResponse.getSpeechletResponse();
	            	//return routingResponse;
	            //}
	          //}
	        //} else {
	        	//log.error("Failed to get service for " + podName);
	          //return serviceResponse.getSpeechletResponse();
	        	//return serviceResponse;
	        }
	      } else if (deployType.equalsIgnoreCase("serve")) {
	        ServeHostnameModel serveHostnameModel = new ServeHostnameModel(podName, replicas);
	        dep = serveHostnameModel.getDeployment();
	        createDeployment(client, host, token, podName, nameSpace, dep);
	      } else if (deployType.equalsIgnoreCase("postgres")) {
	        PostgresModel postgresModel = new PostgresModel(podName, replicas);
	        dep = postgresModel.getDeployment();
	        createDeployment(client, host, token, podName, nameSpace, dep);
	      } else {
	        //return SpeechProcess.getTellSpeechletResponse(String.format("No images found for %s, please check and try again", deployType));
	    	  CallResponse response = new CallResponse(SpeechProcess.getTellSpeechletResponse(String.format("No images found for %s, please check and try again", deployType)), false);
	    	  return response;
	      }

	    } catch (Exception ex) {
	      log.error("Exception when calling deploy deployment api");
	      log.error(ex.getMessage());
	      ex.printStackTrace();
	      //return SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. No deployment was made.");
	      CallResponse response = new CallResponse(SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. No deployment was made."), false);
	      return response;
	    }
	    //return SpeechProcess.getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace));
	    CallResponse response = new CallResponse(SpeechProcess.getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace)), true);
	    return response;
	  }

  public CallResponse deploy(Client client, String host, String token, String podName, String nameSpace, String deployType, int replicas){//, String fromHost, String fromToken) {

	
    try {
      Deployment dep;
      podName = podName.toLowerCase();
      nameSpace = nameSpace.toLowerCase();
      deployType = deployType.toLowerCase();

      log.info("deployType = " + deployType);

      if (deployType.contains("ngin")) {
        NginxModel nginxModel = new NginxModel(podName, replicas);
        dep = nginxModel.getDeployment();
        CallResponse response = createDeployment(client, host, token, podName, nameSpace, dep);
        if (!response.getSuccess()) {
          //return response.getSpeechletResponse();
        	return response;
        }
        CallResponse  serviceResponse = serviceProcess.createService(client, host, token, podName, nameSpace);
      } else if (deployType.contains("gemini") || deployType.contains("dougthomson")) {
    	log.info("Capgemini / Doug deployment");
        CapgeminiModel capgeminiModel = new CapgeminiModel(podName, replicas);
        dep = capgeminiModel.getDeployment();
        log.info("Creating deployment " + podName);
        CallResponse response = createDeployment(client, host, token, podName, nameSpace, dep);       
        if (!response.getSuccess()) {
        	log.info("Failed to create deployment " + podName);
          //return response.getSpeechletResponse();
        	return response;
        }
        log.info("Successfully Created deployment " + podName);
        log.info("Creating creating service for " + podName);
        CallResponse serviceResponse = serviceProcess.createService(client, host, token, podName, nameSpace);
        if(serviceResponse.getSuccess()) {
        log.info("Successfully Created service for " + podName);	
        ServiceThread thread = new ServiceThread(serviceProcess, routingProcess, client, 
        		host, token, podName, nameSpace, null, null, null);
        
        (new Thread(thread)).start();
        //log.info("Retrieving service details for " + podName);
          //CallResponse serviceDetails = serviceProcess.getService(client, host, token, podName, nameSpace);
          //if(serviceDetails.getSuccess()) {
        	  //log.info("Successfully got service details for " + podName);
        	  //log.info("Creating routes for " + podName);
            //CallResponse routingResponse = routingProcess.route(ChangeAction.CREATE, serviceDetails.getIp(), serviceDetails.getHost());
            //if(routingResponse.getSuccess()) {
            	//log.info("Successfully created route for " + podName);
              //return response.getSpeechletResponse();
            	//return response;
           // } else {
            	//log.error("Failed to create route for " + podName);
              //return routingResponse.getSpeechletResponse();
            	//return routingResponse;
            //}
          //}
        //} else {
        	//log.error("Failed to get service for " + podName);
          //return serviceResponse.getSpeechletResponse();
        	//return serviceResponse;
        }
      } else if (deployType.equalsIgnoreCase("serve")) {
        ServeHostnameModel serveHostnameModel = new ServeHostnameModel(podName, replicas);
        dep = serveHostnameModel.getDeployment();
        createDeployment(client, host, token, podName, nameSpace, dep);
      } else if (deployType.equalsIgnoreCase("postgres")) {
        PostgresModel postgresModel = new PostgresModel(podName, replicas);
        dep = postgresModel.getDeployment();
        createDeployment(client, host, token, podName, nameSpace, dep);
      } else {
        //return SpeechProcess.getTellSpeechletResponse(String.format("No images found for %s, please check and try again", deployType));
    	  CallResponse response = new CallResponse(SpeechProcess.getTellSpeechletResponse(String.format("No images found for %s, please check and try again", deployType)), false);
    	  return response;
      }

    } catch (Exception ex) {
      log.error("Exception when calling deploy deployment api");
      log.error(ex.getMessage());
      ex.printStackTrace();
      //return SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. No deployment was made.");
      CallResponse response = new CallResponse(SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. No deployment was made."), false);
      return response;
    }
    //return SpeechProcess.getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace));
    CallResponse response = new CallResponse(SpeechProcess.getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace)), true);
    return response;
  }

}
