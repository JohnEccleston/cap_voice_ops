package voiceops.kubernetescontrol.process.routing;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voiceops.kubernetescontrol.model.CallResponse;
import voiceops.kubernetescontrol.process.speech.SpeechProcess;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class RoutingProcess {

  private static final Logger log = LoggerFactory.getLogger(RoutingProcess.class);
  //private SpeechProcess speechProcess = new SpeechProcess();

  public CallResponse route(ChangeAction action, String ip, String hostname ) {

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
          .withChangeBatch(new ChangeBatch()
              .withChanges(new Change(action,
                  new ResourceRecordSet(resourceName, rrType).withTTL(60L)
                      .withResourceRecords(
                          new ResourceRecord(resourceValue))))));

    } catch (NoSuchHealthCheckException |  NoSuchHostedZoneException nshe) {
        log.error("No routing found to delete, continuing as normal");
        callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse(
            "No routing found to delete")
            , true);
        return callResponse;
    } catch(Exception ex) {
      log.error(String.format("Exception when processing routing, call to %s failed", action.toString()));
      log.error(ex.getMessage());
      ex.printStackTrace();
      callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse(
          String.format("Problem when talking to kubernetes API while trying to %s routing", action.toString()))
          , false);
      return callResponse;
    }
    callResponse = new CallResponse(SpeechProcess.getTellSpeechletResponse(
        String.format("Call to %s routing has been processed successfully", action.toString()))
        , true);
    return callResponse;
  }
}
