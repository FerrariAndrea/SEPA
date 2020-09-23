/* This class implements a Tuned implementation of a SPU
 * 
 * Author: Luca Roffia (luca.roffia@unibo.it)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package it.unibo.arces.wot.sepa.engine.processing.subscriptions;

import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProcessingException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.Notification;
import it.unibo.arces.wot.sepa.commons.response.QueryResponse;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.SubscribeResponse;
import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;
import it.unibo.arces.wot.sepa.commons.sparql.ARBindingsResults;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;
import it.unibo.arces.wot.sepa.engine.processing.tuning.MetricsTuningFile;
import it.unibo.arces.wot.sepa.engine.processing.tuning.UpdateResponseARM;
import it.unibo.arces.wot.sepa.engine.processing.tuning.UpdateResponseWithAR;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalSubscribeRequest;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalUpdateRequest;

import java.util.ArrayList;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;

/*
 *SPUTuned è realizzata a partire da SPUNaive,
 *fornisce un tuning del sistema tramite il filtraggio delle sottoscrizioni a partire dai grafi (SPUNaive)
 *sfrutta la modifica del UpdatePorcessor grazie al quale restituisce un UpdateResponseWithAR,
 *per ottenere le triple "added" e "removed"
 */
class SPUTuned extends SPU {
	private final Logger logger;
	private MetricsTuningFile metrics;
	
	public SPUTuned(InternalSubscribeRequest subscribe, SPUManager manager) throws SEPAProtocolException {
		super(subscribe, manager);

		this.spuid = "sepa://spu/tuned/" + UUID.randomUUID();

		logger = LogManager.getLogger("SPUTuned" + getSPUID());
		logger.debug("SPU: " + this.getSPUID() + " request: " + subscribe);
	}

	@Override
	public Response init() throws SEPASecurityException {
		logger.debug("PROCESS " + subscribe);

		// Process the SPARQL query
		Response ret = manager.processQuery(subscribe);

		if (ret.isError()) {
			logger.error("Not initialized");
			return ret;
		}

		lastBindings = ((QueryResponse) ret).getBindingsResults();

		logger.debug("First results: " + lastBindings.toString());

		return new SubscribeResponse(getSPUID(), subscribe.getAlias(), lastBindings);
	}

	@Override
	public void preUpdateInternalProcessing(InternalUpdateRequest req) throws SEPAProcessingException {

	}

	@Override
	public Notification postUpdateInternalProcessing(UpdateResponse res) throws SEPAProcessingException {
		boolean tuned_on = true;
	
		logger.trace("* PROCESSING *" + subscribe); 
		Response ret = null;

		// Query the SPARQL processing service
		try {
			ret = manager.processQuery(subscribe);
		} catch (SEPASecurityException e) {
			if (logger.isTraceEnabled()) e.printStackTrace();
			throw new SEPAProcessingException(e.getMessage());
		}

		if (ret.isError()) {
			throw new SEPAProcessingException(ret.toString());
		}
	
		
		ARBindingsResults ris=null;
		
		//eseguo tutti e due i tipi di ricerca per le added e le removed
		metrics=((UpdateResponseARM)res).getMetricTuningFile();
		

		long start = System.nanoTime();		
		ris =execTuned((UpdateResponseARM)res,ret);
		long end = System.nanoTime();		
		metrics.setTimeSPUTuned(end-start);
		if(ris!=null) {
			metrics.setTunedAdded(ris.getAddedBindings().toJson().toString());
			metrics.setTunedRemoved(ris.getRemovedBindings().toJson().toString());
		}
	
		
		start = System.nanoTime();	
		ris =execNormal(res,ret);
		if(ris!=null) {
			metrics.setAdded(ris.getAddedBindings().toJson().toString());
			metrics.setRemoved(ris.getRemovedBindings().toJson().toString());
		}
		end = System.nanoTime();
		metrics.setTimeSPUNormal(end-start);

		
		/*
		if(res instanceof UpdateResponseARM && tuned_on) {//TUNED
			ris =execTuned((UpdateResponseARM)res,ret);	
		}else {//NOT TUNED
			 ris =execNormal(res,ret);	
		}
		*/
		 
		if(ris!=null) {
			metrics.write();
			return new Notification(getSPUID(),ris);
		}
		return null;
		
	}
	
