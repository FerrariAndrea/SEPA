/* This class implements a Semantic Processing Unit (SPU) of the Semantic Event Processing Architecture (SEPA) Engine
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

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import it.unibo.arces.wot.sepa.engine.processing.QueryProcessor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.protocol.SPARQL11Properties;
import it.unibo.arces.wot.sepa.commons.request.SubscribeRequest;

import it.unibo.arces.wot.sepa.commons.response.Notification;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;

import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;
import it.unibo.arces.wot.sepa.engine.bean.SubscribeProcessorBeans;
import it.unibo.arces.wot.sepa.engine.core.EventHandler;

/**
 * This class represents a Semantic Processing Unit (SPU)
 * 
 * 
 * @author Luca Roffia (luca.roffia@unibo.it)
 * @version 0.1
 */

// public abstract class SPU extends Observable implements Runnable {
abstract class SPU implements ISPU {
	private final Logger logger;

	// The URI of the subscription (i.e., sepa://spuid/UUID)
	private String uuid = null;

	// Update queue
	protected ConcurrentLinkedQueue<UpdateResponse> updateQueue = new ConcurrentLinkedQueue<UpdateResponse>();

	protected QueryProcessor queryProcessor;

	protected SubscribeRequest request;

	// Handler of notifications
	protected EventHandler handler;

	// Thread loop
	private boolean running = true;

	// Last bindings results
	protected BindingsResults lastBindings = null;

	// Notification result
	private Response notify;

	// List of processing SPU
	private SPUManager manager;

	public SPU(SubscribeRequest subscribe, SPARQL11Properties properties, EventHandler eventHandler,
			Semaphore endpointSemaphore, SPUManager manager) throws SEPAProtocolException {
		if (eventHandler == null)
			throw new SEPAProtocolException(new IllegalArgumentException("Subscribe event handler is null"));
		if (manager == null)
			throw new SEPAProtocolException(new IllegalArgumentException("SPU manager is null"));

		this.manager = manager;

		uuid = manager.generateSpuid();
		logger = LogManager.getLogger("SPU" + uuid);

		request = subscribe;
		handler = eventHandler;

		queryProcessor = new QueryProcessor(properties, endpointSemaphore);

		running = true;
	}

	public SubscribeRequest getSubscribe() {
		return request;
	}

	public abstract Response processInternal(UpdateResponse update, int timeout);

	@Override
	public BindingsResults getLastBindings() {
		return lastBindings;
	}

	@Override
	public void terminate() {
		synchronized (updateQueue) {
			running = false;
			updateQueue.notify();
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (!obj.getClass().equals(SPU.class))
			return false;
		return ((SPU) obj).getUUID().equals(getUUID());
	}

	@Override
	public String getUUID() {
		return uuid;
	}

	@Override
	public void process(UpdateResponse res) {
		synchronized (updateQueue) {
			updateQueue.offer(res);
			updateQueue.notify();
		}
	}

	@Override
	public void run() {
		while (running) {
			// Poll the request from the queue
			UpdateResponse updateResponse;
			while ((updateResponse = updateQueue.poll()) != null && running) {
				// Processing update
				logger.debug("* PROCESSING *");

				// Asynchronous processing and waiting for result
				notify = processInternal(updateResponse, SubscribeProcessorBeans.getSPUProcessingTimeout());

				// Notify event handler
				if (notify.isNotification())
					try {
						handler.notifyEvent((Notification) notify);
					} catch (IOException e) {
						logger.error("Failed to notify " + notify);
					}
				else
					logger.debug("Not a notification: " + notify);

				// Notify SPU manager
				logger.debug("Notify SPU manager. Running: " + running);
				manager.endProcessing(this);
			}

			// Wait next request...
			if (running)
				synchronized (updateQueue) {
					try {
						updateQueue.wait();
					} catch (InterruptedException e) {
						return;
					}
				}
		}
	}
}
