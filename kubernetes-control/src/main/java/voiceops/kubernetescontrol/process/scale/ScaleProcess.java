package voiceops.kubernetescontrol.process.scale;

import com.amazon.speech.speechlet.SpeechletResponse;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.fabric8.kubernetes.api.model.extensions.Scale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import voiceops.kubernetescontrol.model.CallResponse;
import voiceops.kubernetescontrol.process.speech.SpeechProcess;

import javax.ws.rs.core.MediaType;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class ScaleProcess {

  private static final Logger log = LoggerFactory.getLogger(ScaleProcess.class);
  //private SpeechProcess speechProcess = new SpeechProcess();

  public CallResponse getdepScaleIn(Client client, String host, String token, String nameSpace, String podName) {

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
        if(depGetResponse.getStatus() == 404) {
  		  	CallResponse callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse(
						"Cannot scale deployment as deployment doesn't exist."),
						false);
  		  return callResponse;
  	  	}
        log.error("Failed in call to scale : HTTP error code : "
                + depGetResponse.getStatus());
        
        CallResponse callResponse = new CallResponse(
				SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been scaled"),
				false);
        return callResponse;
      }
      else {
        depScaleIn = gson.fromJson(depGetResponse.getEntity(String.class), Scale.class);
      }
    }
    catch(Exception ex) {
      log.error("Exception when calling scale api");
      log.error(ex.getMessage());
      ex.printStackTrace();
      CallResponse callResponse = new CallResponse(
    		  SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API. Deployment has not been scaled"),
				false);
	  return callResponse;
    }

    CallResponse callResponse = new CallResponse(
			SpeechProcess.getTellSpeechletResponse(""),
			true);
	callResponse.setScale(depScaleIn);
	return callResponse;
  }

  public SpeechletResponse scaleUpDown(Client client, String host, String token, String nameSpace, String podName, String scaleDir, Scale depScaleIn) {

    if(depScaleIn == null) {
      return SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API.");
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
      return SpeechProcess.getAskSpeechletResponse(speechText, speechText);
    }
    return scaleByNumber(client, host, token, nameSpace, podName, depScaleIn.getSpec().getReplicas(), depScaleIn);
  }

  public SpeechletResponse scaleByNumber(Client client, String host, String token, String nameSpace, String podName, int scaleNumber, Scale depScaleIn) {

    if(depScaleIn == null) {
      return SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API.");
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
      return SpeechProcess.getTellSpeechletResponse("Problem when talking to kubernetes API.");
    }

    return SpeechProcess.getTellSpeechletResponse(String.format( "%s has been scaled to %s", podName, scaleNumber));
  }

}
