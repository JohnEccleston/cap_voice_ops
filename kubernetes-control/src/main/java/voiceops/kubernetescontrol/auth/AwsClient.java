package voiceops.kubernetescontrol.auth;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public class AwsClient implements ProviderClient {
	
	private static final Logger log = LoggerFactory.getLogger(AwsClient.class);
	private Regions region = Regions.EU_WEST_1 ;

	//@Override
	public SSLContext getSslContext() {	
		SSLContext sslContext = null;
		try {
				AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
						 	    			.withRegion(this.region)
						     			.build();
		    	S3Object object = s3Client.getObject(new GetObjectRequest("k8sdemo-store", "k8sdemo.capademy.com/pki/issued/ca/6434398114042835278235624689.crt"));
		    	
		    	InputStream stream = object.getObjectContent();
		    	
		    	CertificateFactory cf = CertificateFactory.getInstance("X.509");
		    	//maybe other import
		    	X509Certificate caCert = (X509Certificate)cf.generateCertificate(stream);
		
		    	TrustManagerFactory tmf = TrustManagerFactory
		    	    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
		    	KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		    	ks.load(null); // You don't need the KeyStore instance to come from a file.
		    	ks.setCertificateEntry("caCert", caCert);
		
		    	tmf.init(ks);
		
		    	sslContext = SSLContext.getInstance("TLS");
		    	sslContext.init(null, tmf.getTrustManagers(), null);
		    }
			catch(Exception ex) {
				log.error("Failed to get SSLContext for aws kubernetes call");
			
			}
		return sslContext;
	}
	
	public void setRegion(Regions region) {
		this.region = region;
	}

	@Override
	public Client getClient() {
		Client client = null;
		
		try {
			AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
 	    			.withRegion(this.region)
     			.build();
			S3Object object = s3Client.getObject(new GetObjectRequest("k8sdemo-store", "k8sdemo.capademy.com/pki/issued/ca/6434398114042835278235624689.crt"));
			
			InputStream stream = object.getObjectContent();
			
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			//maybe other import
			X509Certificate caCert = (X509Certificate)cf.generateCertificate(stream);
			
			TrustManagerFactory tmf = TrustManagerFactory
			.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(null); // You don't need the KeyStore instance to come from a file.
			ks.setCertificateEntry("caCert", caCert);
			
			tmf.init(ks);
			
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, tmf.getTrustManagers(), null);
			
			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = new HostnameVerifier() {
					public boolean verify(String hostname, SSLSession session) {
				 return true;
			 }
			};
			
			ClientConfig config = new DefaultClientConfig();
			config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(allHostsValid, sslContext));
			client = Client.create(config);
		}
		catch(Exception ex) {
			log.error("Failed to get Jerssy client for aws kubernetes call");
		}
		return client;
	}
}
