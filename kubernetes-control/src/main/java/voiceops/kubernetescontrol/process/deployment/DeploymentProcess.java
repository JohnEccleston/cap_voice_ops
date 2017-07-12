package voiceops.kubernetescontrol.process.deployment;

import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazonaws.services.route53.model.ChangeAction;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.fabric8.kubernetes.api.model.DeleteOptions;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private SpeechProcess speechProcess = new SpeechProcess();
  private RoutingProcess routingProcess = new RoutingProcess();

  public CallResponse createDeployment(Client client, String host, String token, String podName, String nameSpace, io.fabric8.kubernetes.api.model.extensions.Deployment dep) {
    String depPath =
        String.format("/apis/apps/v1beta1/namespaces/%s/deployments", nameSpace.toLowerCase());

    WebResource deployment = client.resource("https://" + host + depPath);

    Gson gson = new Gson();

    String deploymentPost = gson.toJson(dep);
    System.out.println(deploymentPost);

    ClientResponse response = deployment.header("Authorization", token)
        .type(MediaType.APPLICATION_JSON).post(ClientResponse.class, deploymentPost);

    if (response.getStatus() != 201) {
      if(response.getStatus() == 409) {
        CallResponse callResponse = new CallResponse(speechProcess.getTellSpeechletResponse(
            String.format("Cannot create %s deployment as a deployment with that name already exists.", podName)),
            false);
        return callResponse;
      }
      log.error("Failed in call to create deployment : HTTP error code : "
          + response.getStatus());
      CallResponse callResponse = new CallResponse(
          speechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been created"),
          false);
      return callResponse;
    }

    CallResponse callResponse = new CallResponse(
        speechProcess.getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace)),
        true);
    return callResponse;
  }

  public SpeechletResponse deleteDeployment(Client client, String host, String token, String podName, String nameSpace) {
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
          return speechProcess.getTellSpeechletResponse("Cannot delete " + podName + " deployment as it doesn't exist.");
        }
        log.error("Failed in call to delete deployment : HTTP error code : "
            + response.getStatus());

        return speechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been deleted");
      }
    } catch(Exception ex) {
        log.error("Exception when calling delete deployment api");
        log.error(ex.getMessage());
        ex.printStackTrace();
        return speechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API.");
      }
      return speechProcess.getTellSpeechletResponse(podName + " has been deleted from " + nameSpace);
    }

  public SpeechletResponse deploy(Client client, String host, String token, String podName, String nameSpace, String deployType) {

    try {
      Deployment dep;
      podName = podName.toLowerCase();
      nameSpace = nameSpace.toLowerCase();
      deployType = deployType.toLowerCase();

      log.info("deployType = " + deployType);

      if (deployType.contains("ngin")) {
        NginxModel nginxModel = new NginxModel(podName);
        dep = nginxModel.getDeployment();
        CallResponse response = createDeployment(client, host, token, podName, nameSpace, dep);
        if (!response.getSuccess()) {
          return response.getSpeechletResponse();
        }
        CallResponse  serviceResponse = serviceProcess.createService(client, host, token, podName, nameSpace);
      } else if (deployType.contains("gemini")) {
        CapgeminiModel capgeminiModel = new CapgeminiModel(podName);
        dep = capgeminiModel.getDeployment();
        CallResponse response = createDeployment(client, host, token, podName, nameSpace, dep);
        if (!response.getSuccess()) {
          return response.getSpeechletResponse();
        }
        CallResponse serviceResponse = serviceProcess.createService(client, host, token, podName, nameSpace);
        if(serviceResponse.getSuccess()) {
          CallResponse serviceDetails = serviceProcess.getService(client, host, token, podName, nameSpace);
          if(serviceDetails.getSuccess()) {
            CallResponse routingResponse = routingProcess.route(ChangeAction.CREATE, serviceDetails.getIp(), serviceDetails.getHost());
            if(routingResponse.getSuccess()) {
              return response.getSpeechletResponse();
            } else {
              return routingResponse.getSpeechletResponse();
            }
          }
        } else {
          return serviceResponse.getSpeechletResponse();
        }
      } else if (deployType.equalsIgnoreCase("serve")) {
        ServeHostnameModel serveHostnameModel = new ServeHostnameModel(podName);
        dep = serveHostnameModel.getDeployment();
        createDeployment(client, host, token, podName, nameSpace, dep);
      } else if (deployType.equalsIgnoreCase("postgres")) {
        PostgresModel postgresModel = new PostgresModel(podName);
        dep = postgresModel.getDeployment();
        createDeployment(client, host, token, podName, nameSpace, dep);
      } else {
        return speechProcess.getTellSpeechletResponse(String.format("No images found for %s, please check and try again", deployType));
      }

    } catch (Exception ex) {
      log.error("Exception when calling deploy deployment api");
      log.error(ex.getMessage());
      ex.printStackTrace();
      return speechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. No deployment was made.");
    }
    return speechProcess.getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace));
  }

}
