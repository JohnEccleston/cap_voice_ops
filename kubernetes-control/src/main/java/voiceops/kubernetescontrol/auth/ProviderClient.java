package voiceops.kubernetescontrol.auth;

import com.sun.jersey.api.client.Client;

public interface ProviderClient {
	
	public Client getClient();

}
