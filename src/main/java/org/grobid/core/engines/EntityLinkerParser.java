package org.grobid.core.engines;

import com.google.common.collect.Iterables;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.grobid.core.GrobidModel;
import org.grobid.core.analyzers.DeepAnalyzer;
import org.grobid.core.data.Link;
import org.grobid.core.data.Span;
import org.grobid.core.data.chemDataExtractor.ChemicalSpan;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.features.FeaturesVectorEntityLinker;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.BoundingBoxCalculator;
import org.grobid.core.utilities.LayoutTokensUtil;
import org.grobid.core.utilities.UnicodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.length;
import static org.grobid.core.engines.label.SuperconductorsTaggingLabels.*;
import static org.grobid.core.utilities.MeasurementUtils.getLayoutTokenListEndOffset;
import static org.grobid.core.utilities.MeasurementUtils.getLayoutTokenListStartOffset;

@Singleton
public class EntityLinkerParser extends AbstractParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityLinkerParser.class);

    private static volatile EntityLinkerParser instance;

    private List<String> annotationLinks = new ArrayList<>();

    public static EntityLinkerParser getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    private static synchronized void getNewInstance() {
        instance = new EntityLinkerParser();
    }

    @Inject
    public EntityLinkerParser() {
        super(SuperconductorsModels.ENTITY_LINKER);
        instance = this;
        annotationLinks = Arrays.asList(SUPERCONDUCTORS_MATERIAL_LABEL, SUPERCONDUCTORS_TC_VALUE_LABEL);
    }

    public EntityLinkerParser(GrobidModel model, List<String> validLinkAnnotations) {
        super(model);
        instance = this;
        annotationLinks = validLinkAnnotations;
    }

//    public Pair<String, List<Superconductor>> generateTrainingData(List<LayoutToken> layoutTokens) {
//
//        if (isEmpty(layoutTokens))
//            return Pair.of("", new ArrayList<>());
//
//        List<Superconductor> measurements = new ArrayList<>();
//        String ress = null;
//
//        List<LayoutToken> tokens = DeepAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);
//
//        //Normalisation
//        List<LayoutToken> layoutTokensNormalised = tokens.stream().map(layoutToken -> {
//                layoutToken.setText(UnicodeUtil.normaliseText(layoutToken.getText()));
//
//                return layoutToken;
//            }
//        ).collect(Collectors.toList());
//
//
//        List<Span> mentions = chemicalAnnotator.processText(LayoutTokensUtil.toText(layoutTokensNormalised));
//        List<Boolean> listAnnotations = synchroniseLayoutTokensWithMentions(layoutTokensNormalised, mentions);
//
////        mentions.stream().forEach(m -> System.out.println(">>>>>> " + m.getText() + " --> " + m.getType().name()));
//
//        try {
//            // string representation of the feature matrix for CRF lib
//            ress = addFeatures(layoutTokensNormalised, listAnnotations);
//
//            String res = null;
//            try {
//                res = label(ress);
//            } catch (Exception e) {
//                throw new GrobidException("CRF labeling for superconductors parsing failed.", e);
//            }
//            measurements.addAll(extractResults(tokens, res));
//        } catch (Exception e) {
//            throw new GrobidException("An exception occured while running Grobid.", e);
//        }
//
//        return Pair.of(ress, measurements);
//    }

//    public Pair<String, List<Superconductor>> generateTrainingData(String text) {
//        text = text.replace("\r\t", " ");
//        text = text.replace("\n", " ");
//        text = text.replace("\t", " ");
//
//        List<LayoutToken> layoutTokens = null;
//        try {
//            layoutTokens = DeepAnalyzer.getInstance().tokenizeWithLayoutToken(text);
//        } catch (Exception e) {
//            LOGGER.error("fail to tokenize:, " + text, e);
//        }
//
//        return generateTrainingData(layoutTokens);
//
//    }

    // TODO: This cannot work because ProcessedParagraph does not hold a copy of the LayoutTokens

