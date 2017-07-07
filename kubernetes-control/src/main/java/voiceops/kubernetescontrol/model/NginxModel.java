package voiceops.kubernetescontrol.model;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by johneccleston on 05/07/2017.
 */
public class NginxModel {

  Deployment deployment = new Deployment();

  public NginxModel(String name) {

    ObjectMeta meta = new ObjectMeta();
    meta.setName(name);

    HashMap hm = new HashMap();
    hm.put("app", name);

    ObjectMeta podMeta = new ObjectMeta();
    podMeta.setLabels(hm);

    ContainerPort containerPort = new ContainerPort();
    //TODO make this a parameter
    containerPort.setContainerPort(80);
    List<ContainerPort> containerPortList = new ArrayList<ContainerPort>();
    containerPortList.add(containerPort);

    Container container = new Container();
    //TODO make these parameters?
    container.setName(name);
    container.setImage("nginx:1.10");
    container.setPorts(containerPortList);

    List<Container> containers = new ArrayList<Container>();
    containers.add(container);

    PodSpec podSpec = new PodSpec();
    podSpec.setContainers(containers);

    PodTemplateSpec podTemplateSpec = new PodTemplateSpec();
    podTemplateSpec.setMetadata(podMeta);
    podTemplateSpec.setSpec(podSpec);

    DeploymentSpec deploymentSpec = new DeploymentSpec();
    deploymentSpec.setReplicas(3);
    deploymentSpec.setRevisionHistoryLimit(10);
    deploymentSpec.setTemplate(podTemplateSpec);

    deployment.setApiVersion("apps/v1beta1");
    deployment.setMetadata(meta);

    deployment.setKind("Deployment");
    deployment.setSpec(deploymentSpec);

  }

  public Deployment getDeployment() {
    return deployment;
  }
}
