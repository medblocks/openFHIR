package com.medblocks.openfhir.util;

import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_CLUSTER;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medblocks.openfhir.fc.FhirConnectConst;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Class used for populating openEHR flat path Composition
 */
@Slf4j
@Component
public class OpenEhrPopulator {

    // Constants for common suffixes
    private static final String SUFFIX_TERMINOLOGY = "|terminology";
    private static final String SUFFIX_CODE = "|code";
    private static final String SUFFIX_VALUE = "|value";
    private static final String SUFFIX_SIZE = "|size";
    private static final String SUFFIX_UNIT = "|unit";
    private static final String SUFFIX_MAGNITUDE = "|magnitude";
    private static final String SUFFIX_ORDINAL = "|ordinal";
    private static final String SUFFIX_ID = "|id";

    private final OpenFhirMapperUtils openFhirMapperUtils;

    @Autowired
    public OpenEhrPopulator(OpenFhirMapperUtils openFhirMapperUtils) {
        this.openFhirMapperUtils = openFhirMapperUtils;
    }


    /**
     * Adds extracted value to the openEHR flat path composition represented with the 'constructingFlat' variable
     *
     * @param openEhrPath path that should be used in the flat path composition
     * @param extractedValue value as extracted from a FHIR object
     * @param openEhrType openEHR type as defined in the fhir connect model mapping
     * @param constructingFlat composition in a flat path format that's being constructed
     */
    public void setFhirPathValue(String openEhrPath, final Base extractedValue, final String openEhrType,
                                 final JsonObject constructingFlat) {
        if (openEhrType == null) {
            addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);
            return;
        }
        if (OPENEHR_TYPE_NONE.equals(openEhrType) || OPENEHR_TYPE_CLUSTER.equals(openEhrType)) {
            log.warn("Adding nothing on path {} as type is marked as NONE / CLUSTER", openEhrPath);
            return;
        }
        if (extractedValue == null) {
            log.warn("Extracted value is null");
            return;
        }
        if (openEhrPath.contains(RECURRING_SYNTAX)) {
            // still has recurring syntax due to the fact some recurring elements were not aligned or simply couldn't have been
            // in this case just set all to 0th
            openEhrPath = openEhrPath.replace(RECURRING_SYNTAX, ":0");
        }

