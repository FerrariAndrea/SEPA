package it.unibo.arces.wot.sepa.engine.processing.tuning;

import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;

/**
 * An update response with Added and Removed triples.
 */
public class UpdateResponseTuningFile extends UpdateResponse {

    private final TuningFile tf;

    public UpdateResponseTuningFile(String ret,TuningFile tf) {
        super(ret);
        this.tf = tf;
    }

    public TuningFile getTuningFile() {
        return tf;
    }

}
