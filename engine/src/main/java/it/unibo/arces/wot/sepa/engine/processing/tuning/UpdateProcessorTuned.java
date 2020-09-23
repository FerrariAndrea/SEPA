package it.unibo.arces.wot.sepa.engine.processing.tuning;

import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;

import com.google.gson.JsonObject;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.protocol.SPARQL11Properties;
import it.unibo.arces.wot.sepa.commons.request.QueryRequest;
import it.unibo.arces.wot.sepa.commons.request.UpdateRequest;
import it.unibo.arces.wot.sepa.commons.response.QueryResponse;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;
import it.unibo.arces.wot.sepa.engine.bean.UpdateProcessorBeans;
import it.unibo.arces.wot.sepa.engine.processing.UpdateProcessor;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalUpdateRequest;
import it.unibo.arces.wot.sepa.timing.Timings;

public class UpdateProcessorTuned extends UpdateProcessor  {

	public UpdateProcessorTuned(SPARQL11Properties properties) throws SEPAProtocolException {
		super(properties);
		// TODO Auto-generated constructor stub
	}
	
	
	@Override
	public Response process(InternalUpdateRequest req) throws SEPASecurityException {
		//------------------------ADDED REMOVED------------------PREPARE---------inizio
		//controllare che il timeout sia quello giusto
		long tunedStart = Timings.getTime();
		BindingsResults added_removed[]=null;
		try {
			added_removed = PrepareAddedRemovedFilter((int)UpdateProcessorBeans.getTimeout() ,req);
			//System.out.println("added.size: "+added_removed[0].size());
			//System.out.println("removed.size: "+added_removed[1].size());
		} catch (SEPASecurityException | SEPABindingsException e2) {
			// TODO Auto-generated catch block
			System.out.print("!!!!!!!!!!!!!!!!!!!!!!!!!WIP Errore PREPARE!!!!!!!!!!!!!!!!!!!!!!");
			e2.printStackTrace();
		}
		//------------------------ADDED REMOVED------------------PREPARE---------fine
		long tunedEnd = Timings.getTime();
		
	
		// ENDPOINT UPDATE
		UpdateRequest request = new UpdateRequest(properties.getUpdateMethod(), properties.getProtocolScheme(),
				properties.getHost(), properties.getPort(), properties.getUpdatePath(), req.getSparql(),
				req.getDefaultGraphUri(), req.getNamedGraphUri(), req.getBasicAuthorizationHeader(),
				UpdateProcessorBeans.getTimeout(),0);
		logger.trace(request);

		Response ret;
		int n = 0;
		do {
			long start = Timings.getTime();
			ret = endpoint.update(request);
			long stop = Timings.getTime();			
			UpdateProcessorBeans.timings(start, stop);
			
			//------------------------ADDED REMOVED------------------APPLY---------inizio
			if(ret.isUpdateResponse() && added_removed != null) {
				try {
					//attenzioe non so se UpdateProcessorBeans.getTimeout() sia l'arg giusto
					ret=ApplyAddedRemovedFilter(
							added_removed[0],
							added_removed[1],
							new MetricsTuningFile(tunedEnd-tunedStart),
							ret);					
				} catch (SEPASecurityException | SEPABindingsException e1) {
					System.out.print("!!!!!!!!!!!!!!!!!!!!!!!!!WIP Errore APPLY!!!!!!!!!!!!!!!!!!!!!!");
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			//------------------------ADDED REMOVED------------------APPLY---------fine
			
			logger.trace("Response: " + ret.toString());
			Timings.log("UPDATE_PROCESSING_TIME", start, stop);
			
			n++;
			
			if (ret.isTimeoutError()) {
				UpdateProcessorBeans.timedOutRequest();
				logger.error("*TIMEOUT* ("+n+"/"+UpdateProcessorBeans.getTimeoutNRetry()+") "+req);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					logger.warn("Failed to sleep...");
				}
			}
		} while(ret.isTimeoutError() && n < UpdateProcessorBeans.getTimeoutNRetry());
		
		if (ret.isTimeoutError()) {
			logger.error("*** REQUEST ABORTED *** "+request);
			UpdateProcessorBeans.abortedRequest();
		}
		
		
		return ret;
		
	}
	
	
	//-------------------------------------------ADDED REMOVED
		private boolean isBindingPresent(int timeout, Bindings bindings,InternalUpdateRequest req ) throws SEPABindingsException, SEPASecurityException {
		
			Triple t = bindingToTriple(bindings);

			Query ask = new Query();
			ask.setQueryAskType();

			ElementTriplesBlock block = new ElementTriplesBlock();
			block.addTriple(t);
			ask.setQueryPattern(block);

			String askq = ask.serialize();
			logger.debug(askq);
			/*
			 * ----------BK
			QueryRequest askquery = new QueryRequest(properties.getQueryMethod(),
	                properties.getDefaultProtocolScheme(), properties.getDefaultHost(), properties.getDefaultPort(),
	                properties.getDefaultQueryPath(),askq, timeout, null,
	                null, null);
	       */
			QueryRequest askquery = new QueryRequest(
					properties.getQueryMethod(),
					properties.getProtocolScheme(),
					properties.getHost(),
					properties.getPort(),
					properties.getQueryPath(),
					askq,
					req.getDefaultGraphUri(),
					req.getNamedGraphUri(),
					req.getBasicAuthorizationHeader(),
					timeout,
					0);

			System.out.println("isBindingPresent.askq------>"+askq);
			BindingsResults isPresentResult = ((QueryResponse) endpoint.query(askquery)).getBindingsResults();
			return isPresentResult.toJson().get("boolean").getAsBoolean();
		}

		private Triple bindingToTriple(Bindings bindings) throws SEPABindingsException{
			String subject = bindings.getValue("s");
			String predicate = bindings.getValue("p");
			String object = bindings.getValue("o");			
			
			Node s = bindings.isBNode("s") ? NodeFactory.createBlankNode(subject) : NodeFactory.createURI(subject);
			Node p = bindings.isBNode("p") ? NodeFactory.createBlankNode(predicate) : NodeFactory.createURI(predicate);

			Node o = null;
			if(!bindings.isBNode("o")){
				o = bindings.isURI("o") ? NodeFactory.createURI(object) : NodeFactory.createLiteral(object);
			}else{
				o = NodeFactory.createBlankNode(object);
			}

			return new Triple(s,p,o);
		}

		private BindingsResults getTriples(int timeout,String sparql,Set<String> defaultGraph,Set<String> namedGraph, String auth ) throws SEPASecurityException {
			BindingsResults removed;
			/*-----BK
			 * 	QueryRequest cons1 = new QueryRequest(properties.getQueryMethod(),
					properties.getDefaultProtocolScheme(), properties.getDefaultHost(), properties.getDefaultPort(),
					properties.getDefaultQueryPath(), dc, timeout, null,
					null, null);
			 */
		
			
			QueryRequest cons1 = new QueryRequest(
					properties.getQueryMethod(),
					properties.getProtocolScheme(),
					properties.getHost(),
					properties.getPort(),
					properties.getQueryPath(),
					sparql,
					defaultGraph,
					namedGraph,
					auth,
					timeout,
					0);
			
			cons1.setTimeout(timeout);
			logger.debug(cons1.toString());
			removed = ((QueryResponse) endpoint.query(cons1)).getBindingsResults();
			logger.debug(removed);
			return removed;
		}
		
		private BindingsResults[] PrepareAddedRemovedFilter(int timeout ,InternalUpdateRequest req ) throws SEPASecurityException, SEPABindingsException {
			long start = Timings.getTime();
			SPARQLAnalyzer sa = new SPARQLAnalyzer(req.getSparql());
			UpdateConstruct constructs = sa.getConstruct();

			
			BindingsResults added =  new BindingsResults(new JsonObject());
			BindingsResults removed =  new BindingsResults(new JsonObject());

			String dc = constructs.getDeleteConstruct();
			//System.out.println("STRING--->"+dc);
			if (dc.length() > 0) {
				String auth=req.getClientAuthorization().getBasicAuthorizationHeader();
				removed = getTriples(timeout,dc, req.getDefaultGraphUri(), req.getNamedGraphUri(),auth);
			}

			String ac = constructs.getInsertConstruct();		
			if (ac.length() > 0) {
				String auth=req.getClientAuthorization().getBasicAuthorizationHeader();
				added = getTriples(timeout,ac, req.getDefaultGraphUri(), req.getNamedGraphUri(),auth);
				
			}

			//System.out.println("added--->"+ added.toJson().toString());
			//System.out.println("removed--->"+ removed.toJson().toString());
			
			for(Bindings bindings : added.getBindings()){
				boolean isPresent = isBindingPresent(timeout, bindings,req);
				if(isPresent){
					added.getBindings().remove(bindings);
				}
			}

			for(Bindings bindings : removed.getBindings()){
				boolean isPresent = isBindingPresent(timeout, bindings,req);
				if(!isPresent){
					removed.getBindings().remove(bindings);
				}
			}
			long stop = System.currentTimeMillis();
			logger.debug("* ADDED REMOVED PROCESSING ("+(stop-start)+" ms) *");
			//ProcessorBeans.updateTimings(start, stop); //BK
			UpdateProcessorBeans.timings(start, stop);
			BindingsResults added_removed[] = new BindingsResults[2];
			added_removed[0]= added;
			added_removed[1]= removed;
			return added_removed;
		}
		
		private Response ApplyAddedRemovedFilter(BindingsResults added,BindingsResults removed,MetricsTuningFile metrics,Response ret) throws SEPASecurityException, SEPABindingsException {			

			return new UpdateResponseARM(ret.toString(),added,removed,metrics) ;
		}
		private Response ApplyAddedRemovedFilter(BindingsResults added,BindingsResults removed,Response ret) throws SEPASecurityException, SEPABindingsException {
					
			/*
			// UPDATE the endpoint
			Response ret;
			UpdateRequest request = new UpdateRequest(req.getToken(), properties.getUpdateMethod(),
					properties.getDefaultProtocolScheme(), properties.getDefaultHost(), properties.getDefaultPort(),
					properties.getUpdatePath(), req.getSparql(), req.getTimeout(), req.getUsingGraphUri(),
					req.getUsingNamedGraphUri(), req.getAuthorizationHeader());
			logger.trace(request);
			ret = endpoint.update(request);


			stop = Timings.getTime();
			logger.trace("Response: " + ret.toString());
			Timings.log("UPDATE_PROCESSING_TIME", start, stop);
			
			//ProcessorBeans.updateTimings(start, stop); //BK
			UpdateProcessorBeans.timings(start, stop);
			*/

			
			//da controllare che ret.toString() sia valido come argomento per UpdateResponseWithAR
			
			//il controllo su ret.isUpdateResponse()  lo faccio a monte, nella funzione che chiama questa
			//ret = ret.isUpdateResponse() ? new UpdateResponseWithAR(ret.toString(),added,removed) : ret;
			
			return new UpdateResponseWithAR(ret.toString(),added,removed) ;
		}

}
