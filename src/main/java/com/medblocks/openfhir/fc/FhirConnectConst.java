package com.medblocks.openfhir.fc;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Utility class containing all constants defined by the FHIR Connect specification
 */
public class FhirConnectConst {

    public static final String FHIR_RESOURCE_FC = "$resource";
    public static final String FHIR_ROOT_FC = "$fhirRoot";
    public static final String THIS = "$this";
    public static final String OPENEHR_ARCHETYPE_FC = "$archetype";
    public static final String OPENEHR_COMPOSITION_FC = "$composition";
    public static final String OPENEHR_CONTEXT_FC = "$openEhrContext";
    public static final String OPENEHR_ROOT_FC = "$openehrRoot";
    public static final String EHR = "$ehr";

    public static final String OPENEHR_TYPE_NONE = "NONE";
    public static final String OPENEHR_TYPE_CLUSTER = "CLUSTER";
    public static final String OPENEHR_TYPE_MEDIA = "MEDIA";
    public static final String OPENEHR_TYPE_DOSAGE = "DOSAGE";
    public static final String REFERENCE = "$reference";
    public static final String DV_MULTIMEDIA = "DV_MULTIMEDIA";
    public static final String DV_QUANTITY = "DV_QUANTITY";
    public static final String DV_PARTY_IDENTIFIED = "PARTY_IDENTIFIED";
    public static final String DV_PARTY_PROXY = "PARTY_PROXY";

    public static final String DV_ORDINAL = "DV_ORDINAL";
    public static final String DV_PROPORTION = "DV_PROPORTION";
    public static final String DV_COUNT = "DV_COUNT";
    public static final String DV_DATE_TIME = "DV_DATE_TIME";
    public static final String DV_TIME = "DV_TIME";
    public static final String DV_DATE = "DV_DATE";
    public static final String DV_CODED_TEXT = "DV_CODED_TEXT";
    public static final String DV_ELEMENT = "ELEMENT";
    public static final String CODE_PHRASE = "CODE_PHRASE";
    public static final String DV_TEXT = "DV_TEXT";
    public static final String OPENEHR_CODE = "code";
    public static final String OPENEHR_TERMINOLOGY = "terminology";
    public static final String DV_BOOL = "BOOLEAN";
    public static final String IDENTIFIER = "IDENTIFIER";
    public static final String DV_IDENTIFIER = "DV_IDENTIFIER";
    public static final String UNIDIRECTIONAL_TOFHIR = "openEHR->fhir";
    public static final String UNIDIRECTIONAL_TOOPENEHR = "fhir->openehr";
    public static final String CONDITION_OPERATOR_ONE_OF = "one of";
    public static final String CONDITION_OPERATOR_NOT_OF = "not of";
    public static final String CONDITION_OPERATOR_EMPTY = "empty";
    public static final String CONDITION_OPERATOR_TYPE = "type";
    public static final String CONDITION_OPERATOR_NOT_EMPTY = "not empty";

    public static final List<String> OPENEHR_INVALID_PATH_RM_TYPES = Arrays.asList("HISTORY", "EVENT", "ITEM_TREE",
                                                                                   "POINT_EVENT", "POINT_INTERVAL");
    public static final List<String> OPENEHR_UNDERSCORABLES = Arrays.asList("health_care_facility",
                                                                            "end_time"); // certain keywords are reserved and as such need to be prefixed with an underscore when constructing flat paths so ehrbase (un)marshaller can handle it
    public static final Set<String> OPENEHR_CONSISTENT_SET = Set.of(DV_TEXT, DV_CODED_TEXT, "FEEDER_AUDIT");
}
