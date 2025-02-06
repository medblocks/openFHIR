package com.medblocks.openfhir.util;

import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_ARCHETYPE_FC;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RESOLVE;

import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.OpenFhirFhirConfig;
import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.medblocks.openfhir.fc.schema.model.Condition;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import com.medblocks.openfhir.fc.schema.model.With;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.springframework.stereotype.Component;

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

    /**
     * Modifies Mapping.openEhr paths, replacing $reference and $archetype and adding resolve() to FHIR
     * path so it's evaluated by the fhir path engine
     */
    public void prepareReferencedMappings(final String parentFhirPath,
                                          final String openEhrPath,
                                          final List<Mapping> referencedMapping,
                                          final String firstFlatPath) {
        for (final Mapping mapping : referencedMapping) {
            mapping.getWith().setFhir(parentFhirPath + "." + RESOLVE + "." + mapping.getWith().getFhir());
            if (FhirConnectConst.REFERENCE.equals(openEhrPath) || mapping.getWith().getOpenehr() == null) {
                continue;
            }
            if (mapping.getWith().getOpenehr().startsWith(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {
                mapping.getWith()
                        .setOpenehr(new OpenFhirStringUtils().prepareOpenEhrSyntax(mapping.getWith().getOpenehr(),
                                                                                   firstFlatPath));
            } else {
                // todo: I think lots of this below can be removed, revisit once covered with unit tests
                if (openEhrPath.startsWith(FhirConnectConst.REFERENCE)) {
                    final String openehr = mapping.getWith().getOpenehr();
                    final String replacedOpenEhr = openehr.replace(
                            FhirConnectConst.OPENEHR_ARCHETYPE_FC, "");
                    final String lastDelimiter =
                            StringUtils.isBlank(replacedOpenEhr) || replacedOpenEhr.startsWith(".") ? "" : ".";

                    mapping.getWith().setOpenehr(openEhrPath
                                                         .replace(FhirConnectConst.REFERENCE + "/", "")
                                                         .replace(FhirConnectConst.REFERENCE + ".", "")
                                                         .replaceAll("/", ".")
                                                         + lastDelimiter + replacedOpenEhr);
                } else if (openEhrPath.endsWith(FhirConnectConst.REFERENCE)) {
                    final String followingOpenEhr = mapping.getWith().getOpenehr()
                            .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, "");
                    final String openEhrSuffix = StringUtils.isBlank(followingOpenEhr) ? "" : ("." + followingOpenEhr);
                    mapping.getWith().setOpenehr(openEhrPath
                                                         .replace("/" + FhirConnectConst.REFERENCE, "")
                                                         .replace("." + FhirConnectConst.REFERENCE, "")
                                                         .replaceAll("/", ".")
                                                         + openEhrSuffix);
                } else {
                    mapping.getWith().setOpenehr(openEhrPath
                                                         .replace(FhirConnectConst.REFERENCE + "/", ".")
                                                         .replace("/" + FhirConnectConst.REFERENCE, ".")
                                                         .replace("." + FhirConnectConst.REFERENCE, ".")
                                                         .replace(FhirConnectConst.REFERENCE + ".", ".")
                                                         .replaceAll("/", ".")
                                                         + mapping.getWith().getOpenehr()
                            .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, ""));
                }
            }
        }
    }

    /**
     * Followed by mappers needs to inherit parent's properties. This method makes sure parent's paths are inherited
     * in followed by mappings
     *
     * @param followedByMappings followed by mappings that need to inherit parent's properties
     * @param fhirPath parent's fhir path as constructed up until now
     * @param openehr parent's openehr path as constructed up until now
     */
    public void prepareFollowedByMappings(final List<Mapping> followedByMappings,
                                          final String fhirPath,
                                          final String openehr,
                                          final String slotContext) {
        for (final Mapping followedByMapping : followedByMappings) {
            final With with = followedByMapping.getWith();
            final String hardcodedValue = with.getValue();
            if (followedByMapping.getWith().getOpenehr() == null && hardcodedValue != null) {
                // hardcoding to FHIR
                with.setOpenehr(OPENEHR_ARCHETYPE_FC);
            }
            if (!followedByMapping.getWith().getFhir().startsWith(FhirConnectConst.FHIR_RESOURCE_FC)) {
                followedByMapping.getWith().setFhir(fhirPath + "." + followedByMapping.getWith().getFhir());
            }
            if (!followedByMapping.getWith().getOpenehr().startsWith(FhirConnectConst.OPENEHR_ARCHETYPE_FC) && !followedByMapping.getWith().getOpenehr().startsWith(FhirConnectConst.OPENEHR_COMPOSITION_FC)) {

                if (followedByMapping.getWith().getOpenehr().startsWith(FhirConnectConst.REFERENCE)) {
                    final String openEhrWithReference = followedByMapping.getWith().getOpenehr()
                            .replace(FhirConnectConst.REFERENCE, "");

                    final String openEhrPathMiddle = StringUtils.isEmpty(openehr) ? ""
                            : ("/" + openehr + (StringUtils.isEmpty(openEhrWithReference) ? "" : "/"));
                    followedByMapping.getWith()
                            .setOpenehr(FhirConnectConst.REFERENCE + openEhrPathMiddle + openEhrWithReference
                                    .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + ".", "")
                                    .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, ""));
                } else {
                    final String delimeter = followedByMapping.getWith().getOpenehr().startsWith("|") ? "" : "/";
                    followedByMapping.getWith()
                            .setOpenehr(openehr + delimeter + followedByMapping.getWith().getOpenehr()
                                    .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + ".", "")
                                    .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, ""));
                }
            } else if (followedByMapping.getWith().getOpenehr().equals(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {
                followedByMapping.getWith().setOpenehr(openehr);
            } else {
                followedByMapping.getWith().setOpenehr(
                        new OpenFhirStringUtils().prepareOpenEhrSyntax(followedByMapping.getWith().getOpenehr(),
                                                                       slotContext));
            }

            // now conditions
            if (followedByMapping.getFhirCondition() != null
                    && !followedByMapping.getFhirCondition().getTargetRoot()
                    .startsWith(FhirConnectConst.FHIR_RESOURCE_FC)) {
                followedByMapping.getFhirCondition()
                        .setTargetRoot(fhirPath + "." + followedByMapping.getFhirCondition().getTargetRoot());
            }

        }
    }


    /**
     * Slot archetype mappers need to inherit parent's openEhr and FHIR path as well as Condition. This method
     * makes sure this is inherited in slot mappers
     *
     * @param slotArchetypeMappers slot mappers that need to inherit parents properties
     * @param parentMapper parent mapper
     * @param fhirPath parent's fhir path as constructed up until now
     * @param openEhrPath parent's openehr path as constructed up until now
     */
    public void prepareForwardingSlotArchetypeMapper(final OpenFhirFhirConnectModelMapper slotArchetypeMappers,
                                                     final OpenFhirFhirConnectModelMapper parentMapper,
                                                     final String fhirPath,
                                                     final String openEhrPath) {
        slotArchetypeMappers.setFhirConfig(new OpenFhirFhirConfig());
        slotArchetypeMappers.getFhirConfig().setResource(parentMapper.getFhirConfig().getResource());
        slotArchetypeMappers.getFhirConfig().setCondition(parentMapper.getFhirConfig().getCondition());

        prepareForwardingSlotArchetypeMappings(slotArchetypeMappers.getMappings(),
                                               fhirPath,
                                               openEhrPath,
                                               true);
    }

    /**
     * Slot archetype mappers need to inherit parent's openEhr and FHIR path as well as Condition. This method
     * makes sure this is inherited in slot mappers.
     * <p>
     * Used in a FHIR to openEHR mappings where you do not want FHIR to be prefixed in the paths, because we have inner
     * helpers with fhir paths defined as relative to the parent and not absolute ones
     *
     * @param slotArchetypeMappers slot mappers that need to inherit parents properties
     * @param parentMapper parent mapper
     * @param fhirPath parent's fhir path as constructed up until now
     * @param openEhrPath parent's openehr path as constructed up until now
     */
    public void prepareForwardingSlotArchetypeMapperNoFhirPrefix(
            final OpenFhirFhirConnectModelMapper slotArchetypeMappers,
            final OpenFhirFhirConnectModelMapper parentMapper,
            final String fhirPath,
            final String openEhrPath) {
        slotArchetypeMappers.setFhirConfig(new OpenFhirFhirConfig());
        slotArchetypeMappers.getFhirConfig().setResource(parentMapper.getFhirConfig().getResource());
        slotArchetypeMappers.getFhirConfig().setCondition(parentMapper.getFhirConfig().getCondition());

        if (slotArchetypeMappers.getMappings() == null) {
            return;
        }

        prepareForwardingSlotArchetypeMappings(slotArchetypeMappers.getMappings(),
                                               fhirPath,
                                               openEhrPath,
                                               false);

        for (Mapping slotArchetypeMappersMapping : slotArchetypeMappers.getMappings()) {
            if (slotArchetypeMappersMapping.getWith().getOpenehr() == null) {
                slotArchetypeMappersMapping.getWith().setOpenehr(openEhrPath);
            }
        }
    }


    /**
     * Forwarding slot archetype mappings are adjusted in fhir and openehr paths as well as conditions that are being
     * passed down the line to "child" mappings.
     *
     * @param forwardMappers Mappings directly referenced by the slot archetype
     *         (slotArchetypeMappers.getMappings)
     * @param fhirPath fhir path as constructed up until now for the slot archetype
     * @param openEhrPath openehr path as constructed up until now for the slot archetype
     * @param fhirPrefixing whether FHIR path needs to be prefixed as well in the child mappings. False when
     *         mapping
     *         from FHIR to openEHR where we have inner helpers and paths are always defined as relative
     *         to the parent and not absolute
     */
    public void prepareForwardingSlotArchetypeMappings(final List<Mapping> forwardMappers,
                                                       final String fhirPath,
                                                       final String openEhrPath,
                                                       boolean fhirPrefixing) {

        if (forwardMappers == null) {
            return;
        }

        // fix fhir forwarding params
        fixFhirForwardingPaths(forwardMappers, fhirPath, fhirPrefixing);

        // fix openehr forwarding params
        fixOpenEhrForwardingPaths(forwardMappers, openEhrPath);

        // now conditions
        prepareForwardingSlotArchetypeMappingsConditions(forwardMappers, fhirPath, fhirPrefixing);
    }

    public void fixStartingArchetypeMappings(final List<Mapping> mappings, final String mainArchetype){
        for (Mapping mapping : mappings){
            String openehrPath = mapping.getWith().getOpenehr();

            if(openehrPath != null && !openehrPath.contains(mainArchetype)){
                mapping.getWith().setOpenehr(openehrPath.replace(OPENEHR_ARCHETYPE_FC, OPENEHR_ARCHETYPE_FC+"/content["+mainArchetype+"]"));
            }
            if(mapping.getOpenehrCondition()!= null && !mapping.getOpenehrCondition().getTargetRoot().contains(mainArchetype)){
                String openehrConditionTargetRoot = mapping.getOpenehrCondition().getTargetRoot();
                mapping.getOpenehrCondition().setTargetRoot(openehrConditionTargetRoot.replace(OPENEHR_ARCHETYPE_FC, OPENEHR_ARCHETYPE_FC+"/content["+mainArchetype+"]"));
            }
            if(mapping.getFollowedBy()!=null){
                fixStartingArchetypeMappings(mapping.getFollowedBy().getMappings(),mainArchetype);
            }
            if(mapping.getReference() !=null){
                fixStartingArchetypeMappings(mapping.getReference().getMappings(),mainArchetype);
            }
        }
    }

    private void fixFhirForwardingPaths(final List<Mapping> forwardMappers,
                                        final String fhirPath,
                                        boolean fhirPrefixing) {
        for (final Mapping slotArchetypeMappersMapping : forwardMappers) {
            final String fhir = slotArchetypeMappersMapping.getWith().getFhir();
            if (fhir == null) {
                continue;
            }
            if (FhirConnectConst.FHIR_ROOT_FC.equals(fhir) || FhirConnectConst.FHIR_RESOURCE_FC.equals(fhir)) {
                if (!fhirPrefixing) {
                    slotArchetypeMappersMapping.getWith().setFhir("");
                } else {
                    slotArchetypeMappersMapping.getWith().setFhir(fhirPath);
                }
            }
            fixFhirForwardingPathsRootResource(slotArchetypeMappersMapping, FhirConnectConst.FHIR_ROOT_FC, fhirPath,
                                               fhirPrefixing);
            fixFhirForwardingPathsRootResource(slotArchetypeMappersMapping, FhirConnectConst.FHIR_RESOURCE_FC, fhirPath,
                                               fhirPrefixing);
        }
    }

    private void fixFhirForwardingPathsRootResource(final Mapping slotArchetypeMappersMapping, final String constant,
                                                    final String fhirPath,
                                                    boolean fhirPrefixing) {
        if (slotArchetypeMappersMapping.getWith().getFhir().startsWith(constant)) {
            slotArchetypeMappersMapping.getWith().setFhir(slotArchetypeMappersMapping.getWith().getFhir()
                                                                  .replace(constant,
                                                                           fhirPrefixing ? fhirPath : ""));

            if (slotArchetypeMappersMapping.getWith().getFhir().startsWith(".") && !fhirPrefixing) {
                slotArchetypeMappersMapping.getWith()
                        .setFhir(slotArchetypeMappersMapping.getWith().getFhir().substring(1));
            }
        }
    }

    private void fixOpenEhrForwardingPaths(final List<Mapping> forwardMappers,
                                           final String openEhrPath) {
        for (Mapping slotArchetypeMappersMapping : forwardMappers) {
            if (slotArchetypeMappersMapping.getWith().getOpenehr() == null) {
                continue;
            }
            if (FhirConnectConst.OPENEHR_ARCHETYPE_FC.equals(slotArchetypeMappersMapping.getWith().getOpenehr())) {
                slotArchetypeMappersMapping.getWith().setOpenehr(openEhrPath);
            } else if (slotArchetypeMappersMapping.getWith().getOpenehr()
                    .startsWith(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {
                slotArchetypeMappersMapping.getWith().setOpenehr(slotArchetypeMappersMapping.getWith().getOpenehr()
                                                                         .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC,
                                                                                  openEhrPath));
            } else if (slotArchetypeMappersMapping.getWith().getOpenehr()
                    .startsWith(FhirConnectConst.OPENEHR_COMPOSITION_FC)){
                slotArchetypeMappersMapping.getWith().setOpenehr(slotArchetypeMappersMapping.getWith().getOpenehr()
                        .replace(FhirConnectConst.OPENEHR_COMPOSITION_FC,
                                openEhrPath));
            } else if (slotArchetypeMappersMapping.getWith().getOpenehr().startsWith(FhirConnectConst.REFERENCE)) {
                slotArchetypeMappersMapping.getWith().setOpenehr(
                        slotArchetypeMappersMapping.getWith().getOpenehr() + "/" + openEhrPath);
            } else {
                // prefix with parent
                final String suff = StringUtils.isBlank(slotArchetypeMappersMapping.getWith().getOpenehr()) ? ""
                        : ("/" + slotArchetypeMappersMapping.getWith().getOpenehr());
                slotArchetypeMappersMapping.getWith().setOpenehr(openEhrPath + suff);
            }
        }
    }

    private void prepareForwardingSlotArchetypeMappingsConditions(final List<Mapping> forwardMappers,
                                                                  final String fhirPath,
                                                                  final boolean fhirPrefixing) {
        for (Mapping slotArchetypeMappersMapping : forwardMappers) {
            final Condition fhirCondition = slotArchetypeMappersMapping.getFhirCondition();
            if (fhirCondition == null) {
                continue;
            }

            final String targetRoot = fhirCondition.getTargetRoot();
            if (FhirConnectConst.FHIR_ROOT_FC.equals(targetRoot) || FhirConnectConst.FHIR_RESOURCE_FC.equals(
                    targetRoot)) {
                if (!fhirPrefixing) {
                    fhirCondition.setTargetRoot("");
                } else {
                    fhirCondition.setTargetRoot(fhirPath);
                }
            }
            setTargetRootBasedOnConstant(targetRoot, fhirCondition, fhirPrefixing, fhirPath,
                                         FhirConnectConst.FHIR_ROOT_FC);
            setTargetRootBasedOnConstant(targetRoot, fhirCondition, fhirPrefixing, fhirPath,
                                         FhirConnectConst.FHIR_RESOURCE_FC);
        }
    }

    private void setTargetRootBasedOnConstant(final String targetRoot, final Condition fhirCondition,
                                              final boolean fhirPrefixing, final String fhirPath,
                                              final String constant) {
        if (targetRoot.startsWith(constant)) {
            fhirCondition.setTargetRoot(targetRoot
                                                .replace(constant,
                                                         fhirPrefixing ? fhirPath : ""));

            if (targetRoot.startsWith(".") && !fhirPrefixing) {
                fhirCondition.setTargetRoot(targetRoot.substring(1));
            }
        }
    }
}
