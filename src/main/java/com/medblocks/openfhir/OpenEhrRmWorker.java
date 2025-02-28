package com.medblocks.openfhir;

import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhrHelper;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.aql.webtemplatepath.AqlPath;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_UNDERSCORABLES;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;

@Component
public class OpenEhrRmWorker {

    private final OpenFhirStringUtils openFhirStringUtils;
    private final OpenFhirMapperUtils openFhirMapperUtils;

    @Autowired
    public OpenEhrRmWorker(OpenFhirStringUtils openFhirStringUtils,
                           OpenFhirMapperUtils openFhirMapperUtils) {
        this.openFhirStringUtils = openFhirStringUtils;
        this.openFhirMapperUtils = openFhirMapperUtils;
    }

    /**
     * Modifies simplified flat path with occurrences, i.e. patient.person becomes patient/person[n]
     * at the same time, it sets openEHR type as it finds all possible ones on the template definition
     *
     * @param helpers     created FhirToOpenEhr helpers
     * @param webTemplate openEHR web template
     */
    public void fixFlatWithOccurrences(final List<FhirToOpenEhrHelper> helpers, final WebTemplate webTemplate) {
        for (final FhirToOpenEhrHelper fhirToOpenEhrHelper : helpers) {

            final String openEhrKey = fhirToOpenEhrHelper.getOpenEhrPath();
            final Set<String> forcedTypes = openFhirStringUtils.getPossibleRmTypeValue(fhirToOpenEhrHelper.getOpenEhrType());

            final boolean hasSuffix = openEhrKey.contains("|");
            final String suffix = hasSuffix ? openEhrKey.substring(openEhrKey.indexOf("|")) : null;
            final String flat = hasSuffix ? openEhrKey.substring(0, openEhrKey.indexOf("|")) : openEhrKey;
            final String[] split = flat.substring(flat.indexOf("/") + 1).split("/"); // we want to remove the first path, as it's the template itself
            final WebTemplateNode tree = webTemplate.getTree();
            final StringJoiner constructing = new StringJoiner("/");
            final String pathToFindSuffix="/";

            // walk through all web template nodes and enrich them with types and occurrence indexes
            walkThroughNodes(tree.getChildren(), String.join("/", split), constructing, forcedTypes, fhirToOpenEhrHelper,pathToFindSuffix);

            final String actualSuffix = openFhirMapperUtils.endsWithAqlSuffix(suffix) ? openFhirMapperUtils.replaceAqlSuffixWithFlatSuffix(suffix) : suffix;
            fhirToOpenEhrHelper.setOpenEhrPath(tree.getId() + "/" + fhirToOpenEhrHelper.getOpenEhrPath() + (hasSuffix ? actualSuffix : ""));

            // we compare so that we can see if if was found within the template; if not, we don't want for it to end up in the flat json
            final int initialOpenEhrPathWithProperTreeLength = split.length + 1;
            if (fhirToOpenEhrHelper.getOpenEhrPath().replace("|", "/").split("/").length < initialOpenEhrPathWithProperTreeLength ) {
                // means it didn't find it fully.. so it probably doesn't exist
                if (!FhirConnectConst.DV_MULTIMEDIA.equals(fhirToOpenEhrHelper.getOpenEhrType())) { // multimedia and its 'content' is a tad bit special...
                    fhirToOpenEhrHelper.setOpenEhrType(OPENEHR_TYPE_NONE);
                }
            }

            if (fhirToOpenEhrHelper.getFhirToOpenEhrHelpers() != null) {
                fixFlatWithOccurrences(fhirToOpenEhrHelper.getFhirToOpenEhrHelpers(), webTemplate);
            }

            removeInvalidOpenEhrPath(fhirToOpenEhrHelper);

        }
    }

