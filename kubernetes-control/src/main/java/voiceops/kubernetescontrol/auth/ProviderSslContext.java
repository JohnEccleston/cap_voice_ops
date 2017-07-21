package voiceops.kubernetescontrol.auth;

import javax.net.ssl.SSLContext;

public interface ProviderSslContext {
	
	public SSLContext getSslContext();

}
