package it.unibo.arces.wot.sepa.apps.dtn.test;

import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.security.SEPASecurityManager;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermLiteral;
import it.unibo.arces.wot.sepa.pattern.DTNGenericClient;
import it.unibo.arces.wot.sepa.pattern.DTNProducer;
import it.unibo.arces.wot.sepa.pattern.JSAP;

public class MainTest {

	public static void main(String[] args) throws Exception {
		JSAP appProfile = new JSAP("chat.jsap");
		String updateID = "REGISTER_USER";
		String queryID = "USERS";
		SEPASecurityManager sm = null;
		
		DTNProducer producer = new DTNProducer(appProfile, updateID, sm);
		producer.setUpdateBindingValue("userName", new RDFTermLiteral("DTNtest"));
		Response r = producer.update(6000);
		
		System.out.println(r);
		
		producer.close();
		
		DTNGenericClient client = new DTNGenericClient(appProfile);
		r = client.query(queryID, null, -1);
		
		System.out.println(r);
		
		client.close();
		
		//DTNProtocol protocol = new DTNProtocol();
		//Response response = protocol.query(new QueryRequest(HTTPMethod.GET, "ipn", "5", 150, "", appProfile.getSPARQLQuery(updateID), "", "", "", 60));
		
		//System.out.println(response);
		//protocol.close();
	}

}