    /**
     * Walks through web template nodes and sets recurring indexes on simplified flat path; at the same time, it adds
     * openEHR type
     *
     * @param webTemplateNodes web template nodes as they exist on the openEHR template
     * @param path             part of openEHR path that's being searched for within the webTemplateNodes
     * @param constructing     string being constructed from the path elements, however here already including recurring indexes notation, i.e. medikationseintrag[n]
     * @param forcedTypes      if openEHR type is being "forced" by a fhir connect mapping definition, this is the one we'll try to find within the template
     */
    public void walkThroughNodes(final List<WebTemplateNode> webTemplateNodes, final String path, final StringJoiner constructing, final Set<String> forcedTypes, final FhirToOpenEhrHelper fhirToOpenEhrHelper, String pathToFindSuffix) {
        if (StringUtils.isBlank(path)) {
            // everything has been resolved
            fhirToOpenEhrHelper.setOpenEhrPath(constructing.toString());
            return;
        }
        final List<String> remainingPaths;
        final String[] splitOpenEhrPath = path.split("/");
        final String zerothSplitPath = splitOpenEhrPath[0];
        final String pathToFind = pathToFindSuffix + zerothSplitPath.replace("*", "/");
        if (zerothSplitPath.startsWith("_")) {
            // we don't bother with this, can't be multiple occurrences
            constructing.add(splitOpenEhrPath[0]);
            remainingPaths = Arrays.asList(splitOpenEhrPath).subList(1, splitOpenEhrPath.length);
            walkThroughNodes(webTemplateNodes, String.join("/", remainingPaths),
                             constructing, forcedTypes, fhirToOpenEhrHelper, pathToFindSuffix);
            return;
        }
        AqlPath aqlPath = AqlPath.parse(pathToFind);
        Set<String> aqlPathNodes = webTemplateNodes.stream()
                .filter(node -> aqlPath.format(false).equals(node.getAqlPath(false)))
                .map(node-> node.getId(false))
                .collect(Collectors.toSet());
        final WebTemplateNode findingTheOne = webTemplateNodes.stream()
                .filter(ch -> aqlPath.format(true).equals(ch.getAqlPath(true)) || (aqlPath.format(false).equals(ch.getAqlPath(false)) && aqlPathNodes.size()<=1))
                .findAny()
                .orElse(null);
        if (findingTheOne == null) {
            for (WebTemplateNode itemTree : webTemplateNodes) {
                walkThroughNodes(itemTree.getChildren(), path, constructing, forcedTypes, fhirToOpenEhrHelper, pathToFindSuffix);
            }
            fhirToOpenEhrHelper.setOpenEhrPath(constructing.toString());
            return;
        }
        remainingPaths = Arrays.asList(splitOpenEhrPath).subList(1, splitOpenEhrPath.length);
        if (findingTheOne.isMulti()) {
            // is multiple occurrences
            constructing.add(findingTheOne.getId() + RECURRING_SYNTAX);
            fhirToOpenEhrHelper.setOpenEhrType(forcedTypes == null ? findingTheOne.getRmType() : getCorrectOpenEhrType(forcedTypes, findingTheOne,constructing));
        } else {
            if(FhirConnectConst.OPENEHR_INVALID_PATH_RM_TYPES.contains(findingTheOne.getRmType())) {
                constructing.add(findingTheOne.getRmType());
            }else {
                constructing.add(OPENEHR_UNDERSCORABLES.contains(findingTheOne.getId()) ? ("_"+findingTheOne.getId()) : findingTheOne.getId());
            }
            fhirToOpenEhrHelper.setOpenEhrType(forcedTypes == null ? findingTheOne.getRmType() : getCorrectOpenEhrType(forcedTypes, findingTheOne,constructing));
        }
        String remainingPathsStr = String.join("/", remainingPaths);
        AqlPath remainingsAqlPath = AqlPath.parse(remainingPathsStr);
        if(!remainingsAqlPath.format(false).equals(remainingPathsStr)) {
            walkThroughNodes(findingTheOne.getChildren(), String.join("/", remainingPaths),
                    constructing, forcedTypes, fhirToOpenEhrHelper, findingTheOne.getAqlPath(true) + "/");
        }
        else{
            walkThroughNodes(findingTheOne.getChildren(), String.join("/", remainingPaths),
                    constructing, forcedTypes, fhirToOpenEhrHelper, findingTheOne.getAqlPath(false) + "/");
        }
    }

    private String getCorrectOpenEhrType(final Set<String> forcedTypes,
                                         final WebTemplateNode relevantTemplateNode, StringJoiner constructing) {

        if ((forcedTypes.size() == 1 && relevantTemplateNode.getChildren().size() <= 3) || (forcedTypes.size() == 1 && relevantTemplateNode.getChildren().size() <= 5 && forcedTypes.contains("CODE_PHRASE"))) {
            return new ArrayList<>(forcedTypes).get(0);
        }

        HashSet<String> relevantTemplateNodeTypes = getchildrenRmTypes(relevantTemplateNode);
        if (relevantTemplateNode.getRmType().equals("ELEMENT")) { // need to look deeper
            return relevantTemplateNode.getChildren().stream()
                    .filter(el -> el.getId().contains("value"))
                    .map(webTemplateNode -> {
                        if (!webTemplateNode.getId().equals("value") && !FhirConnectConst.OPENEHR_CONSISTENT_SET.containsAll(relevantTemplateNodeTypes)) {
                            constructing.add(webTemplateNode.getId());
                        }
                        return webTemplateNode.getRmType();
                    })
                    .findAny()
                    .orElse(null);
        }

        final List<String> matchedByType = forcedTypes.stream()
                .filter(ft -> ft.equals(relevantTemplateNode.getRmType())).toList();
        return matchedByType.isEmpty() ? new ArrayList<>(forcedTypes).get(0) : matchedByType.get(0); // is this ok??
    }
    private HashSet<String> getchildrenRmTypes(WebTemplateNode webTemplateNode){
        return new HashSet<>(webTemplateNode.getChildren().stream().map(WebTemplateNode::getRmType).sorted().toList());
    }

    private void removeInvalidOpenEhrPath(final FhirToOpenEhrHelper fhirToOpenEhrHelper){
        String[] openEhrPathParts = fhirToOpenEhrHelper.getOpenEhrPath().split("/");
        List<String> filteredParts = new ArrayList<>();
        for (String openEhrPathPart : openEhrPathParts) {
            if (!FhirConnectConst.OPENEHR_INVALID_PATH_RM_TYPES.contains(openEhrPathPart)) {
                filteredParts.add(openEhrPathPart);
            }
        }
        fhirToOpenEhrHelper.setOpenEhrPath(String.join("/", filteredParts));
    }
}
