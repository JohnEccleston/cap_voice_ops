package voiceops.kubernetescontrol.process.service;

import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazonaws.services.route53.model.RRType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import jdk.nashorn.internal.codegen.CompilerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voiceops.kubernetescontrol.KubernetesControlSpeechlet;
import voiceops.kubernetescontrol.model.CallResponse;
import voiceops.kubernetescontrol.model.NginxServiceModel;

import javax.ws.rs.core.MediaType;

/**
 * Created by johneccleston on 12/07/2017.
 */
public class ServiceProcess extends KubernetesControlSpeechlet {

  private static final Logger log = LoggerFactory.getLogger(ServiceProcess.class);

  public SpeechletResponse createService(Client client, String host, String token, String podName, String nameSpace) {
    io.fabric8.kubernetes.api.model.Service service;
    NginxServiceModel nginxServiceModel = new NginxServiceModel(podName, nameSpace);

    service = nginxServiceModel.getService();

    String servicePath =
        String.format("/api/v1/namespaces/%s/services", nameSpace.toLowerCase());

    WebResource deployment = client.resource("https://" + host + servicePath);

    Gson gson = new Gson();

    String servicePost = gson.toJson(service);
    System.out.println(servicePost);

    ClientResponse response = deployment.header("Authorization", token)
        .type(MediaType.APPLICATION_JSON).post(ClientResponse.class, servicePost);

    if (response.getStatus() != 201) {
      if(response.getStatus() == 409) {
        return getTellSpeechletResponse("Cannot create service " + podName + " as it already exists. Deployment hasn't been created.");
      }
      log.error("Failed in call to create deployment : HTTP error code : "
          + response.getStatus());

      return getTellSpeechletResponse("Problem when talking to kubernetes API. Service has not been created, but deployment may have been.");
    }

    return getTellSpeechletResponse("Service " + podName + " has been deployed to " + nameSpace);
  }

  public CallResponse getService(Client client, String host, String token, String podName, String nameSpace) {
    String singleServicePath =
        String.format("/api/v1/namespaces/%s/services/%s", nameSpace, podName);

    CallResponse callResponse;

    String ip = "";
    String hostname = "";

    try {

      WebResource serviceResource = client.resource("https://" + host + singleServicePath);

      while (ip.length() == 0 && hostname.length() == 0) {

        Thread.sleep(5000);

        ClientResponse serGetResponse =
            serviceResource
                .header("Authorization", token)
                .accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);

        if (serGetResponse.getStatus() != 200) {
          throw new RuntimeException("Failed : HTTP error code : "
              + serGetResponse.getStatus());
        }

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(serGetResponse.getEntity(String.class));

        ip = root.path("status").path("loadBalancer").path("ingress").path(0).path("ip").asText();
        hostname = root.path("status").path("loadBalancer").path("ingress").path(0).path("hostname").asText();
      }
    }
    catch(Exception ex) {
      log.error("Exception when calling get service api");
      log.error(ex.getMessage());
      ex.printStackTrace();
      callResponse = new CallResponse(getTellSpeechletResponse("Problem when talking to kubernetes API."), false);
      return callResponse;
    }
    callResponse = new CallResponse(getTellSpeechletResponse("getService was successful"), true);
    callResponse.setHost(hostname);
    callResponse.setIp(ip);
    return callResponse;

  }

  public CallResponse deleteService(Client client, String host, String token, String podName, String nameSpace) {

    CallResponse callResponse;
    String servicePath =
        String.format("/api/v1/namespaces/%s/services/%s", nameSpace.toLowerCase(), podName.toLowerCase());

    try {
        WebResource service = client.resource("https://" + host + servicePath);
        ClientResponse serviceResponse = service.header("Authorization", token)
            .type(MediaType.APPLICATION_JSON).delete(ClientResponse.class);
            if (serviceResponse.getStatus() != 200 && serviceResponse.getStatus() != 404) {
          log.error("Failed in call to delete service : HTTP error code : "
              + serviceResponse.getStatus());
          callResponse = new CallResponse(getTellSpeechletResponse("Problem when talking to kubernetes API. Service has not been deleted"), false);
          return callResponse;
        }

    } catch(Exception ex) {
        log.error("Exception when calling delete service api");
        log.error(ex.getMessage());
        ex.printStackTrace();
        callResponse = new CallResponse(getTellSpeechletResponse("Problem when talking to kubernetes API."), false);
        return callResponse;
    }
    callResponse = new CallResponse(getTellSpeechletResponse(String.format("Service %s has been deleted from %s", podName, nameSpace)), true);
    return callResponse;
  }
}
