package voiceops.kubernetescontrol;

import java.util.HashSet;
import java.util.Set;

import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler;

public class KubernetesControlSpeechletRequestStreamHandler extends SpeechletRequestStreamHandler {
    private static final Set<String> supportedApplicationIds = new HashSet<String>();
    static {
        /*
         * This Id can be found on https://developer.amazon.com/edw/home.html#/ "Edit" the relevant
         * Alexa Skill and put the relevant Application Ids in this Set.
         */
        //supportedApplicationIds.add("amzn1.echo-sdk-ams.app.[unique-value-here]");
    	supportedApplicationIds.add("amzn1.ask.skill.85b797dc-0d20-488c-88d9-7533600ed139");
    }

    public KubernetesControlSpeechletRequestStreamHandler() {
        super(new KubernetesControlSpeechlet(), supportedApplicationIds);
    }
}
