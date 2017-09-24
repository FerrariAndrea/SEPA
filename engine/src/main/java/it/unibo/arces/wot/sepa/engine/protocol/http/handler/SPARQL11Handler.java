package it.unibo.arces.wot.sepa.engine.protocol.http.handler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Observable;
//import java.util.HashMap;
//import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Observer;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;

import org.apache.http.nio.protocol.BasicAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;

import org.apache.http.protocol.HttpContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

//import it.unibo.arces.wot.sepa.commons.request.QueryRequest;
import it.unibo.arces.wot.sepa.commons.request.Request;
//import it.unibo.arces.wot.sepa.commons.response.Response;
//import it.unibo.arces.wot.sepa.commons.request.UpdateRequest;
import it.unibo.arces.wot.sepa.commons.response.QueryResponse;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.engine.bean.HTTPHandlerBeans;
import it.unibo.arces.wot.sepa.engine.bean.SEPABeans;
//import it.unibo.arces.wot.sepa.engine.core.ResponseHandler;
import it.unibo.arces.wot.sepa.engine.protocol.http.HttpUtilities;
import it.unibo.arces.wot.sepa.engine.scheduling.Scheduler;
import it.unibo.arces.wot.sepa.engine.security.CORSManager;

public abstract class SPARQL11Handler
		implements HttpAsyncRequestHandler<HttpRequest>, SPARQL11HandlerMBean, Observer {
	private static final Logger logger = LogManager.getLogger("SPARQL11Handler");

	private Scheduler scheduler;

	protected HTTPHandlerBeans jmx = new HTTPHandlerBeans();

	private HashMap<Integer,HttpAsyncExchange> responders = new HashMap<Integer,HttpAsyncExchange>();
	
	public SPARQL11Handler(Scheduler scheduler, long timeout) throws IllegalArgumentException {

		if (scheduler == null)
			throw new IllegalArgumentException("One or more arguments are null");

		this.scheduler = scheduler;

		// JMX
		SEPABeans.registerMBean("SEPA:type=" + this.getClass().getSimpleName(), this);
	}

	protected boolean validate(HttpRequest request) {
		// TODO Validate SPARQL 1.1 Query
		return true;
	}

	protected boolean authorize(HttpRequest request) {
		// TODO Always authorized
		return true;
	}

	protected boolean corsHandling(HttpAsyncExchange exchange) {
		if (!CORSManager.processCORSRequest(exchange)) {
			HttpUtilities.sendFailureResponse(exchange, HttpStatus.SC_UNAUTHORIZED, "CORS origin not allowed");
			return false;
		}

		if (CORSManager.isPreFlightRequest(exchange)) {
			HttpUtilities.sendResponse(exchange, HttpStatus.SC_NO_CONTENT, "");
			return false;
		}

		return true;
	}

	protected abstract Request parse(HttpAsyncExchange exchange);

	@Override
	public HttpAsyncRequestConsumer<HttpRequest> processRequest(HttpRequest request, HttpContext context)
			throws HttpException, IOException {
		// Buffer request content in memory for simplicity
		return new BasicAsyncRequestConsumer();
	}

	@Override
	public void handle(HttpRequest request, HttpAsyncExchange httpExchange, HttpContext context)
			throws HttpException, IOException {
		
		new Thread() {
			public void run() {
				jmx.newRequest();

				// CORS
				if (!corsHandling(httpExchange)) {
					jmx.corsFailed();
					return;
				}

				// Parsing SPARQL 1.1 request and attach a token
				Request sepaRequest = parse(httpExchange);
				

				// Parsing failed
				if (sepaRequest == null) {
					logger.error("Parsing failed: " + request);
					HttpUtilities.sendFailureResponse(httpExchange, HttpStatus.SC_BAD_REQUEST, "Parsing failed: " + request);
					jmx.parsingFailed();
					return;
				}

				// Validate
				if (!validate(request)) {
					logger.error("Validation failed SPARQL: " + sepaRequest.getSPARQL());
					HttpUtilities.sendFailureResponse(httpExchange, HttpStatus.SC_BAD_REQUEST,
							"Validation failed SPARQL: " + sepaRequest.getSPARQL());
					jmx.validatingFailed();
					return;
				}

				// Authorize
				if (!authorize(request)) {
					logger.error("Authorization failed SPARQL: " + sepaRequest.getSPARQL());
					HttpUtilities.sendFailureResponse(httpExchange, HttpStatus.SC_UNAUTHORIZED,
							"Authorization failed SPARQL: " + sepaRequest.getSPARQL());
					jmx.authorizingFailed();
					return;
				}

				// Schedule a new request
				Integer requestToken = scheduler.schedule(sepaRequest,new SPARQL11ResponseHandler(httpExchange));	
		
				if (requestToken == -1) {
					logger.warn("No more tokens");
					HttpUtilities.sendFailureResponse(httpExchange, HttpStatus.SC_NOT_ACCEPTABLE, "No more tokens");
					return;
				}

				responders.put(requestToken, httpExchange);
				logger.info("Request #" + requestToken);	
			}
		}.start();
		
	}

	@Override
	public void update(Observable o, Object arg) {
		Response ret = (Response) arg;
		
		HttpAsyncExchange responder = responders.get(ret.getToken());
		
		if (responder != null) {
			JsonObject json = new JsonParser().parse(ret.toString()).getAsJsonObject();

			if (ret.getClass().equals(QueryResponse.class))
				HttpUtilities.sendResponse(responder, json.get("code").getAsInt(), json.get("body").toString());
			else
				HttpUtilities.sendResponse(responder, json.get("code").getAsInt(), json.toString());	
			
			responders.remove(ret.getToken());
		}
		
	}

	@Override
	public long getRequests() {
		return jmx.getRequests();
	}


	@Override
	public void reset() {
		jmx.reset();

	}
	
	@Override
	public float getHandlingTime_ms() {
		return jmx.getHandlingTime_ms();
	}

	@Override
	public float getHandlingMinTime_ms() {
		return jmx.getHandlingMinTime_ms();
	}

	@Override
	public float getHandlingAvgTime_ms() {
		return jmx.getHandlingAvgTime_ms();
	}

	@Override
	public float getHandlingMaxTime_ms() {
		return jmx.getHandlingMaxTime_ms();
	}

	@Override
	public long getErrors_Timeout() {
		return jmx.getErrors_Timeout();
	}

	@Override
	public long getErrors_CORSFailed() {
		return jmx.getErrors_CORSFailed();
	}

	@Override
	public long getErrors_ParsingFailed() {
		return jmx.getErrors_ParsingFailed();
	}

	@Override
	public long getErrors_ValidatingFailed() {
		return jmx.getErrors_ValidatingFailed();
	}
}
