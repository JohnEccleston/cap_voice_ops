package voiceops.kubernetescontrol.process.status;

import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voiceops.kubernetescontrol.model.DeploymentAlexa;
import voiceops.kubernetescontrol.process.speech.SpeechProcess;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class StatusProcess {

  private static final Logger log = LoggerFactory.getLogger(StatusProcess.class);
  //private SpeechProcess speechProcess = new SpeechProcess();

  public SpeechletResponse getDeploymentStatus(Client client, String host, String token, String nameSpace, Session session) {

    //List<Pod> pods = new ArrayList<Pod>();
	  List<DeploymentAlexa> deployments = new ArrayList<DeploymentAlexa>();

    if (nameSpace == null) {
      String speechText = "OK. For what name space?";
      return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
    }

    String path = "/apis/apps/v1beta1/namespaces/" + nameSpace.toLowerCase() + "/deployments";

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

        return SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API.");
      }

      String output = r1.getEntity(String.class);
      JsonObject jsonObject = new JsonParser().parse(output).getAsJsonObject();
      JsonArray items = jsonObject.get("items").getAsJsonArray();

      for (JsonElement item : items) {
      	DeploymentAlexa deployment = new DeploymentAlexa();
      	deployment.setName(item.getAsJsonObject().get("metadata").getAsJsonObject().get("name").getAsString());
      	deployment.setReplicas(item.getAsJsonObject().get("spec").getAsJsonObject().get("replicas").getAsInt());
      	deployment.setImage(item.getAsJsonObject().get("spec").getAsJsonObject().get("template").getAsJsonObject().get("spec").getAsJsonObject().get("containers").getAsJsonArray().get(0).getAsJsonObject().get("image").getAsString());
    	deployments.add(deployment);
      }
    }
    catch(Exception ex) {
      //error caught when calling web service
      //respond appropriately
      log.error("Exception when calling pods api");
      log.error(ex.getMessage());
      ex.printStackTrace();
      return SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API.");
    }

    if(deployments.isEmpty()) {
      return SpeechProcess.getTellSpeechletResponse("No deployments found for that name space.");
    }

    if(deployments.size() < 6) {
      return SpeechProcess.getDeploymentStatusSpeech(deployments);
    }
    return SpeechProcess.getMoreThan5Speech(deployments, session);
  }
}
