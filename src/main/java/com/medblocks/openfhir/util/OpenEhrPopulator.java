package com.medblocks.openfhir.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medblocks.openfhir.fc.FhirConnectConst;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_CLUSTER;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;

/**
 * Class used for populating openEHR flat path Composition
 */
@Slf4j
@Component
public class OpenEhrPopulator {

    private final OpenFhirMapperUtils openFhirMapperUtils;

    @Autowired
    public OpenEhrPopulator(OpenFhirMapperUtils openFhirMapperUtils) {
        this.openFhirMapperUtils = openFhirMapperUtils;
    }



    /**
     * Adds extracted value to the openEHR flat path composition represented with the 'constructingFlat' variable
     *
     * @param openEhrPath      path that should be used in the flat path composition
     * @param extractedValue   value as extracted from a FHIR object
     * @param openEhrType      openEHR type as defined in the fhir connect model mapping
     * @param constructingFlat composition in a flat path format that's being constructed
     */
    public void setFhirPathValue(final String openEhrPath, final Base extractedValue, final String openEhrType, final JsonObject constructingFlat) {
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
                if(addedDate) {
                    return;
                }
            case FhirConnectConst.DV_TIME:
                final boolean addedTime = handleDvTime(openEhrPath, extractedValue, constructingFlat);
                if(addedTime) {
                    return;
                }
            case FhirConnectConst.DV_CODED_TEXT:
                final boolean addedCodeText = handleDvCodedText(openEhrPath, extractedValue, constructingFlat);
                if(addedCodeText) {
                    return;
                }
            case FhirConnectConst.DV_IDENTIFIER:
                final boolean addedIdentifier = handleIdentifier(openEhrPath, extractedValue, constructingFlat);
                if(addedIdentifier) {
                    return;
                }
            case FhirConnectConst.CODE_PHRASE:
                final boolean addedCode = handleCodePhrase(openEhrPath, extractedValue, constructingFlat, openEhrType);
                if(addedCode) {
                    return;
                }
            case FhirConnectConst.DV_TEXT:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);
                return;
            case FhirConnectConst.DV_BOOL:
                final boolean addedBool = handleDvBool(openEhrPath, extractedValue, constructingFlat);
                if(addedBool) {
                    return;
                }
            default:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);

        }
    }

    private void handleDvMultimedia(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Attachment attachment) {
            int size = (attachment.getSize() == 0 && attachment.getData() != null) ? attachment.getData().length : attachment.getSize();
            addToConstructingFlat(path + "|size", String.valueOf(size), flat);
            addToConstructingFlat(path + "|mediatype", attachment.getContentType(), flat);
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
                addToConstructingFlatDouble(path + "|magnitude", quantity.getValue().doubleValue(), flat);
            }
            addToConstructingFlat(path + "|unit", quantity.getUnit(), flat);
            return true;
        } else if (value instanceof Ratio ratio) {
            setFhirPathValue(path, ratio.getNumerator(), FhirConnectConst.DV_QUANTITY, flat);
            return true;
        } else {
            log.warn("openEhrType is DV_QUANTITY but extracted value is not Quantity and not Ratio; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvOrdinal(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlat(path + "|ordinal", quantity.getValue().toPlainString(), flat);
            }
            addToConstructingFlat(path + "|value", quantity.getUnit(), flat);
            addToConstructingFlat(path + "|code", quantity.getCode(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_ORDINAL but extracted value is not Quantity; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvProportion(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if ("%".equals(quantity.getCode())) {
                addToConstructingFlatDouble(path + "|denominator", 100.0, flat);
            }
            if (quantity.getValue() != null) {
                addToConstructingFlatDouble(path + "|numerator", quantity.getValue().doubleValue(), flat);
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
            log.warn("openEhrType is DV_COUNT but extracted value is not Quantity and not IntegerType; is {}", value.getClass());
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
                Coding coding = codings.get(0);
                addToConstructingFlat(path + "|code", coding.getCode(), flat);
                addToConstructingFlat(path + "|terminology", coding.getSystem(), flat);
                if(codeableConcept.getText() == null || codeableConcept.getText().isEmpty()) {
                    addToConstructingFlat(path + "|value", coding.getDisplay(), flat);
                }
            }
            addToConstructingFlat(path + "|value", codeableConcept.getText(), flat);
            return true;
        } else if(value instanceof Coding coding) {
            addToConstructingFlat(path + "|code", coding.getCode(), flat);
            addToConstructingFlat(path + "|terminology", coding.getSystem(), flat);
            addToConstructingFlat(path + "|value", coding.getDisplay(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_CODED_TEXT but extracted value is not CodeableConcept; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleIdentifier(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Identifier identifier) {
            addToConstructingFlat(path + "|id", identifier.getValue(), flat);
            return true;
        } else {
            log.warn("openEhrType is IDENTIFIER but extracted value is not Identifier; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleCodePhrase(final String path, final Base value, final JsonObject flat, final String openEhrType) {
        if (value instanceof Coding coding) {
            addToConstructingFlat(path + "|code", coding.getCode(), flat);
            addToConstructingFlat(path + "|value", coding.getCode(), flat);
            addToConstructingFlat(path + "|terminology", coding.getSystem(), flat);
            return true;
        } else if (value instanceof Extension extension) {
            setFhirPathValue(path, extension.getValue(), openEhrType, flat);
            return true;
        } else if (value instanceof CodeableConcept concept) {
            setFhirPathValue(path, concept.getCodingFirstRep(), openEhrType, flat);
            return true;
        } else if (value instanceof Enumeration<?> enumeration) {
            addToConstructingFlat(path + "|code", enumeration.getValueAsString(), flat);
            addToConstructingFlat(path + "|value", enumeration.getValueAsString(), flat);
            return true;
        } else {
            log.warn("openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}", value.getClass());
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

    private void addValuePerFhirType(final Base fhirValue, final String openEhrPath, final JsonObject constructingFlat) {
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
            addToConstructingFlat(openEhrPath, extracted.getValue().hasPrimitiveValue() ? extracted.getValue().primitiveValue() : null, constructingFlat);
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
