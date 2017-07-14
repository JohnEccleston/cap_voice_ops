package voiceops.kubernetescontrol.process.speech;

import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voiceops.kubernetescontrol.model.DeploymentAlexa;

import java.util.List;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class SpeechProcess {

  private static final Logger log = LoggerFactory.getLogger(SpeechProcess.class);

  public static SpeechletResponse getAskSpeechletResponse(String speechText, String repromptText) {
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    card.setTitle("Session");
    card.setContent(speechText);

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);

    // Create reprompt
    PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
    repromptSpeech.setText(repromptText);
    Reprompt reprompt = new Reprompt();
    reprompt.setOutputSpeech(repromptSpeech);

    return SpeechletResponse.newAskResponse(speech, reprompt, card);
  }

  public static SpeechletResponse getTellSpeechletResponse(String speechText) {
    // Create the Simple card content.
    SimpleCard card = new SimpleCard();
    card.setTitle("Session");
    card.setContent(speechText);

    // Create the plain text output.
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(speechText);

    return SpeechletResponse.newTellResponse(speech, card);
  }

  public static SpeechletResponse getDeploymentStatusSpeech(List<DeploymentAlexa> deployments) {

	  StringBuilder sb = new StringBuilder("I will now list the deployments for this environment" +
				", their statuses, and their pod sizes. ");
		
		for(DeploymentAlexa deployment : deployments) {
			sb.append(deployment.getName() + ", is an  " + deployment.getImage() + " deployment, it has " + 
					deployment.getReplicas() + " pods, and is " + deployment.getStatus() + ". ");
		}
		sb.append("Thanks");
		
		return getTellSpeechletResponse(sb.toString());
  }

  public static SpeechletResponse getMoreThan5Speech(List<DeploymentAlexa> deployments, Session session) {

	  StringBuilder sb = new StringBuilder("This enviroment has " + deployments.size() + " deployments, would" +
				" you like me to list them all, their statuses, and pod sizes? ");
		
		session.setAttribute("deployments", deployments);
		return getAskSpeechletResponse(sb.toString(), sb.toString());
  }
}
