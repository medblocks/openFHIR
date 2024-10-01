package com.medblocks.openfhir.util;

import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.model.FhirConfig;
import com.medblocks.openfhir.fc.model.FhirConnectMapper;
import com.medblocks.openfhir.fc.model.Mapping;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.medblocks.openfhir.util.OpenFhirStringUtils.RESOLVE;

@Slf4j
@Component
public class OpenFhirMapperUtils {

    final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss");
    final SimpleDateFormat time2 = new SimpleDateFormat("HH:mm");
    final SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");


    public String getFhirConnectTypeToFhir(final String fhirConnectType) {
        if (fhirConnectType == null) {
            return null;
        }
        switch (fhirConnectType) {
            case "QUANTITY":
            case "PROPORTION":
                return Quantity.class.getSimpleName();
            case "DATETIME":
                return DateTimeType.class.getSimpleName();
            case "DATE":
                return DateType.class.getSimpleName();
            case "TIME":
                return TimeType.class.getSimpleName();
            case "BOOL":
                return BooleanType.class.getSimpleName();
            case "IDENTIFIER":
                return Identifier.class.getSimpleName();
            case "CODEABLECONCEPT":
                return CodeableConcept.class.getSimpleName();
            case "CODING":
                return Coding.class.getSimpleName();
            case "STRING":
                return StringType.class.getSimpleName();
            default:
                return null;
        }
    }

    public String dateToString(final Date date) {
        if (date == null) {
            return null;
        }
        return sdf2.format(date);
    }

    public String dateTimeToString(final Date date) {
        if (date == null) {
            return null;
        }
        return sdf.format(date);
    }

    public String timeToString(final Date date) {
        if (date == null) {
            return null;
        }
        return time.format(date);
    }

    public Date stringToDate(final String date) {
        if (date == null) {
            return null;
        }
        try {
            return sdf.parse(date);
        } catch (ParseException e) {
            log.error("Couldn't parse date: {}", date, e);
            try {
                return sdf2.parse(date);
            } catch (ParseException ex) {
                log.error("Couldn't parse date: {}", date, e);
            }
        }
        return null;
    }

    public Date stringToTime(final String date) {
        if (date == null) {
            return null;
        }
        try {
            return time.parse(date);
        } catch (ParseException e) {
            log.error("Couldn't parse date: {}", date, e);
            try {
                return time2.parse(date);
            } catch (ParseException ex) {
                log.error("Couldn't parse date: {}", date, e);
            }
        }
        return null;
    }

