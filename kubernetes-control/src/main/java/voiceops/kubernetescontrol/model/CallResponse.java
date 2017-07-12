package voiceops.kubernetescontrol.model;

import com.amazon.speech.speechlet.SpeechletResponse;

import io.fabric8.kubernetes.api.model.extensions.Scale;

/**
 * Created by johneccleston on 07/07/2017.
 */
public class CallResponse {

  private SpeechletResponse speechletResponse;
  private Boolean success;
  private Scale scale;

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

public Scale getScale() {
	return scale;
}

public void setScale(Scale scale) {
	this.scale = scale;
}
}
