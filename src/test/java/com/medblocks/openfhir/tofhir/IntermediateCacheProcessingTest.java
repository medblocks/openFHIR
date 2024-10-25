package com.medblocks.openfhir.tofhir;

import com.medblocks.openfhir.util.FhirInstanceCreator;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class IntermediateCacheProcessingTest {


    @Test
    public void testIntermediateCachePopulation() {
        final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = new FhirInstanceCreator.InstantiateAndSetReturn();
        final HashMap<String, Object> cache = new HashMap<>();

        final CodeableConcept codeableConcept = new CodeableConcept();
        final Coding coding = new Coding();
        final StringType code = new StringType();
        hardcodedReturn.setReturning(codeableConcept);
        hardcodedReturn.setPath("category");
        hardcodedReturn.setInner(FhirInstanceCreator.InstantiateAndSetReturn.builder()
                .returning(coding)
                .path("coding")
                .inner(FhirInstanceCreator.InstantiateAndSetReturn.builder()
                        .returning(code)
                        .path("code")
                        .build())
                .build());

        new IntermediateCacheProcessing(new OpenFhirStringUtils()).populateIntermediateCache(hardcodedReturn,
                "123",
                cache,
                "",
                "",
                "",
                "");

        Assert.assertEquals(code.toString(), cache.get("123_.category.coding.code_").toString());
        Assert.assertEquals(coding.toString(), cache.get("123_.category.coding_").toString());
        Assert.assertEquals(codeableConcept.toString(), cache.get("123_.category_").toString());
    }
}
