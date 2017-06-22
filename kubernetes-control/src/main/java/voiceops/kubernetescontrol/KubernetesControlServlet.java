package voiceops.kubernetescontrol;

import com.amazon.speech.speechlet.servlet.SpeechletServlet;

public class KubernetesControlServlet extends SpeechletServlet {
	
	public KubernetesControlServlet() {
	    this.setSpeechlet(new KubernetesControlSpeechlet());
	  }

}
