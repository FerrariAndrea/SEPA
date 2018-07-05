/*  This class implements the processing of the requests coming form the scheduler
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

package it.unibo.arces.wot.sepa.engine.processing;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.protocol.SPARQL11Properties;
import it.unibo.arces.wot.sepa.commons.request.QueryRequest;
import it.unibo.arces.wot.sepa.commons.request.Request;
import it.unibo.arces.wot.sepa.commons.request.SubscribeRequest;
import it.unibo.arces.wot.sepa.commons.request.UnsubscribeRequest;
import it.unibo.arces.wot.sepa.commons.request.UpdateRequest;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;
import it.unibo.arces.wot.sepa.engine.bean.ProcessorBeans;
import it.unibo.arces.wot.sepa.engine.bean.SEPABeans;
import it.unibo.arces.wot.sepa.engine.core.EngineProperties;
import it.unibo.arces.wot.sepa.engine.core.EventHandler;
import it.unibo.arces.wot.sepa.engine.scheduling.ScheduledRequest;
import it.unibo.arces.wot.sepa.engine.scheduling.SchedulerRequestResponseQueue;

import org.apache.logging.log4j.LogManager;

public class Processor extends Thread implements ProcessorMBean {
	private final Logger logger = LogManager.getLogger();

	// Processors
	private final UpdateProcessor updateProcessor;
	private final QueryProcessor queryProcessor;
	private final SubscribeProcessor subscribeProcessor;
	
	// Scheduler queue
	private SchedulerRequestResponseQueue queue;

	// Concurrent endpoint limit
	private Semaphore endpointSemaphore = null;

	// Update queue
	private LinkedBlockingQueue<UpdateRequest> updateQueue = new LinkedBlockingQueue<UpdateRequest>();
	
	public Processor(SPARQL11Properties endpointProperties, EngineProperties properties,
			SchedulerRequestResponseQueue queue) throws IllegalArgumentException, SEPAProtocolException {
		if (queue == null) {
			logger.error("Queue is null");
			throw new IllegalArgumentException("Queue is null");
		}
		this.queue = queue;

		// Number of maximum concurrent requests (supported by the endpoint)
		int max = properties.getMaxConcurrentRequests();
		if (max > 0)
			endpointSemaphore = new Semaphore(max, true);

		// Update processor
		updateProcessor = new UpdateProcessor(endpointProperties, endpointSemaphore);

		// Query processor
		queryProcessor = new QueryProcessor(endpointProperties, endpointSemaphore);

		// SPU manager
		subscribeProcessor = new SubscribeProcessor(endpointProperties, properties, endpointSemaphore);

		// JMX
		SEPABeans.registerMBean("SEPA:type=" + this.getClass().getSimpleName(), this);
		ProcessorBeans.setEndpoint(endpointProperties);
		ProcessorBeans.setQueryTimeout(properties.getQueryTimeout());
		ProcessorBeans.setUpdateTimeout(properties.getUpdateTimeout());
		
		// Main thread for FIFO scheduling of updates 
		Thread updateScheduler = new Thread() {
			public void run() {
				while(true) {
					UpdateRequest request;
					try {
						request = updateQueue.take();
					} catch (InterruptedException e) {
						return;
					}

					// Process update request
					request.setTimeout(ProcessorBeans.getUpdateTimeout());
					Response ret = updateProcessor.process(request);

					// Notify update result
					queue.addResponse(ret);

					// Subscription processing
					if (ret.isUpdateResponse()) {
						subscribeProcessor.process((UpdateResponse) ret);
					}	
				}
			}
		};
		updateScheduler.setName("SEPA-Update-Scheduler");
		updateScheduler.start();
	}

	@Override
	public void run() {
		while (true) {
			// WAIT NEW REQUEST
			ScheduledRequest scheduledRequest;
			try {
				scheduledRequest = queue.waitRequest();
			} catch (InterruptedException e1) {
				return;
			}

			
			Request request = scheduledRequest.getRequest();
			if (request.isUpdateRequest()) {
				logger.debug("Update request #" + request.getToken());
				logger.trace(request);

				// Put the request into the FIFO queue
				updateQueue.offer((UpdateRequest) request);
			} else if (request.isQueryRequest()) {
				logger.debug("Query request #" + request.getToken());
				logger.trace(request);

				request.setTimeout(ProcessorBeans.getQueryTimeout());
				
				Thread queryProcessing = new Thread() {
					public void run() {
						Response ret = queryProcessor.process((QueryRequest) request);
						queue.addResponse(ret);
					}
				};
				queryProcessing.setName("SEPA-Query-Processing-Thread-" + request.getToken());
				queryProcessing.start();
			} else if (request.isSubscribeRequest()) {
				logger.debug("Subscribe request #" + request.getToken());
				logger.trace(request);

				Response ret = subscribeProcessor.subscribe((SubscribeRequest) request,
						(EventHandler) scheduledRequest.getHandler());

				queue.addResponse(ret);
			} else if (request.isUnsubscribeRequest()) {
				logger.info("Unsubscribe request #" + request.getToken());
				logger.debug(request);

				Response ret = subscribeProcessor.unsubscribe((UnsubscribeRequest) request);

				queue.addResponse(ret);
			}
		}
	}

	@Override
	public synchronized void start() {
		super.start();
		subscribeProcessor.start();
	}

	@Override
	public void interrupt() {
		super.interrupt();
		subscribeProcessor.stop();
	}

	@Override
	public void reset() {
		ProcessorBeans.reset();
	}

	@Override
	public float getTimings_UpdateTime_ms() {
		return ProcessorBeans.getUpdateTime_ms();
	}

	@Override
	public float getTimings_QueryTime_ms() {
		return ProcessorBeans.getQueryTime_ms();
	}

	@Override
	public long getProcessedRequests() {
		return ProcessorBeans.getProcessedRequests();
	}

	@Override
	public long getProcessedQueryRequests() {
		return ProcessorBeans.getProcessedQueryRequests();
	}

	@Override
	public long getProcessedUpdateRequests() {
		return ProcessorBeans.getProcessedUpdateRequests();
	}

	@Override
	public float getTimings_UpdateTime_Min_ms() {
		return ProcessorBeans.getTimings_UpdateTime_Min_ms();
	}

	@Override
	public float getTimings_UpdateTime_Average_ms() {
		return ProcessorBeans.getTimings_UpdateTime_Average_ms();
	}

	@Override
	public float getTimings_UpdateTime_Max_ms() {
		return ProcessorBeans.getTimings_UpdateTime_Max_ms();
	}

	@Override
	public float getTimings_QueryTime_Min_ms() {
		return ProcessorBeans.getTimings_QueryTime_Min_ms();
	}

	@Override
	public float getTimings_QueryTime_Average_ms() {
		return ProcessorBeans.getTimings_QueryTime_Average_ms();
	}

	@Override
	public float getTimings_QueryTime_Max_ms() {
		return ProcessorBeans.getTimings_QueryTime_Max_ms();
	}

	@Override
	public int getUpdateTimeout() {
		return ProcessorBeans.getUpdateTimeout();
	}

	@Override
	public int getQueryTimeout() {
		return ProcessorBeans.getQueryTimeout();
	}

	@Override
	public void setUpdateTimeout(int t) {
		ProcessorBeans.setUpdateTimeout(t);
	}

	@Override
	public void setQueryTimeout(int t) {
		ProcessorBeans.setQueryTimeout(t);
	}
}
