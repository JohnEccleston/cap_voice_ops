package voiceops.kubernetescontrol.model;

import com.amazon.speech.speechlet.SpeechletResponse;

/**
 * Created by johneccleston on 07/07/2017.
 */
public class CallResponse {

  private SpeechletResponse speechletResponse;
  private Boolean success;

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
}
