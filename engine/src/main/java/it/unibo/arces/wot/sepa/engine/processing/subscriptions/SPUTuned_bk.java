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
import it.unibo.arces.wot.sepa.engine.processing.tuning.UpdateResponseWithAR;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalSubscribeRequest;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalUpdateRequest;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;

/*
 *SPUTuned è realizzata a partire da SPUNaive,
 *fornisce un tuning del sistema tramite il filtraggio delle sottoscrizioni a partire dai grafi (SPUNaive)
 *sfrutta la modifica del UpdatePorcessor grazie al quale restituisce un UpdateResponseWithAR,
 *per ottenere le triple "added" e "removed"
 */
class SPUTuned_bk extends SPU {
	private final Logger logger;

	public SPUTuned_bk(InternalSubscribeRequest subscribe, SPUManager manager) throws SEPAProtocolException {
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

		// Find removed bindings
		System.out.println("IS UpdateResponseWithAR: " + (res instanceof UpdateResponseWithAR));//ok 
		long start = System.nanoTime();		
		if(res instanceof UpdateResponseWithAR) {//TUNED
			BindingsResults solutionForRemoved = ((UpdateResponseWithAR)res).getRemoved();
			BindingsResults solutionForAdded = ((UpdateResponseWithAR)res).getAdded();
			//check ADDED from UpdateResponseWithAR	
		
			for (Bindings solution : lastBindings.getBindings()) {
				if (!solutionForRemoved.contains(solution) && !solution.isEmpty())
					removed.add(solution);
				else
					solutionForRemoved.remove(solution);
			}
			
			long stop = System.nanoTime();
			logger.trace("Removed bindings: " + removed + " found in " + (stop - start) + " ns");
			
			//check REMOVED from UpdateResponseWithAR
			
			for (Bindings solution : solutionForAdded.getBindings()) {
				if (!lastBindings.contains(solution) && !solution.isEmpty())
					added.add(solution);
			}			
			stop = System.nanoTime();
			logger.trace("Added bindings: " + added + " found in " + (stop - start) + " ns");
			
		
		}else {//NOT TUNED
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
		}
		
		// Update the last bindings with the current ones
		lastBindings = currentBindings;
		// Send notification (or end processing indication)
		if (!added.isEmpty() || !removed.isEmpty())
			return new Notification(getSPUID(), new ARBindingsResults(added, removed));

		return null;
	}
}