        switch (openEhrType) {
            case FhirConnectConst.DV_MULTIMEDIA:
                handleDvMultimedia(openEhrPath, extractedValue, constructingFlat);
            case FhirConnectConst.DV_QUANTITY:
                final boolean addedQuantity = handleDvQuantity(openEhrPath, extractedValue, constructingFlat);
                if (addedQuantity) {
                    return;
                }
            case FhirConnectConst.DV_ORDINAL:
                boolean addedOrdinal = handleDvOrdinal(openEhrPath, extractedValue, constructingFlat);
                if (addedOrdinal) {
                    return;
                }
            case FhirConnectConst.DV_PROPORTION:
                boolean addedProportion = handleDvProportion(openEhrPath, extractedValue, constructingFlat);
                if (addedProportion) {
                    return;
                }
            case FhirConnectConst.DV_COUNT:
                final boolean addedCount = handleDvCount(openEhrPath, extractedValue, constructingFlat);
                if (addedCount) {
                    return;
                }
            case FhirConnectConst.DV_DATE_TIME:
                final boolean addedDateTime = handleDvDateTime(openEhrPath, extractedValue, constructingFlat);
                if (addedDateTime) {
                    return;
                }
            case FhirConnectConst.DV_DATE:
                final boolean addedDate = handleDvDate(openEhrPath, extractedValue, constructingFlat);
                if (addedDate) {
                    return;
                }
            case FhirConnectConst.DV_TIME:
                final boolean addedTime = handleDvTime(openEhrPath, extractedValue, constructingFlat);
                if (addedTime) {
                    return;
                }
            case FhirConnectConst.DV_CODED_TEXT:
                final boolean addedCodeText = handleDvCodedText(openEhrPath, extractedValue, constructingFlat);
                if (addedCodeText) {
                    return;
                }
            case FhirConnectConst.DV_IDENTIFIER:
                final boolean addedIdentifier = handleIdentifier(openEhrPath, extractedValue, constructingFlat);
                if (addedIdentifier) {
                    return;
                }
            case FhirConnectConst.CODE_PHRASE:
                final boolean addedCode = handleCodePhrase(openEhrPath, extractedValue, constructingFlat, openEhrType);
                if (addedCode) {
                    return;
                }
            case FhirConnectConst.DV_TEXT:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);
                return;
            case FhirConnectConst.DV_BOOL:
                final boolean addedBool = handleDvBool(openEhrPath, extractedValue, constructingFlat);
                if (addedBool) {
                    return;
                }
            case FhirConnectConst.DV_PARTY_IDENTIFIED:
                final boolean addedPartyIdentified = handlePartyIdentifier(openEhrPath, extractedValue,
                                                                           constructingFlat);
                if (addedPartyIdentified) {
                    return;
                }
            case FhirConnectConst.DV_PARTY_PROXY:
                final boolean addedPartyProxy = handlePartyProxy(openEhrPath, extractedValue, constructingFlat);
                if (addedPartyProxy) {
                    return;
                }
            default:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);

        }
    }

    private void handleDvMultimedia(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Attachment attachment) {
            int size = (attachment.getSize() == 0 && attachment.getData() != null) ? attachment.getData().length
                    : attachment.getSize();
            addToConstructingFlat(path + SUFFIX_SIZE, String.valueOf(size), flat);
            addToConstructingFlat(path + SUFFIX_TERMINOLOGY, attachment.getContentType(), flat);
            if (StringUtils.isNotEmpty(attachment.getUrl())) {
                addToConstructingFlat(path + "|url", attachment.getUrl(), flat);
            } else {
                addToConstructingFlat(path + "|data", Base64.getEncoder().encodeToString(attachment.getData()), flat);
            }
        } else {
            log.warn("openEhrType is MULTIMEDIA but extracted value is not Attachment; is {}", value.getClass());
        }
    }

    private boolean handleDvQuantity(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlatDouble(path + SUFFIX_MAGNITUDE, quantity.getValue().doubleValue(), flat);
            }
            addToConstructingFlat(path + SUFFIX_UNIT, quantity.getUnit(), flat);
            return true;
        } else if (value instanceof Ratio ratio) {
            setFhirPathValue(path, ratio.getNumerator(), FhirConnectConst.DV_QUANTITY, flat);
            return true;
        } else {
            log.warn("openEhrType is DV_QUANTITY but extracted value is not Quantity and not Ratio; is {}",
                     value.getClass());
        }
        return false;
    }

    private boolean handleDvOrdinal(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlat(path + SUFFIX_ORDINAL, quantity.getValue().toPlainString(), flat);
            }
            addToConstructingFlat(path + SUFFIX_VALUE, quantity.getUnit(), flat);
            addToConstructingFlat(path + SUFFIX_CODE, quantity.getCode(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_ORDINAL but extracted value is not Quantity; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvProportion(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if ("%".equals(quantity.getCode())) {
                addToConstructingFlatDouble(path + SUFFIX_MAGNITUDE, 100.0, flat);
            }
            if (quantity.getValue() != null) {
                addToConstructingFlatDouble(path + SUFFIX_MAGNITUDE, quantity.getValue().doubleValue(), flat);
            }
            addToConstructingFlat(path + "|type", "2", flat); // hardcoded?
            return true;
        } else {
            log.warn("openEhrType is DV_PROPORTION but extracted value is not Quantity; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvCount(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlatInteger(path, quantity.getValue().intValueExact(), flat);
            }
            return true;
        } else if (value instanceof IntegerType integerType) {
            if (integerType.getValue() != null) {
                addToConstructingFlatInteger(path, integerType.getValue(), flat);
            }
            return true;
        } else {
            log.warn("openEhrType is DV_COUNT but extracted value is not Quantity and not IntegerType; is {}",
                     value.getClass());
        }
        return false;
    }

    private boolean handleDvDateTime(final String path, final Base value, final JsonObject flat) {
        if (value instanceof DateTimeType dateTime) {
            if (dateTime.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateTimeToString(dateTime.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof DateType date) {
            if (date.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateToString(date.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof TimeType time) {
            if (time.getValue() != null) {
                addToConstructingFlat(path, time.getValue(), flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvDate(final String path, final Base value, final JsonObject flat) {
        if (value instanceof DateTimeType dateTime) {
            if (dateTime.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateToString(dateTime.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof DateType date) {
            if (date.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateToString(date.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvTime(final String path, final Base value, final JsonObject flat) {
        if (value instanceof DateTimeType dateTime) {
            if (dateTime.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.timeToString(dateTime.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof DateType date) {
            if (date.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.timeToString(date.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof TimeType time) {
            if (time.getValue() != null) {
                addToConstructingFlat(path, time.getValue(), flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvCodedText(final String path, final Base value, final JsonObject flat) {
        if (value instanceof CodeableConcept codeableConcept) {
            List<Coding> codings = codeableConcept.getCoding();
            if (!codings.isEmpty()) {
                // Handle the first coding as the primary coded text
                Coding primaryCoding = codings.get(0);
                addToConstructingFlat(path + SUFFIX_CODE, primaryCoding.getCode(), flat);
                addToConstructingFlat(path + SUFFIX_TERMINOLOGY, primaryCoding.getSystem(), flat);
                addToConstructingFlat(path + SUFFIX_VALUE, primaryCoding.getDisplay(), flat);
                
                // Handle additional codings as mappings
                addAdditionalCodingsAsMappings(path, codings, flat);
            }
            addToConstructingFlat(path + SUFFIX_VALUE, codeableConcept.getText(), flat);
            return true;
        } else if (value instanceof Coding coding) {
            addToConstructingFlat(path + SUFFIX_CODE, coding.getCode(), flat);
            addToConstructingFlat(path + SUFFIX_TERMINOLOGY, coding.getSystem(), flat);
            addToConstructingFlat(path + SUFFIX_VALUE, coding.getDisplay(), flat);
            return true;
        } else if (value instanceof StringType extractedString && path.contains("|")) {
            addToConstructingFlat(path, extractedString.getValue(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_CODED_TEXT but extracted value is not CodeableConcept; is {}",
                     value.getClass());
        }
        return false;
    }

    /**
     * Adds additional codings from a CodeableConcept as mappings in the openEHR flat format
     * 
     * @param path The base path for the mappings
     * @param codings The list of codings (first one is skipped as it's the primary coding)
     * @param flat The JSON object to add the mappings to
     */
    private void addAdditionalCodingsAsMappings(String path, List<Coding> codings, JsonObject flat) {
        for (int i = 1; i < codings.size(); i++) {
            Coding coding = codings.get(i);
            String mappingPath = path + "/_mapping:" + (i-1);
            
            addToConstructingFlat(mappingPath + "/match", "=", flat);
            addToConstructingFlat(mappingPath + "/target|preferred_term", coding.getDisplay(), flat);
            addToConstructingFlat(mappingPath + "/target|code", coding.getCode(), flat);
            addToConstructingFlat(mappingPath + "/target|terminology", coding.getSystem(), flat);
        }
    }

    private boolean handleIdentifier(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Identifier identifier) {
            addToConstructingFlat(path + SUFFIX_ID, identifier.getValue(), flat);
            return true;
        } else {
            log.warn("openEhrType is IDENTIFIER but extracted value is not Identifier; is {}", value.getClass());
        }
        return false;
    }

    private boolean handlePartyIdentifier(final String path, final Base value, final JsonObject flat) {
        if (value instanceof StringType string) {
            addToConstructingFlat(path + "|name", string.getValue(), flat);
            return true;
        } else if (value instanceof Identifier id) {
            addToConstructingFlat(path + "|id", id.getValue(), flat);
            addToConstructingFlat(path + "|assigner", id.getSystem(), flat);
            addToConstructingFlat(path + "|type", id.getType().getText(), flat);
            // if coding.code exists, it should override the type
            addToConstructingFlat(path + "|type", id.getType().getCodingFirstRep().getCode(), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handlePartyProxy(final String path, final Base value, final JsonObject flat) {
        if (value instanceof StringType string) {
            addToConstructingFlat(path + "|name", string.getValue(), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleCodePhrase(final String path, final Base value, final JsonObject flat,
                                     final String openEhrType) {
        if (value instanceof Coding coding) {
            addToConstructingFlat(path + SUFFIX_CODE, coding.getCode(), flat);
            addToConstructingFlat(path + SUFFIX_VALUE, coding.getCode(), flat);
            addToConstructingFlat(path + SUFFIX_TERMINOLOGY, coding.getSystem(), flat);
            return true;
        } else if (value instanceof Extension extension) {
            setFhirPathValue(path, extension.getValue(), openEhrType, flat);
            return true;
        } else if (value instanceof CodeableConcept concept) {
            setFhirPathValue(path, concept.getCodingFirstRep(), openEhrType, flat);
            return true;
        } else if (value instanceof Enumeration<?> enumeration) {
            addToConstructingFlat(path + SUFFIX_CODE, enumeration.getValueAsString(), flat);
            addToConstructingFlat(path + SUFFIX_VALUE, enumeration.getValueAsString(), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleDvBool(final String path, final Base value, final JsonObject flat) {
        if (value instanceof BooleanType booleanType) {
            addToConstructingBoolean(path, booleanType.getValue(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_BOOL but extracted value is not BooleanType; is {}", value.getClass());
        }
        return false;
    }

    private void addValuePerFhirType(final Base fhirValue, final String openEhrPath,
                                     final JsonObject constructingFlat) {
        if (fhirValue instanceof Quantity extractedQuantity) {
            if (extractedQuantity.getValue() != null) {
                addToConstructingFlat(openEhrPath, extractedQuantity.getValue().toPlainString(), constructingFlat);
            }
        } else if (fhirValue instanceof Coding extractedQuantity) {
            addToConstructingFlat(openEhrPath, extractedQuantity.getCode(), constructingFlat);
        } else if (fhirValue instanceof DateTimeType extractedQuantity) {
            addToConstructingFlat(openEhrPath, extractedQuantity.getValueAsString(), constructingFlat);
        } else if (fhirValue instanceof Annotation extracted) {
            addToConstructingFlat(openEhrPath, extracted.getText(), constructingFlat);
        } else if (fhirValue instanceof Address extracted) {
            addToConstructingFlat(openEhrPath, extracted.getText(), constructingFlat);
        } else if (fhirValue instanceof HumanName extracted) {
            addToConstructingFlat(openEhrPath, extracted.getNameAsSingleString(), constructingFlat);
        } else if (fhirValue instanceof Extension extracted) {
            if (extracted.getValue().hasPrimitiveValue()) {
                addValuePerFhirType(extracted.getValue(), openEhrPath, constructingFlat);
            }
//            addToConstructingFlat(openEhrPath, extracted.getValue().hasPrimitiveValue() ? extracted.getValue().primitiveValue() : null, constructingFlat);
        } else if (fhirValue.hasPrimitiveValue()) {
            addToConstructingFlat(openEhrPath, fhirValue.primitiveValue(), constructingFlat);
        } else {
            log.error("Unsupported fhir value toString!: {}", fhirValue);
        }
    }

    final void addToConstructingFlat(final String key, final String value, final JsonObject constructingFlat) {
        if (StringUtils.isEmpty(value)) {
            return;
        }
        log.debug("Setting value {} on path {}", value, key);
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    final void addToConstructingBoolean(final String key, final Boolean value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.addProperty(key, value);
    }

    final void addToConstructingFlatDouble(final String key, final Double value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    final void addToConstructingFlatInteger(final String key, final Integer value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }
}
