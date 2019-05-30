package it.unibo.arces.wot.sepa.apps.mqtt;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.security.SEPASecurityManager;
import it.unibo.arces.wot.sepa.commons.sparql.ARBindingsResults;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;
import it.unibo.arces.wot.sepa.pattern.Consumer;
import it.unibo.arces.wot.sepa.pattern.JSAP;

public class MqttTopicMapper extends Consumer {
	private static final Logger logger = LogManager.getLogger();

	// Topics mapping
	private HashMap<String, String> topic2observation = new HashMap<String, String>();

	public MqttTopicMapper(JSAP appProfile, SEPASecurityManager sm)
			throws SEPAProtocolException, SEPASecurityException {
		super(appProfile, "OBSERVATIONS_TOPICS", sm);
	}

	public ArrayList<String[]> map(String topic, String value) throws Exception {
		// Check if value can be parsed with regex
		// e.g. pepoli:6lowpan:network = | ID: NODO1 | Temperature: 24.60 | Humidity:
		// 35.40 | Pressure: 1016.46

		ArrayList<String[]> ret = new ArrayList<String[]>();

		String newValue = value;
		String newTopic = topic;

		// No need to parse the value
		if (!appProfile.getExtendedData().getAsJsonObject("mqtt-parsing").getAsJsonObject("topics").has(topic)) {
			ret.add(new String[] { topic, value });
			return ret;
		}

		// Get the pattern to parse value and ID
		String ptn = appProfile.getExtendedData().getAsJsonObject("mqtt-parsing").getAsJsonObject("topics")
				.get(topic).getAsString();
		JsonObject parsing = appProfile.getExtendedData().getAsJsonObject("mqtt-parsing").getAsJsonObject("patterns")
				.getAsJsonObject(ptn);
		String format = parsing.get("format").getAsString();

		switch (format) {
		case "plain":
			JsonArray arr = parsing.getAsJsonObject("value").getAsJsonArray("regex");

			for (JsonElement regex : arr) {
				Pattern p = Pattern.compile(regex.getAsString());
				Matcher m = p.matcher(value);

				if (m.matches()) {
					newTopic = topic.replace(":", "/");

					for (int i = 1; i < m.groupCount(); i++) {
						if (!m.group(i).equals(m.group("value")))
							newTopic += "/" + m.group(i);
					}

					newValue = m.group("value");
				}
			}

			String observation = topic2observation.get(newTopic);

			if (observation == null) {
				logger.warn("Topic NOT found: " + newTopic);
			} else
				ret.add(new String[] { observation, newValue });
			break;
		case "json":
			String idMember = parsing.get("id").getAsString();
			JsonObject valueObj = parsing.getAsJsonObject("value");

			String member = valueObj.get("member").getAsString();
			JsonArray patternArray = valueObj.get("regex").getAsJsonArray();

			JsonObject json = new JsonParser().parse(value).getAsJsonObject();

			// Topic
			String topicSuffix = json.get(idMember).getAsString();
			newTopic = topic + "/" + topicSuffix;

			// Payload
			String payload = null;
			if (valueObj.has("encoding")) {
				switch (valueObj.get("encoding").getAsString()) {
				case "base64":
					byte[] decoded = Base64.getDecoder().decode(json.get(member).getAsString());
					payload = new String(decoded);
					break;
				}
			} else
				payload = json.get(member).getAsString();

			for (JsonElement elem : patternArray) {
				String pattern = elem.getAsString();

				// Parsing
				Pattern p = Pattern.compile(pattern);
				Matcher m = p.matcher(payload);

				if (m.matches()) {
					try {
						newValue = m.group("value");

						observation = topic2observation.get(newTopic);

						if (observation == null) {
							logger.warn("Topic NOT found: " + newTopic);
						} else
							ret.add(new String[] { observation, newValue });
					} catch (IllegalArgumentException e) {
						int i = 1;

						try {
							while (m.group("value" + i) != null) {
								observation = topic2observation.get(newTopic + "/" + m.group("id" + i));

								if (observation == null) {
									logger.warn("Topic NOT found: " + newTopic + "/" + m.group("id" + i));
								} else {
									newValue = m.group("value" + i);
									ret.add(new String[] { observation, newValue });
								}
								i++;
							}
						} catch (IllegalArgumentException e1) {
							logger.debug("No more values: " + e1.getMessage());
						}
					}
				}
			}
			break;
		}

//		if (appProfile.getExtendedData().get("regexTopics").getAsJsonObject().has(topic)) {
//			JsonArray arr = appProfile.getExtendedData().get("regexTopics").getAsJsonObject().get(topic)
//					.getAsJsonArray();
//
//			for (JsonElement regex : arr) {
//				Pattern p = Pattern.compile(regex.getAsString());
//				Matcher m = p.matcher(value);
//
//				if (m.matches()) {
//					newTopic = topic.replace(":", "/");
//
//					for (int i = 1; i < m.groupCount(); i++) {
//						if (!m.group(i).equals(m.group("value")))
//							newTopic += "/" + m.group(i);
//					}
//
//					newValue = m.group("value");
//				}
//			}
//
//			String observation = topic2observation.get(newTopic);
//
//			if (observation == null) {
//				logger.warn("Topic NOT found: " + newTopic);
//			} else
//				ret.add(new String[] { observation, newValue });
//		}
//
//		// Check if value can be parsed with JSON
//		// e.g. {"moistureValue":3247, "nodeId":"device3",
//		// "timestamp":"2017-11-15T10:00:02.123028089Z"}
//
//		// Parsing
//		// application/1/device/1bc0f73caf72d467/rx
//		// {"applicationID":"1","applicationName":"Guaspari","deviceName":"EndNode1002","devEUI":"1bc0f73caf72d467","txInfo":{"frequency":915400000,"dr":4},"adr":false,"fCnt":4218,"fPort":1,"data":"U3wxOTA1MTMyMjQwfEl8MTAwMnxIMXwzMzl8SDJ8MzMxfEgzfDMzMQ=="}
//		// application/1/device/754366e02ff23515/rx
//		// {"applicationID":"1","applicationName":"Guaspari","deviceName":"EndNode1001","devEUI":"754366e02ff23515","txInfo":{"frequency":916000000,"dr":4},"adr":false,"fCnt":3454,"fPort":1,"data":"U3wxOTA2MTMwOTUwfEl8MTAwMXxIMXwzMzd8SDJ8MzMzfEgzfDMzMg=="}
//
//		/*
//		 * 
//		 * "jsonTopics": { "ground/lora/moisture": { "id": "nodeId", "value" :
//		 * "moistureValue", "parsing": { "pattern" : "(?<value>\\w+)" } },
//		 * "application/1/device/1bc0f73caf72d467/rx": { "id": "deviceName", "value" :
//		 * "data", "parsing": { "encoding" : "base64", "pattern" :
//		 * ".\\w+.[|].\\w+.[|].\\w+.[|].\\w+[|].(?<id1>\\w+).[|].(?<value1>\\w+).[|].(?<id2>\\w+).[|].(?<value2>\\w+).[|].(?<id3>\\w+).[|].(?<value3>\\w+)"
//		 * } } },
//		 */
//
//		else if (appProfile.getExtendedData().get("jsonTopics").getAsJsonObject().has(topic)) {
//			String idMember = appProfile.getExtendedData().getAsJsonObject("jsonTopics").getAsJsonObject(topic)
//					.get("id").getAsString();
//			JsonObject valueObj = appProfile.getExtendedData().getAsJsonObject("jsonTopics").getAsJsonObject(topic)
//					.getAsJsonObject("value");
//
//			String member = valueObj.get("member").getAsString();
//			JsonArray patternArray = valueObj.get("regex").getAsJsonArray();
//
//			JsonObject json = new JsonParser().parse(value).getAsJsonObject();
//
//			// Topic
//			String topicSuffix = json.get(idMember).getAsString();
//			newTopic = topic + "/" + topicSuffix;
//
//			// Payload
//			String payload = null;
//			if (valueObj.has("encoding")) {
//				switch (valueObj.get("encoding").getAsString()) {
//				case "base64":
//					byte[] decoded = Base64.getDecoder().decode(json.get(member).getAsString());
//					payload = new String(decoded);
//					break;
//				}
//			} else
//				payload = json.get(member).getAsString();
//
//			for (JsonElement elem : patternArray) {
//				String pattern = elem.getAsString();
//
//				// Parsing
//				Pattern p = Pattern.compile(pattern);
//				Matcher m = p.matcher(payload);
//
//				if (m.matches()) {
//					try {
//						newValue = m.group("value");
//
//						String observation = topic2observation.get(newTopic);
//
//						if (observation == null) {
//							logger.warn("Topic NOT found: " + newTopic);
//						} else
//							ret.add(new String[] { observation, newValue });
//					} catch (IllegalArgumentException e) {
//						int i = 1;
//
//						try {
//							while (m.group("value" + i) != null) {
//								String observation = topic2observation.get(newTopic + "/" + m.group("id" + i));
//
//								if (observation == null) {
//									logger.warn("Topic NOT found: " + newTopic + "/" + m.group("id" + i));
//								} else {
//									newValue = m.group("value" + i);
//									ret.add(new String[] { observation, newValue });
//								}
//								i++;
//							}
//						} catch (IllegalArgumentException e1) {
//							logger.debug("No more values: " + e1.getMessage());
//						}
//					}
//				}
//			}
//		} else {
//			String observation = topic2observation.get(newTopic);
//			ret.add(new String[] { observation, newValue });
//		}

		return ret;

	}

	@Override
	public void onResults(ARBindingsResults results) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAddedResults(BindingsResults results) {
		for (Bindings bindings : results.getBindings()) {
			topic2observation.put(bindings.getValue("topic"), bindings.getValue("observation"));
		}
	}

	@Override
	public void onRemovedResults(BindingsResults results) {
		for (Bindings bindings : results.getBindings()) {
			topic2observation.remove(bindings.getValue("topic"));
		}
	}

	@Override
	public void onBrokenConnection() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onError(ErrorResponse errorResponse) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSubscribe(String spuid, String alias) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUnsubscribe(String spuid) {
		// TODO Auto-generated method stub

	}

}
