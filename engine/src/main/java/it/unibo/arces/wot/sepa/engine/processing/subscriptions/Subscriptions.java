/* A monitor class for subscription management
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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPANotExistsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.response.Notification;
import it.unibo.arces.wot.sepa.engine.bean.SPUManagerBeans;
import it.unibo.arces.wot.sepa.engine.dependability.Dependability;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalSubscribeRequest;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalUpdateRequest;

/**
 * A monitor class for subscription management
 * 
 * 
 * @author Luca Roffia (luca.roffia@unibo.it)
 * @version 0.9.12
 */
public class Subscriptions {
	private static final Logger logger = LogManager.getLogger();

	// SID ==> Subscriber
	private static final HashMap<String, Subscriber> subscribers = new HashMap<String, Subscriber>();

	// SPUID ==> Subscribers
	private static final HashMap<String, HashSet<Subscriber>> handlers = new HashMap<String, HashSet<Subscriber>>();

	// Request ==> SPU
	private static final HashMap<InternalSubscribeRequest, SPU> requests = new HashMap<InternalSubscribeRequest, SPU>();

	public static SPU createSPU(InternalSubscribeRequest req, SPUManager manager) {
		try {
			//return new SPUNaive(req, manager);
			return new SPUTuned(req, manager);
		} catch (SEPAProtocolException e) {
			return null;
		}
	}
	
	public static Collection<SPU> filter(InternalUpdateRequest update) {
		// First level filter: RDF data set
		Collection<SPU> ret = new HashSet<>();
		Set<String> target = update.getRdfDataSet();
		
		for(InternalSubscribeRequest sub : requests.keySet()) {
			Set<String> context = sub.getRdfDataSet();
			for (String graph: target) {
				if (context.contains(graph)) {
					ret.add(requests.get(sub));
					break;
				}
			}
		}
		
		return ret;
	}
	
	public static boolean contains(InternalSubscribeRequest req) {
		return requests.containsKey(req);
	}

	public static void register(InternalSubscribeRequest req, SPU spu) {
		handlers.put(spu.getSPUID(), new HashSet<Subscriber>());
		requests.put(req, spu);

		SPUManagerBeans.setActiveSPUs(handlers.size());
		logger.debug("@subscribe SPU activated: " + spu.getSPUID() + " total (" + handlers.size() + ")");
	}

	public static SPU getSPU(InternalSubscribeRequest req) {
		return requests.get(req);
	}

	public static Subscriber addSubscriber(InternalSubscribeRequest req, SPU spu) {
		Subscriber sub = new Subscriber(spu, req);
		handlers.get(spu.getSPUID()).add(sub);
		subscribers.put(sub.getSID(), sub);

		SPUManagerBeans.addSubscriber();
		Dependability.onSubscribe(req.getGID(), sub.getSID());

		return sub;
	}
	
	public static Subscriber getSubscriber(String sid) throws SEPANotExistsException {
		Subscriber sub = subscribers.get(sid);
		
		if (sub == null) throw new SEPANotExistsException("Subscriber "+sid+" does not exists");
		return sub;
	}

	public static boolean removeSubscriber(Subscriber sub) throws SEPANotExistsException {
		String sid = sub.getSID();
		String spuid = sub.getSPU().getSPUID();

		if (!subscribers.containsKey(sid)) {
			logger.warn("@internalUnsubscribe SID not found: " + sid);
			throw new SEPANotExistsException("SID not found: " + sid);
		}

		SPUManagerBeans.removeSubscriber();

		logger.trace("@internalUnsubscribe SID: " + sid + " from SPU: " + spuid + " with active subscriptions: "
				+ subscribers.size());

		handlers.get(spuid).remove(sub);
		subscribers.remove(sid);

		// No more handlers: return true
		if (handlers.get(spuid).isEmpty()) {
			logger.debug("@internalUnsubscribe no more subscribers. Kill SPU: " + sub.getSPU().getSPUID());

			requests.remove(sub.getSPU().getSubscribe());
			handlers.remove(spuid);

			return true;
		}

		// More handlers
		return false;
	}

	public static void notifySubscribers(String spuid, Notification notify) {
		for (Subscriber client : handlers.get(spuid)) {
			// Dispatching events
			Notification event = new Notification(client.getSID(), notify.getARBindingsResults(),
					client.nextSequence());
			try {
				client.notifyEvent(event);
			} catch (SEPAProtocolException e) {
				logger.error(e.getMessage());
				if (logger.isTraceEnabled()) e.printStackTrace();
			}
		}
	}
}
