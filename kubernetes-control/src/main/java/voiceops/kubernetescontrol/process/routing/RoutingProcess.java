package voiceops.kubernetescontrol.process.routing;

import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voiceops.kubernetescontrol.KubernetesControlSpeechlet;
import voiceops.kubernetescontrol.model.CallResponse;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class RoutingProcess extends KubernetesControlSpeechlet {

  private static final Logger log = LoggerFactory.getLogger(RoutingProcess.class);

  public CallResponse createRouting(String ip, String hostname ) {

    CallResponse callResponse;

    String resourceName = "k8sdemo.capademy.com";
    RRType rrType = RRType.CNAME;
    String resourceValue = "";

    if ( hostname.length() != 0 ) {
      resourceName = "aws." + resourceName;
      rrType = RRType.CNAME;
      resourceValue = hostname;
    } else if (ip.length() != 0 ) {
      resourceName = "azure." + resourceName;
      rrType = RRType.A;
      resourceValue = ip;
    }

    AmazonRoute53 route53 = AmazonRoute53ClientBuilder
        .standard()
        .withRegion(Regions.EU_WEST_1)
        .build();

    try {
        route53.changeResourceRecordSets(new ChangeResourceRecordSetsRequest()
            .withHostedZoneId("Z1EEC6GA7MB3OO")
            .withChangeBatch( new ChangeBatch()
                .withChanges( new Change(ChangeAction.CREATE,
                    new ResourceRecordSet(resourceName, rrType).withTTL(60L)
                        .withResourceRecords(
                            new ResourceRecord(resourceValue))))));


    } catch(Exception ex) {
      log.error("Exception when creating routing");
      log.error(ex.getMessage());
      ex.printStackTrace();
      callResponse = new CallResponse(getTellSpeechletResponse("Problem when talking to kubernetes API."), false);
      return callResponse;
    }
    callResponse = new CallResponse(getTellSpeechletResponse("Routing has been created"), true);
    return callResponse;
  }
}
