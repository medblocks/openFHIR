package com.medblocks.openfhir.util;

import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class FhirConnectValidatorTest {

    @Test
    void validateAgainstModelSchema() {
        final Yaml yaml = OpenFhirTestUtility.getYaml();
        final FhirConnectModel fhirConnectModel = yaml.loadAs(
                getClass().getResourceAsStream("/kds_new/projects/org.highmed/KDS/diagnose/KDS_problem_diagnose.yml"),
                FhirConnectModel.class);
        final List<String> strings = new FhirConnectValidator().validateAgainstModelSchema(fhirConnectModel);
        Assert.assertTrue(strings.isEmpty());
    }
}