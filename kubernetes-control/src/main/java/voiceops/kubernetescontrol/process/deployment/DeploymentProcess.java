package voiceops.kubernetescontrol.process.deployment;

import com.amazon.speech.speechlet.SpeechletResponse;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.fabric8.kubernetes.api.model.DeleteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voiceops.kubernetescontrol.KubernetesControlSpeechlet;
import voiceops.kubernetescontrol.model.CallResponse;

import javax.ws.rs.core.MediaType;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class DeploymentProcess extends KubernetesControlSpeechlet {

  private static final Logger log = LoggerFactory.getLogger(DeploymentProcess.class);

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
        CallResponse callResponse = new CallResponse(getTellSpeechletResponse(
            String.format("Cannot create %s deployment as a deployment with that name already exists.", podName)),
            false);
        return callResponse;
      }
      log.error("Failed in call to create deployment : HTTP error code : "
          + response.getStatus());
      CallResponse callResponse = new CallResponse(
          getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been created"),
          false);
      return callResponse;
    }

    CallResponse callResponse = new CallResponse(
        getTellSpeechletResponse(String.format("%s has been deployed to %s", podName, nameSpace)),
        true);
    return callResponse;
  }

  public SpeechletResponse deleteDeployment(Client client, String host, String token, String nameSpace, String podName) {
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
          return getTellSpeechletResponse("Cannot delete " + podName + " deployment as it doesn't exist.");
        }
        log.error("Failed in call to delete deployment : HTTP error code : "
            + response.getStatus());

        return getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been deleted");
      }
    } catch(Exception ex) {
        log.error("Exception when calling delete deployment api");
        log.error(ex.getMessage());
        ex.printStackTrace();
        return getTellSpeechletResponse("Problem when talking to kubernetes API.");
      }
      return getTellSpeechletResponse(podName + " has been deleted from " + nameSpace);
    }

}
