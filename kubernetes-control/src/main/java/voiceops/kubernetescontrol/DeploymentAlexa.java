package voiceops.kubernetescontrol;

public class DeploymentAlexa {
	
	String name;
	String image;
	int replicas;
	
	public int getReplicas() {
		return replicas;
	}
	public void setReplicas(int replicas) {
		this.replicas = replicas;
	}
	public String getImage() {
		return image;
	}
	public void setImage(String image) {
		this.image = image;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getStatus() {
		if(replicas < 1) {
			return "not running";
		}
		return "running";
	}
}
