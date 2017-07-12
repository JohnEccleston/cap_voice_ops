package voiceops.kubernetescontrol.process.speech;

import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voiceops.kubernetescontrol.Pod;

import java.util.List;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class SpeechProcess {

  private static final Logger log = LoggerFactory.getLogger(SpeechProcess.class);

  public SpeechletResponse getAskSpeechletResponse(String speechText, String repromptText) {
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

  public SpeechletResponse getPodStatusSpeech(List<Pod> pods) {

    StringBuilder sb = new StringBuilder("I will now list the pods for this environment " +
        "and their statuses. ");

    for(Pod pod : pods) {
      sb.append(pod.getName() + ", " + pod.getStatus() + ". ");
    }
    sb.append(" Thanks");

    return getTellSpeechletResponse(sb.toString());
  }

  public SpeechletResponse getMoreThan5Speech(List<Pod> pods, Session session) {

    StringBuilder sb = new StringBuilder("This enviroment has " + pods.size() + " pods, would" +
        " you like me to list them all and their statuses. ");

    session.setAttribute("pods", pods);
    return getAskSpeechletResponse(sb.toString(), sb.toString());
  }
}
