package it.unibo.arces.wot.sepa.engine.processing.tuning;

import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;

/**
 * An update response with Added and Removed triples and Metrics
 */
public class UpdateResponseARM extends UpdateResponseWithAR {

    private final MetricsTuningFile tf;

    
    public UpdateResponseARM(String ret, BindingsResults added, BindingsResults removed, MetricsTuningFile tf) {
		super(ret, added, removed);
		this.tf = tf;
	}


	public MetricsTuningFile getMetricTuningFile() {
        return tf;
    }

}
