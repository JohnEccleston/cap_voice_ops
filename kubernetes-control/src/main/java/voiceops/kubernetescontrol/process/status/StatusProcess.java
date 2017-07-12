package voiceops.kubernetescontrol.process.status;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voiceops.kubernetescontrol.KubernetesControlSpeechlet;
import voiceops.kubernetescontrol.Pod;
import voiceops.kubernetescontrol.process.speech.SpeechProcess;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class StatusProcess {

  private static final Logger log = LoggerFactory.getLogger(StatusProcess.class);
  private SpeechProcess speechProcess = new SpeechProcess();

  public SpeechletResponse getPodStatus(Client client, String host, String token, String nameSpace, Session session) {

    List<Pod> pods = new ArrayList<Pod>();

    if (nameSpace == null) {
      String speechText = "OK. For what name space?";
      return speechProcess.getAskSpeechletResponse(speechText, speechText);
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

        return speechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API.");
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
      return speechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API.");
    }

    if(pods.isEmpty()) {
      return speechProcess.getTellSpeechletResponse("No pods found for that name space.");
    }

    if(pods.size() < 6) {
      return speechProcess.getPodStatusSpeech(pods);
    }
    return speechProcess.getMoreThan5Speech(pods, session);
  }
}
