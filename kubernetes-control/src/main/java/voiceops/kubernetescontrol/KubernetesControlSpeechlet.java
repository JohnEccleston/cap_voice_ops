/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package voiceops.kubernetescontrol;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;

import javax.net.ssl.*;

import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpConnection;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import sun.net.www.http.HttpClient;

import io.fabric8.kubernetes.api.model.PodList;

/**
 * This sample shows how to create a simple speechlet for handling speechlet requests.
 */
public class KubernetesControlSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(KubernetesControlSpeechlet.class);
    
    //String accountToken = getEnvOrDefault("K8S_ACCOUNT_TOKEN", "/var/run/secrets/kubernetes.io/serviceaccount/token");

   //@Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        // any initialization logic goes here
    }

    //@Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        return getWelcomeResponse();
    }

    //@Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        log.info("full request = ", request.toString());

        Intent intent = request.getIntent();
        //Slot slot = intent.getSlot("name");
        
        
        
        String intentName = (intent != null) ? intent.getName() : null;

        if ("CreateCluster".equals(intentName)) {
        	callKubernetesApi();
            return getCreateClusterResponse();
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            return getHelpResponse();
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    //@Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        // any cleanup logic goes here
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getWelcomeResponse() {
        String speechText = "Welcome to Voice Ops, I can launch a cluster and do much more";

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("VoiceOps");
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getCreateClusterResponse() {
        String speechText = "Cluster created, if you believe that, you'd believe anything.";

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("VoiceOps");
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);
        
       

        return SpeechletResponse.newTellResponse(speech, card);
    }

    /**
     * Creates a {@code SpeechletResponse} for the help intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHelpResponse() {
    	String speechOutput =
                "With Voice Ops, you can create"
                        + " a kubernetes cluster."
                        + " For example, you could say create,"
                        + " me a cluster?";

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("VoiceOps");
        card.setContent(speechOutput);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechOutput);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }
    
    private void callKubernetesApi() {
    	//String host = "ec2-34-250-241-111.eu-west-1.compute.amazonaws.com";
    	String host = "api.k8sdemo.capademy.com";
    	String port = "443";
//    	String path = "/api";
      String path = "/api/v1/namespaces/kube-system/pods";
    	
    	log.info(SDKGlobalConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toString());
    	
    	try{
    		//String token = getServiceAccountToken(accountToken);
    		
	    	AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
	    			.withRegion(Regions.EU_WEST_1)
	    			.build();
//	    	S3Object object = s3Client.getObject(new GetObjectRequest("k8sdemo-store", "k8sdemo.capademy.com/pki/issued/ca/6434398114042835278235624689.crt"));
	    	S3Object object = s3Client.getObject(new GetObjectRequest("k8sdemo-store", "access/creds.yml"));

//	    	InputStream stream = object.getObjectContent();
//
//	    	CertificateFactory cf = CertificateFactory.getInstance("X.509");
//	    	//maybe other import
//	    	X509Certificate caCert = (X509Certificate)cf.generateCertificate(stream);
//
//	    	TrustManagerFactory tmf = TrustManagerFactory
//	    	    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
//	    	KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
//	    	ks.load(null); // You don't need the KeyStore instance to come from a file.
//	    	ks.setCertificateEntry("caCert", caCert);
//
//	    	tmf.init(ks);
//
//	    	SSLContext sslContext = SSLContext.getInstance("TLS");
//	    	sslContext.init(null, tmf.getTrustManagers(), null);
    	
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        HashMap<Object, Object> yamlParsers = (HashMap<Object, Object>) yaml.load(object.getObjectContent());
//        log.info(Arrays.toString(yamlParsers.entrySet().toArray()));
        final String user = yamlParsers.get("user").toString();
        final String password = yamlParsers.get("password").toString();


	    	String PROTO = "https://";
			  URL url = new URL(PROTO + host + /* ":" + port + */ path);
			  log.info("Getting endpoints from " + url);

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
          }
          public void checkClientTrusted(X509Certificate[] certs, String authType) {
          }
          public void checkServerTrusted(X509Certificate[] certs, String authType) {
          }
        }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
          public boolean verify(String hostname, SSLSession session) {
            return true;
          }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        Authenticator.setDefault (new Authenticator() {
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, password.toCharArray());
          }
        });

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();


//            HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
//            conn.setSSLSocketFactory(sslContext.getSocketFactory());
//            conn.addRequestProperty("Authorization", "Bearer " + "BEARER_TOKEN_GOES_HERE");
//

//            Gson g = new Gson();
//            log.info("response = " + conn.getInputStream().toString());
//            final BufferedReader reader =
//              new BufferedReader(new InputStreamReader(conn.getInputStream()));
//            g.fromJson(reader, PodList.class);
//            PodList podlist = g.fromJson(conn.getResponseMessage(), PodList.class);

            log.info("response = " + conn.getResponseMessage());
            log.info("content = " + conn.getContent());
            log.info("content string = " + conn.getContent().toString());

        StringWriter writer = new StringWriter();
        IOUtils.copy(conn.getInputStream(), writer, StandardCharsets.UTF_8);
        String theString = writer.toString();
        log.info("the string = " + theString);

            InputStream ins = conn.getInputStream();
            InputStreamReader isr = new InputStreamReader(ins);
            BufferedReader in = new BufferedReader(isr);

            String inputLine;

            while ((inputLine = in.readLine()) != null)
            {
              System.out.println(inputLine);
            }

            in.close();
//            stream.close();
            isr.close();
            ins.close();
            
            
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private static String getEnvOrDefault(String var, String def) {
        String val = System.getenv(var);
        if (val == null) {
            val = def;
        }
        return val;
    }

    private static String getServiceAccountToken(String file)  {
        try {
            return new String(Files.readAllBytes(Paths.get(file)));
        } catch (IOException e) {
            log.error("unable to load service account token" + file);
            throw new RuntimeException("Unable to load services account token " + file);
        }
    }
}
