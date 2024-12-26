package com.medblocks.openfhir;

import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class OpenFhirEngineTest {

    @Test
    public void testGetTemplateIdFromOpenEhr() throws IOException {
        final OpenFhirEngine openFhirEngine = new OpenFhirEngine(null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new Gson());
        final String flatJson = IOUtils.toString(getClass().getResourceAsStream("/growth_chart/growth_chart_flat.json"));
        final String templateIdFromOpenEhr = openFhirEngine.getTemplateIdFromOpenEhr(flatJson);
        Assert.assertEquals("growth_chart", templateIdFromOpenEhr);

        final String composition = IOUtils.toString(getClass().getResourceAsStream("/growth_chart/growth_chart_composition.json"));
        final String templateIdFromCompositionOpenEhr = openFhirEngine.getTemplateIdFromOpenEhr(composition);
        Assert.assertEquals("Growth chart", templateIdFromCompositionOpenEhr);
    }
}