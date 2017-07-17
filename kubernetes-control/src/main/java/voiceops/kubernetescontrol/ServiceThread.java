package voiceops.kubernetescontrol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.route53.model.ChangeAction;
import com.sun.jersey.api.client.Client;

import voiceops.kubernetescontrol.model.CallResponse;
import voiceops.kubernetescontrol.process.deployment.DeploymentProcess;
import voiceops.kubernetescontrol.process.routing.RoutingProcess;
import voiceops.kubernetescontrol.process.service.ServiceProcess;

public class ServiceThread implements Runnable {
	
	private ServiceProcess serviceProcess;
	private Client client;
	private String host;
	private String token;
	private String podName;
	private String nameSpace;
	private RoutingProcess routingProcess;
	private DeploymentProcess deploymentProcess;
	private String fromHost;
	private String fromToken;
	private static final Logger log = LoggerFactory.getLogger(ServiceThread.class);
	
	public ServiceThread(ServiceProcess serviceProcess, RoutingProcess routingProcess, Client client,
			String host, String token, String podName, String nameSpace, DeploymentProcess deploymentProcess, String fromHost, String fromToken) {
		this.serviceProcess = serviceProcess;
		this.routingProcess = routingProcess;
		this.client = client;
		this.host = host;
		this.token = token;
		this.podName = podName;
		this.nameSpace = nameSpace;
		this.deploymentProcess = deploymentProcess;
		this.fromHost = fromHost;
		this.fromToken = fromToken;
		
	}

    public void run() {
        log.info("Service thread kicked off!!!!!!");
        log.info("deployment = " + podName);
        log.info("nameSpace = " + nameSpace);
        CallResponse serviceDetails = serviceProcess.getService(client, host, token, podName, nameSpace);
        if(serviceDetails.getSuccess()) {
      	  log.info("Successfully got service details for " + podName);
      	  log.info("Creating routes for " + podName);
          CallResponse routingResponse = routingProcess.route(ChangeAction.UPSERT, serviceDetails.getIp(), serviceDetails.getHost());
          if(routingResponse.getSuccess()) {
          	log.info("Successfully created route for " + podName);
            //return response.getSpeechletResponse();
          	//return response;
          } else {
          	log.error("Failed to create route for " + podName);
            //return routingResponse.getSpeechletResponse();
          	//return routingResponse;
          }
        }
        else {
        	log.error("Failed to get service for " + podName);
        }
        
        if(deploymentProcess != null) {
        	deploymentProcess.deleteDeployment(client, fromHost, fromToken, podName, nameSpace);
        }
        log.info("Service thread finished!!!!!!");	
    }
}
