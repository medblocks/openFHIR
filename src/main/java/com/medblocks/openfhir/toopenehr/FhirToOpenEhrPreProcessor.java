package com.medblocks.openfhir.toopenehr;

public class FhirToOpenEhrPreProcessor {
    /**
     * needs to loop through Bundle entries and make sure all resource references are added as
     * Reference.resource!! else engine wont map properly
     */
    public void preProcess() {

    }
}
