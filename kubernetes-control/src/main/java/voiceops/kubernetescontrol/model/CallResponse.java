package voiceops.kubernetescontrol.model;

import com.amazon.speech.speechlet.SpeechletResponse;

import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Scale;

/**
 * Created by johneccleston on 07/07/2017.
 */
public class CallResponse {

  private SpeechletResponse speechletResponse;
  private Boolean success;
  public Scale getScale() {
	return scale;
  }

  private String host;
  private String ip;
  private Scale scale;
  private Deployment deployment;

  public Deployment getDeployment() {
	return deployment;
}

public void setDeployment(Deployment deployment) {
	this.deployment = deployment;
}

public CallResponse(SpeechletResponse speechletResponse, Boolean success) {
    this.speechletResponse = speechletResponse;
    this.success = success;
  }

  public SpeechletResponse getSpeechletResponse() {
    return speechletResponse;
  }

  public Boolean getSuccess() {
    return success;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public void setScale(Scale depScaleIn) {
	this.scale = depScaleIn;
  }
}