    public void prepareReferencedMappings(final String parentFhirPath,
                                          final String openEhrPath,
                                          final List<Mapping> referencedMapping) {
        for (Mapping mapping : referencedMapping) {
            mapping.getWith().setFhir(parentFhirPath + "." + RESOLVE + "." + mapping.getWith().getFhir());
            if (!FhirConnectConst.REFERENCE.equals(openEhrPath) && mapping.getWith().getOpenehr() != null) {
                mapping.getWith().setOpenehr(openEhrPath
                        .replace(FhirConnectConst.REFERENCE + "/", "")
                        .replaceAll("/", ".")
                        + mapping.getWith().getOpenehr()
                        .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, ""));
            }

        }
    }

    public void prepareForwardingSlotArchetypeMapper(final FhirConnectMapper slotArchetypeMappers,
                                                     final FhirConnectMapper parentMapper,
                                                     final String fhirPath,
                                                     final String openEhrPath,
                                                     final String firstFlatPath) {
        slotArchetypeMappers.setFhirConfig(new FhirConfig());
        slotArchetypeMappers.getFhirConfig().setResource(parentMapper.getFhirConfig().getResource());
        slotArchetypeMappers.getFhirConfig().setCondition(parentMapper.getFhirConfig().getCondition());

        prepareForwardingSlotArchetypeMapper(slotArchetypeMappers.getMappings(),
                fhirPath,
                openEhrPath,
                true,
                firstFlatPath);
    }

    public void prepareFollowedByMappings(final List<Mapping> followedByMappings,
                                          final String fhirPath,
                                          final String openehr,
                                          final String slotContext) {
        for (Mapping followedByMapping : followedByMappings) {
            if (!followedByMapping.getWith().getFhir().startsWith(FhirConnectConst.FHIR_RESOURCE_FC)) {
                followedByMapping.getWith().setFhir(fhirPath + "." + followedByMapping.getWith().getFhir());
            }
            if (!followedByMapping.getWith().getOpenehr().startsWith(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {

                if (followedByMapping.getWith().getOpenehr().startsWith(FhirConnectConst.REFERENCE)) {
                    final String openEhrWithReference = followedByMapping.getWith().getOpenehr().replace(FhirConnectConst.REFERENCE, "");

                    final String openEhrPathMiddle = StringUtils.isEmpty(openehr) ? "" : ("/" + openehr + (StringUtils.isEmpty(openEhrWithReference) ? "" : "/"));
                    followedByMapping.getWith().setOpenehr(FhirConnectConst.REFERENCE + openEhrPathMiddle + openEhrWithReference
                            .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + ".", "")
                            .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, ""));
                } else {
                    final String delimeter = followedByMapping.getWith().getOpenehr().startsWith("|") ? "" : "/";
                    followedByMapping.getWith().setOpenehr(openehr + delimeter + followedByMapping.getWith().getOpenehr()
                            .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + ".", "")
                            .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, ""));
                }
            } else if (followedByMapping.getWith().getOpenehr().equals(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {
                followedByMapping.getWith().setOpenehr(openehr);
            } else {
                followedByMapping.getWith().setOpenehr(new OpenFhirStringUtils().prepareOpenEhrSyntax(followedByMapping.getWith().getOpenehr(), slotContext));
            }

            // now conditions
            if (followedByMapping.getCondition() != null
                    && !followedByMapping.getCondition().getTargetRoot().startsWith(FhirConnectConst.FHIR_RESOURCE_FC)) {
                followedByMapping.getCondition().setTargetRoot(fhirPath + "." + followedByMapping.getCondition().getTargetRoot());
            }

        }
    }

    public void prepareForwardingSlotArchetypeMapperNoFhirPrefix(final FhirConnectMapper slotArchetypeMappers,
                                                                 final FhirConnectMapper parentMapper,
                                                                 final String fhirPath,
                                                                 final String openEhrPath) {
        slotArchetypeMappers.setFhirConfig(new FhirConfig());
        slotArchetypeMappers.getFhirConfig().setResource(parentMapper.getFhirConfig().getResource());
        slotArchetypeMappers.getFhirConfig().setCondition(parentMapper.getFhirConfig().getCondition());

        prepareForwardingSlotArchetypeMapper(slotArchetypeMappers.getMappings(),
                fhirPath,
                openEhrPath,
                false,
                null);

        for (Mapping slotArchetypeMappersMapping : slotArchetypeMappers.getMappings()) {
            if (slotArchetypeMappersMapping.getWith().getOpenehr() == null) {
                slotArchetypeMappersMapping.getWith().setOpenehr(openEhrPath);
            }
        }
    }

    public void prepareForwardingSlotArchetypeMapper(final List<Mapping> forwardMappers,
                                                     final String fhirPath,
                                                     final String openEhrPath,
                                                     boolean fhirPrefixing,
                                                     final String firstFlatPath) {

        // fix fhir forwarding params
        for (Mapping slotArchetypeMappersMapping : forwardMappers) {
            if (slotArchetypeMappersMapping.getWith().getFhir() == null) {
                continue;
            }
            if (FhirConnectConst.FHIR_ROOT_FC.equals(slotArchetypeMappersMapping.getWith().getFhir())) {
                if (!fhirPrefixing) {
                    slotArchetypeMappersMapping.getWith().setFhir("");
                } else {
                    slotArchetypeMappersMapping.getWith().setFhir(fhirPath);
                }
            } else if (slotArchetypeMappersMapping.getWith().getFhir().startsWith(FhirConnectConst.FHIR_ROOT_FC)) {
                slotArchetypeMappersMapping.getWith().setFhir(slotArchetypeMappersMapping.getWith().getFhir()
                        .replace(FhirConnectConst.FHIR_ROOT_FC, fhirPrefixing ? fhirPath : ""));

                if (slotArchetypeMappersMapping.getWith().getFhir().startsWith(".") && !fhirPrefixing) {
                    slotArchetypeMappersMapping.getWith().setFhir(slotArchetypeMappersMapping.getWith().getFhir().substring(1));
                }
            }
        }

        // fix openehr forwarding params
        for (Mapping slotArchetypeMappersMapping : forwardMappers) {
            if (slotArchetypeMappersMapping.getWith().getOpenehr() == null) {
                continue;
            }
            if (FhirConnectConst.OPENEHR_ARCHETYPE_FC.equals(slotArchetypeMappersMapping.getWith().getOpenehr())) {
                slotArchetypeMappersMapping.getWith().setOpenehr(openEhrPath.replaceAll("/", "."));
            } else if (slotArchetypeMappersMapping.getWith().getOpenehr().startsWith(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {
                slotArchetypeMappersMapping.getWith().setOpenehr(slotArchetypeMappersMapping.getWith().getOpenehr().replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC,
                        openEhrPath.replaceAll("/", ".")));
            } else if (slotArchetypeMappersMapping.getWith().getOpenehr().startsWith(FhirConnectConst.REFERENCE)) {
                slotArchetypeMappersMapping.getWith().setOpenehr(slotArchetypeMappersMapping.getWith().getOpenehr() + "." + openEhrPath.replaceAll("/", "."));
            } else {
                // prefix with parent
                final String suff = StringUtils.isBlank(slotArchetypeMappersMapping.getWith().getOpenehr()) ? "" : ("." + slotArchetypeMappersMapping.getWith().getOpenehr());
                slotArchetypeMappersMapping.getWith().setOpenehr(openEhrPath.replaceAll("/", ".") + suff);
            }
        }

        // now conditions
        for (Mapping slotArchetypeMappersMapping : forwardMappers) {
            if (slotArchetypeMappersMapping.getCondition() == null) {
                continue;
            }

            if (FhirConnectConst.FHIR_ROOT_FC.equals(slotArchetypeMappersMapping.getCondition().getTargetRoot())) {
                if (!fhirPrefixing) {
                    slotArchetypeMappersMapping.getCondition().setTargetRoot("");
                } else {
                    slotArchetypeMappersMapping.getCondition().setTargetRoot(fhirPath);
                }
            } else if (slotArchetypeMappersMapping.getCondition().getTargetRoot().startsWith(FhirConnectConst.FHIR_ROOT_FC)) {
                slotArchetypeMappersMapping.getCondition().setTargetRoot(slotArchetypeMappersMapping.getCondition().getTargetRoot()
                        .replace(FhirConnectConst.FHIR_ROOT_FC, fhirPrefixing ? fhirPath : ""));

                if (slotArchetypeMappersMapping.getCondition().getTargetRoot().startsWith(".") && !fhirPrefixing) {
                    slotArchetypeMappersMapping.getCondition().setTargetRoot(slotArchetypeMappersMapping.getCondition().getTargetRoot().substring(1));
                }
            }
        }
    }
}
