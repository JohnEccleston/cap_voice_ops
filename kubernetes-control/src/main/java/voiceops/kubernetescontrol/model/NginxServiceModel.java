package voiceops.kubernetescontrol.model;

import io.fabric8.kubernetes.api.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by johneccleston on 05/07/2017.
 */
public class NginxServiceModel {

  Service service = new Service();

  public NginxServiceModel(String name, String namespace) {

    ObjectMeta metadata = new ObjectMeta();
    metadata.setName(name);
    metadata.setNamespace(namespace);

    IntOrString intOrString = new IntOrString();
    intOrString.setIntVal(80);

    ServicePort servicePort = new ServicePort();
    servicePort.setName("tcp-80-80-"+name);
    servicePort.setPort(80);
    servicePort.setNodePort(31400);

    List<ServicePort> servicePorts = new ArrayList<ServicePort>();
    servicePorts.add(servicePort);

    HashMap hm = new HashMap();
    hm.put("app", name);

    ServiceSpec spec = new ServiceSpec();
    spec.setPorts(servicePorts);
    spec.setSelector(hm);
    spec.setType("LoadBalancer");

    service.setKind("Service");
    service.setApiVersion("v1");
    service.setMetadata(metadata);
    service.setSpec(spec);
  }

  public Service getService() {
    return service;
  }
}