//    public ProcessedParagraph process(ProcessedParagraph paragraph) {
//        if(isEmpty(paragraph.getSpans())) {
//            return paragraph;
//        }
//
//        process(Token paragraph.getTokens(), paragraph.getSpans());
//
//    }

    /** THis modify the annotations list **/
    public void process(List<LayoutToken> layoutTokens, List<Span> annotations) {

        //Normalisation
        List<LayoutToken> layoutTokensPreNormalised = layoutTokens.stream()
            .map(layoutToken -> {
                    LayoutToken newOne = new LayoutToken(layoutToken);
                    newOne.setText(UnicodeUtil.normaliseText(layoutToken.getText()));
//                        .replaceAll("\\p{C}", " ")));
                    return newOne;
                }
            ).collect(Collectors.toList());

        // List<LayoutToken> for the selected segment
        List<LayoutToken> layoutTokensNormalised = DeepAnalyzer.getInstance()
            .retokenizeLayoutTokens(layoutTokensPreNormalised);

        // list of textual tokens of the selected segment
        //List<String> texts = getTexts(tokenizationParts);

        if (isEmpty(layoutTokensNormalised))
            return;

        try {
            List<Span> filteredAnnotations = annotations.stream().filter(a -> annotationLinks.contains(a.getType())).collect(Collectors.toList());
            List<ChemicalSpan> mentions = filteredAnnotations.stream().map(a -> new ChemicalSpan(a.getOffsetStart(), a.getOffsetEnd(), a.getType())).collect(Collectors.toList());
            List<String> listAnnotations = synchroniseLayoutTokensWithMentions(layoutTokensNormalised, mentions);

            // string representation of the feature matrix for CRF lib
            String ress = addFeatures(layoutTokensNormalised, listAnnotations);

            if (StringUtils.isEmpty(ress))
                return;

            // labeled result from CRF lib
            String res = null;
            try {
                res = label(ress);
            } catch (Exception e) {
                throw new GrobidException("CRF labeling for superconductors parsing failed.", e);
            }

            List<Span> localLinkedEntities = extractResults(layoutTokensNormalised, res, filteredAnnotations);

            for (Span annotation : annotations) {
                for (Span localEntity : localLinkedEntities) {
                    if (localEntity.equals(annotation)) {
                        annotation.setLinks(localEntity.getLinks());
                        annotation.setLinkable(true);
                        break;
                    }
                }
            }

        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
    }

    /**
     * Extract all occurrences of measurement/quantities from a simple piece of text.
     */
    public void process(String text, List<Span> annotations) {
        if (isBlank(text)) {
            return;
        }

        text = text.replace("\r", " ");
        text = text.replace("\n", " ");
        text = text.replace("\t", " ");

        List<LayoutToken> tokens = new ArrayList<>();
        try {
            tokens = DeepAnalyzer.getInstance().tokenizeWithLayoutToken(text);
        } catch (Exception e) {
            LOGGER.error("fail to tokenize:, " + text, e);
        }

        if (isEmpty(tokens)) {
            return;
        }
        process(tokens, annotations);
    }


    @SuppressWarnings({"UnusedParameters"})
    private String addFeatures(List<LayoutToken> tokens, List<String> annotations) {
        StringBuilder result = new StringBuilder();
        try {
            ListIterator<LayoutToken> it = tokens.listIterator();
            while (it.hasNext()) {
                int index = it.nextIndex();
                LayoutToken token = it.next();

                String text = token.getText();
                if (text.equals(" ") || text.equals("\n")) {
                    continue;
                }

                FeaturesVectorEntityLinker featuresVector =
                    FeaturesVectorEntityLinker.addFeatures(token.getText(), null, annotations.get(index));
                result.append(featuresVector.printVector());
                result.append("\n");
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
        return result.toString();
    }

    /**
     * Extract identified quantities from a labeled text.
     */
    public List<Span> extractResults(List<LayoutToken> tokens, String result, List<Span> annotations) {
        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(SuperconductorsModels.ENTITY_LINKER, result, tokens);
        List<TaggingTokenCluster> clusters = clusteror.cluster();

        int pos = 0; // position in term of characters for creating the offsets

        boolean insideLink = false;
        List<TaggingTokenCluster> detectedClusters = clusters.stream().filter(a -> !a.getTaggingLabel().getLabel().equals(OTHER_LABEL)).collect(Collectors.toList());
        if (detectedClusters.size() != annotations.size()) {
            LOGGER.warn("Linking seems not correct. Expected annotations: " + annotations.size() + ", output links: " + detectedClusters.size());
        }
        Span leftSide = null;

        for (TaggingTokenCluster cluster : clusters) {
            if (cluster == null) {
                continue;
            }

            TaggingLabel clusterLabel = cluster.getTaggingLabel();
            List<LayoutToken> theTokens = cluster.concatTokens();
            String clusterContent = LayoutTokensUtil.toText(cluster.concatTokens()).trim();
            List<BoundingBox> boundingBoxes = null;

            if (!clusterLabel.equals(SUPERCONDUCTORS_OTHER))
                boundingBoxes = BoundingBoxCalculator.calculate(cluster.concatTokens());

            int startPos = theTokens.get(0).getOffset();
            int endPos = startPos + clusterContent.length();

            if (clusterLabel.equals(ENTITY_LINKER_LEFT_ATTACHMENT)) {
                if (insideLink) {
                    LOGGER.info("Found link-left label with content " + clusterContent);

//                    Pair<Integer, Integer> extremitiesAsIndex = getExtremitiesAsIndex(tokens, startPos, endPos);
//                    List<LayoutToken> link = new ArrayList<>();
//                    for (int x = extremitiesAsIndex.getLeft(); x < extremitiesAsIndex.getRight(); x++) {
//                        link.add(tokens.get(x));
//                    }

                    int layoutTokenListStartOffset = getLayoutTokenListStartOffset(theTokens);
                    int layoutTokenListEndOffset = getLayoutTokenListEndOffset(theTokens);
                    List<Span> collect = annotations.stream().filter(a -> {
                        int supLayoutStart = getLayoutTokenListStartOffset(a.getLayoutTokens());
                        int supLayoutEnd = getLayoutTokenListEndOffset(a.getLayoutTokens());

                        return supLayoutStart == layoutTokenListStartOffset && supLayoutEnd == layoutTokenListEndOffset;
                    }).collect(Collectors.toList());

                    if (collect.size() == 1) {
                        Span rightSide = collect.get(0);
                        LOGGER.info("Link left -> " + rightSide.getText());
//                        leftSide.setType(getLinkedType(leftSide.getType()));
                        leftSide.addLink(new Link(String.valueOf(rightSide.getId()), rightSide.getText(), rightSide.getType(), "crf"));
//                        rightSide.setType(getLinkedType(rightSide.getType()));
                        rightSide.addLink(new Link(String.valueOf(leftSide.getId()), leftSide.getText(), leftSide.getType(), "crf"));
                    } else {
                        LOGGER.error("Cannot find the span corresponding to the link. Ignoring it. ");
                    }

                    insideLink = false;
                } else {
                    LOGGER.warn("Something is wrong, there is link to the left but not to the right. Ignoring it. ");
                }

            } else if (clusterLabel.equals(ENTITY_LINKER_RIGHT_ATTACHMENT)) {
                LOGGER.info("Found link-right label with content " + clusterContent);

                int layoutTokenListStartOffset = getLayoutTokenListStartOffset(theTokens);
                int layoutTokenListEndOffset = getLayoutTokenListEndOffset(theTokens);
                List<Span> spansCorrespondingToCurrentLink = annotations.stream().filter(a -> {
                    int supLayoutStart = getLayoutTokenListStartOffset(a.getLayoutTokens());
                    int supLayoutEnd = getLayoutTokenListEndOffset(a.getLayoutTokens());

                    return supLayoutStart == layoutTokenListStartOffset && supLayoutEnd == layoutTokenListEndOffset;
                }).collect(Collectors.toList());
//
//                    Pair<Integer, Integer> extremitiesAsIndex = getExtremitiesAsIndex(tokens, startPos, endPos);
//                    List<LayoutToken> link = new ArrayList<>();
//                    for (int x = extremitiesAsIndex.getLeft(); x < extremitiesAsIndex.getRight(); x++) {
//                        link.add(tokens.get(x));
//                    }
                if (!insideLink) {
                    if (spansCorrespondingToCurrentLink.size() == 1) {
                        LOGGER.info("Link right -> " + spansCorrespondingToCurrentLink.get(0).getText());
                        leftSide = spansCorrespondingToCurrentLink.get(0);
                    } else {
                        LOGGER.error("Cannot find the span corresponding to the link. Ignoring it. ");
                    }

                    insideLink = true;
                } else {
                    LOGGER.warn("Something is wrong, there is a link, but this means I should link on the left. Let's ignore the previous stored link and start from scratch. ");
                    if (spansCorrespondingToCurrentLink.size() == 1) {
                        LOGGER.info("Link right -> " + spansCorrespondingToCurrentLink.get(0).getText());
                        leftSide = spansCorrespondingToCurrentLink.get(0);
                    } else {
                        LOGGER.error("Cannot find the span corresponding to the link. Ignoring it. ");
                    }
                    insideLink = true;
                }

            } else if (clusterLabel.equals(ENTITY_LINKER_OTHER)) {

            } else {
                LOGGER.error("Warning: unexpected label in entity-linker parser: " + clusterLabel.getLabel() + " for " + clusterContent);
            }
        }

        return annotations;
    }

    private String getLinkedType(String type) {
        if (type.equals("<material>")) {
            return "<material-tc>";
        } else if (type.equals("<tcValue>")) {
            return "<temperature-tc>";
        } else {
            LOGGER.error("Something is wrong when updating the entity type. Current type :" + type);
        }
        return type;
    }

    protected List<String> synchroniseLayoutTokensWithMentions(List<LayoutToken> tokens, List<ChemicalSpan> mentions) {
        List<String> output = new ArrayList<>();

        if (CollectionUtils.isEmpty(mentions)) {
            tokens.stream().forEach(t -> output.add(OTHER_LABEL));

            return output;
        }

        mentions = mentions.stream()
            .sorted(Comparator.comparingInt(ChemicalSpan::getStart))
            .collect(Collectors.toList());

        int globalOffset = Iterables.getFirst(tokens, new LayoutToken()).getOffset();

        int mentionId = 0;
        ChemicalSpan mention = mentions.get(mentionId);

        for (LayoutToken token : tokens) {
            //normalise the offsets
            int mentionStart = globalOffset + mention.getStart();
            int mentionEnd = globalOffset + mention.getEnd();

            if (token.getOffset() < mentionStart) {
                output.add(OTHER_LABEL);
                continue;
            } else {
                if (token.getOffset() >= mentionStart
                    && token.getOffset() + length(token.getText()) <= mentionEnd) {
                    output.add(mention.getLabel());
                    continue;
                }

                if (mentionId == mentions.size() - 1) {
                    output.add(OTHER_LABEL);
                    break;
                } else {
                    output.add(OTHER_LABEL);
                    mentionId++;
                    mention = mentions.get(mentionId);
                }
            }
        }
        if (tokens.size() > output.size()) {

            for (int counter = output.size(); counter < tokens.size(); counter++) {
                output.add(OTHER_LABEL);
            }
        }

        return output;
    }
}
