package voiceops.kubernetescontrol.model;

import com.amazon.speech.speechlet.SpeechletResponse;

/**
 * Created by johneccleston on 07/07/2017.
 */
public class CallResponse {

  private SpeechletResponse speechletResponse;
  private Boolean success;
  private String host;
  private String ip;

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
}