	private ARBindingsResults execNormal(UpdateResponse res,Response ret) {

		// Current and previous bindings
		BindingsResults results = ((QueryResponse) ret).getBindingsResults();
		BindingsResults currentBindings = new BindingsResults(results);

		// Initialize the results with the current bindings
		BindingsResults added = new BindingsResults(results.getVariables(), null);
		BindingsResults removed = new BindingsResults(results.getVariables(), null);
		
		// Create empty bindings if null
		if (lastBindings == null)
			lastBindings = new BindingsResults(null, null);

		logger.trace("Current bindings: " + currentBindings);
		logger.trace("Last bindings: " + lastBindings);
		

		long start = System.nanoTime();
		//lastBindings è tipo una cache locale delle triple che il cliente ha?
		for (Bindings solution : lastBindings.getBindings()) {
			if (!results.contains(solution) && !solution.isEmpty())
				removed.add(solution);
			else
				results.remove(solution);
		}
		long stop = System.nanoTime();
		logger.trace("Removed bindings: " + removed + " found in " + (stop - start) + " ns");

		// Find added bindings
		start = System.nanoTime();
		for (Bindings solution : results.getBindings()) {
			if (!lastBindings.contains(solution) && !solution.isEmpty())
				added.add(solution);
		}		
		stop = System.nanoTime();
		logger.trace("Added bindings: " + added + " found in " + (stop - start) + " ns");
		// Update the last bindings with the current ones
		lastBindings = currentBindings;
		System.out.println("Not tuned added: "+added.toJson().toString());
		System.out.println("Not tuned removed: "+removed.toJson().toString());
		// Send notification (or end processing indication)
		
		if (!added.isEmpty() || !removed.isEmpty()) {
			return  new ARBindingsResults(added, removed);
		}else {
			return null;
		}
	}
	
	private ARBindingsResults execTuned(UpdateResponseARM res,Response ret ) {
		
		

		// Current and previous bindings
		BindingsResults results = ((QueryResponse) ret).getBindingsResults();
		BindingsResults currentBindings = new BindingsResults(results);

		// Initialize the results with the current bindings
		BindingsResults added = new BindingsResults(results.getVariables(), null);
		BindingsResults removed = new BindingsResults(results.getVariables(), null);
		
		// Create empty bindings if null
		if (lastBindings == null)
			lastBindings = new BindingsResults(null, null);
		
		BindingsResults solutionForRemoved = ((UpdateResponseWithAR)res).getRemoved();
		BindingsResults solutionForAdded = ((UpdateResponseWithAR)res).getAdded();

		long start = System.nanoTime();
		//check ADDED from UpdateResponseWithAR	
	
		for (Bindings solution : lastBindings.getBindings()) {
			if (solutionForRemoved.contains(solution) && !solution.isEmpty()){
				removed.add(solution);
				solutionForRemoved.remove(solution);
			}
		}
		
		long stop = System.nanoTime();
		logger.trace("Removed bindings: " + removed + " found in " + (stop - start) + " ns");
		
		start = System.nanoTime();
		//check REMOVED from UpdateResponseWithAR		
		for (Bindings solution : solutionForAdded.getBindings()) {
			if (!lastBindings.contains(solution) && !solution.isEmpty()) {
				try {
					added.add(convertBindings(solution,results.getVariables()));
				} catch (SEPABindingsException e) {
					System.out.println("WIP-------- ERROR on convertBindings:  "+e.getMessage());
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
				//added.add(solution);
		}	
		
		stop = System.nanoTime();
		logger.trace("Added bindings: " + added + " found in " + (stop - start) + " ns");
		
		
		System.out.println("Tuned added: "+added.toJson().toString());
		System.out.println("Tuned removed: "+removed.toJson().toString());
		if (!solutionForAdded.isEmpty() || !solutionForRemoved.isEmpty()) {
			return new ARBindingsResults(added, removed);
		}else {
			return null;
		}
		
	}
	private Bindings convertBindings(Bindings original,ArrayList<String> variables) throws SEPABindingsException {
		Bindings ris =new Bindings();
		if(variables.get(0)!=null ) {
			ris.addBinding(variables.get(0), original.getRDFTerm("s"));
		}
		if(variables.get(1)!=null ) {
			ris.addBinding(variables.get(1), original.getRDFTerm("p"));
		}
		if(variables.get(2)!=null ) {
			ris.addBinding(variables.get(2), original.getRDFTerm("o"));
		}			
		return ris; 
	}
}
