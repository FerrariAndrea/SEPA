package it.unibo.arces.wot.sepa.engine.protocol.http.handler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;

import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.request.Request;
import it.unibo.arces.wot.sepa.commons.request.UpdateRequest;
import it.unibo.arces.wot.sepa.engine.protocol.http.HttpUtilities;
import it.unibo.arces.wot.sepa.engine.scheduling.Scheduler;

/**
 * <a href="https://www.w3.org/TR/sparql11-protocol/"> SPARQL 1.1 Protocol</a>
 *
 * *
 * 
 * <pre>
 *                               HTTP Method   Query String Parameters           Request Content Type                Request Message Body
 *----------------------------------------------------------------------------------------------------------------------------------------
 * update via URL-encoded POST|   POST         None                              application/x-www-form-urlencoded   URL-encoded, ampersand-separated query parameters.
 *                            |                                                                                     update (exactly 1)
 *                            |                                                                                     using-graph-uri (0 or more)
 *                            |                                                                                     using-named-graph-uri (0 or more)
 *----------------------------------------------------------------------------------------------------------------------------------------																													
 * update via POST directly   |    POST       using-graph-uri (0 or more)       application/sparql-update           Unencoded SPARQL update request string
 *                                            using-named-graph-uri (0 or more)
 * </pre>
 * 
 */
public class UpdateHandler extends SPARQL11Handler {
	protected static final Logger logger = LogManager.getLogger("UpdateHandler");

	public UpdateHandler(Scheduler scheduler) throws IllegalArgumentException {
		super(scheduler);
	}

	@Override
	protected Request parse(HttpAsyncExchange exchange) {
		if (!exchange.getRequest().getRequestLine().getMethod().toUpperCase().equals("POST")) {
			logger.error("UNSUPPORTED METHOD: " + exchange.getRequest().getRequestLine().getMethod().toUpperCase());
			throw new SPARQL11ProtocolException( HttpStatus.SC_BAD_REQUEST,
					"Unsupported method: " + exchange.getRequest().getRequestLine().getMethod().toUpperCase());
		}

		String body = null;
		HttpEntity entity = ((HttpEntityEnclosingRequest) exchange.getRequest()).getEntity();
		try {
			body = EntityUtils.toString(entity, Charset.forName("UTF-8"));
		} catch (ParseException | IOException e) {
			body = e.getLocalizedMessage();
		}

		Header[] headers = exchange.getRequest().getHeaders("Content-Type");
		if (headers.length != 1) {
			logger.error("Content-Type is missing");
			throw new SPARQL11ProtocolException(HttpStatus.SC_BAD_REQUEST, "Content-Type is missing");
		}

		if (headers[0].getValue().equals("application/sparql-update")) {
			logger.debug("update via POST directly");
			
			return new UpdateRequest(body);
		} else if (headers[0].getValue().equals("application/x-www-form-urlencoded")) {
			try {
				Map<String,String> params = HttpUtilities.splitQuery(body);
				
				if (params.get("update") != null) return new UpdateRequest(params.get("update"));		
			} catch (UnsupportedEncodingException e1) {
				logger.error(e1.getMessage());
				throw new SPARQL11ProtocolException( HttpStatus.SC_BAD_REQUEST, e1.getMessage());
			}
		}

		logger.error("Request MUST conform to SPARQL 1.1 Protocol (https://www.w3.org/TR/sparql11-protocol/)");
		throw new SPARQL11ProtocolException(HttpStatus.SC_BAD_REQUEST,
				"Request MUST conform to SPARQL 1.1 Protocol (https://www.w3.org/TR/sparql11-protocol/)");
	}
}
