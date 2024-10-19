package com.medblocks.openfhir;

import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhrHelper;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;

@Component
public class OpenEhrRmWorker {

    private final OpenFhirStringUtils openFhirStringUtils;

    @Autowired
    public OpenEhrRmWorker(OpenFhirStringUtils openFhirStringUtils) {
        this.openFhirStringUtils = openFhirStringUtils;
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

            // walk through all web template nodes and enrich them with types and occurrence indexes
            walkThroughNodes(tree.getChildren(), String.join("/", split), constructing, forcedTypes, fhirToOpenEhrHelper);


            fhirToOpenEhrHelper.setOpenEhrPath(tree.getId() + "/" + fhirToOpenEhrHelper.getOpenEhrPath() + (hasSuffix ? suffix : ""));

            // we compare so that we can see if if was found within the template; if not, we don't want for it to end up in the flat json
            final String initialOpenEhrPathWithProperTreeId = tree.getId() + "/" + flat.substring(flat.indexOf("/") + 1);
            if (fhirToOpenEhrHelper.getOpenEhrPath().length() < initialOpenEhrPathWithProperTreeId.length()) {
                // means it didn't find it fully.. so it probably doesn't exist
                if (!FhirConnectConst.DV_MULTIMEDIA.equals(fhirToOpenEhrHelper.getOpenEhrType())) { // multimedia and its 'content' is a tad bit special...
                    fhirToOpenEhrHelper.setOpenEhrType(OPENEHR_TYPE_NONE);
                }
            }

            if (fhirToOpenEhrHelper.getFhirToOpenEhrHelpers() != null) {
                fixFlatWithOccurrences(fhirToOpenEhrHelper.getFhirToOpenEhrHelpers(), webTemplate);
            }
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
    public void walkThroughNodes(final List<WebTemplateNode> webTemplateNodes, final String path, final StringJoiner constructing, final Set<String> forcedTypes, final FhirToOpenEhrHelper fhirToOpenEhrHelper) {
        if (StringUtils.isBlank(path)) {
            // everything has been resolved
            fhirToOpenEhrHelper.setOpenEhrPath(constructing.toString());
            return;
        }
        final List<String> remainingPaths;
        final String[] splitOpenEhrPath = path.split("/");
        final String pathToFind = splitOpenEhrPath[0];
        if (pathToFind.startsWith("_")) {
            // we don't bother with this, can't be multiple occurrences
            constructing.add(pathToFind);
            remainingPaths = Arrays.asList(splitOpenEhrPath).subList(1, splitOpenEhrPath.length);
            walkThroughNodes(webTemplateNodes, String.join("/", remainingPaths),
                    constructing, forcedTypes, fhirToOpenEhrHelper);
            return;
        }


        final WebTemplateNode findingTheOne = webTemplateNodes.stream()
                .filter(ch -> pathToFind.equals(ch.getId()))
                .findAny()
                .orElse(null);
        if (findingTheOne == null) {
            for (WebTemplateNode itemTree : webTemplateNodes) {
                walkThroughNodes(itemTree.getChildren(), path, constructing, forcedTypes, fhirToOpenEhrHelper);
            }
            fhirToOpenEhrHelper.setOpenEhrPath(constructing.toString());
            return;
        }
        remainingPaths = Arrays.asList(splitOpenEhrPath).subList(1, splitOpenEhrPath.length);
        if (findingTheOne.isMulti()) {
            // is multiple occurrences
            constructing.add(pathToFind + "[n]");
            fhirToOpenEhrHelper.setOpenEhrType(forcedTypes == null ? findingTheOne.getRmType() : getCorrectOpenEhrType(forcedTypes, findingTheOne));
        } else {
            constructing.add(pathToFind);
            fhirToOpenEhrHelper.setOpenEhrType(forcedTypes == null ? findingTheOne.getRmType() : getCorrectOpenEhrType(forcedTypes, findingTheOne));
        }

        walkThroughNodes(findingTheOne.getChildren(), String.join("/", remainingPaths),
                constructing, forcedTypes, fhirToOpenEhrHelper);
    }

    private String getCorrectOpenEhrType(final Set<String> forcedTypes,
                                         final WebTemplateNode relevantTemplateNode) {
        if (forcedTypes.size() == 1) {
            return new ArrayList<>(forcedTypes).get(0);
        }
        if (relevantTemplateNode.getRmType().equals("ELEMENT")) { // need to look deeper
            return relevantTemplateNode.getChildren().stream()
                    .filter(el -> "value".equals(el.getId()))
                    .map(WebTemplateNode::getRmType)
                    .findAny()
                    .orElse(null);
        }
        final List<String> matchedByType = forcedTypes.stream()
                .filter(ft -> ft.equals(relevantTemplateNode.getRmType())).toList();
        return matchedByType.isEmpty() ? new ArrayList<>(forcedTypes).get(0) : matchedByType.get(0); // is this ok??
    }
}
