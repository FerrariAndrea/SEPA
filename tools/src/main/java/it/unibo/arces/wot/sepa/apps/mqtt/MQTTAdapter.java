package it.unibo.arces.wot.sepa.apps.mqtt;

import java.io.IOException;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.protocol.SSLSecurityManager;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermLiteral;
import it.unibo.arces.wot.sepa.pattern.ApplicationProfile;
import it.unibo.arces.wot.sepa.pattern.Producer;

public class MQTTAdapter extends Producer implements MqttCallback {
	private static final Logger logger = LogManager.getLogger("MQTT-SEPA-Adapter");

	private MqttClient mqttClient;
	private String[] topicsFilter = null;
	private String serverURI = null;

	public static void main(String[] args) throws IOException, SEPAProtocolException, SEPASecurityException, SEPAPropertiesException {
		MQTTAdapter adapter = new MQTTAdapter();
		adapter.start();
		
		System.out.println("Press any key to exit...");
		System.in.read();
		
		adapter.stop();
	}
	
	public MQTTAdapter() throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException {
		super(new ApplicationProfile("mqtt.jsap"), "MQTT_MESSAGE");
	}

	@Override
	public void connectionLost(Throwable arg0) {
		logger.error("Connection lost: " + arg0.getMessage());

		while (true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e1) {

			}

			logger.warn("Connecting...");
			try {
				mqttClient.connect();
			} catch (MqttException e) {
				logger.fatal("Failed to connect: " + e.getMessage());
				continue;
			}

			logger.warn("Subscribing...");
			try {
				mqttClient.subscribe(topicsFilter);
			} catch (MqttException e) {
				logger.fatal("Failed to subscribe " + e.getMessage());
				continue;
			}

			break;
		}

		logger.info("Connected and subscribed!");

	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken arg0) {

	}

	@Override
	public void messageArrived(String topic, MqttMessage value) throws Exception {
		logger.info(topic + " " + value.toString());

		Bindings bindings = new Bindings();
		bindings.addBinding("topic", new RDFTermLiteral(topic));
		bindings.addBinding("value", new RDFTermLiteral(value.toString()));
		bindings.addBinding("broker", new RDFTermLiteral(serverURI));
		update(bindings);
	}

	public boolean start() {
		// MQTT
		JsonObject mqtt = getApplicationProfile().getExtendedData().get("mqtt").getAsJsonObject();

		String url = mqtt.get("url").getAsString();
		int port = mqtt.get("port").getAsInt();
		JsonArray topics = mqtt.get("topics").getAsJsonArray();

		topicsFilter = new String[topics.size()];
		int i = 0;
		for (JsonElement topic : topics) {
			topicsFilter[i] = topic.getAsString();
			i++;
		}

		boolean sslEnabled = false;
		if (mqtt.get("ssl") != null)
			sslEnabled = mqtt.get("ssl").getAsBoolean();

		if (sslEnabled) {
			serverURI = "ssl://" + url + ":" + String.format("%d", port);
		} else {
			serverURI = "tcp://" + url + ":" + String.format("%d", port);
		}

		// Create client
		logger.info("Creating MQTT client...");
		String clientID = MqttClient.generateClientId();
		logger.info("Client ID: " + clientID);
		logger.info("Server URI: " + serverURI);
		try {
			mqttClient = new MqttClient(serverURI, clientID);
		} catch (MqttException e) {
			logger.error(e.getMessage());
			return false;
		}

		// Connect
		logger.info("Connecting...");
		MqttConnectOptions options = new MqttConnectOptions();
		if (sslEnabled) {
			SSLSecurityManager sm;
			try {
				sm = new SSLSecurityManager("TLSv1","sepa.jks", "sepa2017", "sepa2017");
			} catch (UnrecoverableKeyException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException
					| CertificateException | IOException e) {
				logger.error(e.getMessage());
				return false;
			}
			logger.info("Set SSL security");
			try {
				options.setSocketFactory(sm.getSSLContext().getSocketFactory());
			} catch (KeyManagementException | NoSuchAlgorithmException e) {
				logger.error(e.getMessage());
				return false;
			}
		}
		try {
			mqttClient.connect(options);
		} catch (MqttException e) {
			logger.error(e.getMessage());
		}

		// Subscribe
		mqttClient.setCallback(this);
		logger.info("Subscribing...");
		try {
			mqttClient.subscribe(topicsFilter);
		} catch (MqttException e) {
			logger.error(e.getMessage());
			return false;
		}

		String printTopics = "Topic filter ";
		for (String s : topicsFilter) {
			printTopics += s + " ";
		}
		logger.info("MQTT client " + clientID + " subscribed to " + serverURI + printTopics);
		
		return true;
	}

	public void stop() {
		try {
			if (topicsFilter != null)
				mqttClient.unsubscribe(topicsFilter);
		} catch (MqttException e1) {
			logger.error("Failed to unsubscribe " + e1.getMessage());
		}

		try {
			mqttClient.disconnect();
		} catch (MqttException e) {
			logger.error("Failed to disconnect " + e.getMessage());
		}

	}
}
