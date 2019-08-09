package org.grobid.core.engines;

import com.google.common.collect.Iterables;
import edu.emory.mathcs.nlp.component.tokenizer.EnglishTokenizer;
import edu.emory.mathcs.nlp.component.tokenizer.Tokenizer;
import edu.emory.mathcs.nlp.component.tokenizer.token.Token;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.DeepAnalyzer;
import org.grobid.core.data.*;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.SuperconductorsTaggingLabels;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.engines.tagging.GenericTaggerUtils;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.tokenization.LabeledTokensContainer;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.IOUtilities;
import org.grobid.core.utilities.LayoutTokensUtil;
import org.grobid.core.utilities.MeasurementOperations;
import org.grobid.core.utilities.UnitUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Singleton
public class AggregatedProcessing {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatedProcessing.class);

    private EngineParsers parsers;

    private SuperconductorsParser superconductorsParser;
    private QuantityParser quantityParser;


    public AggregatedProcessing(SuperconductorsParser superconductorsParser, QuantityParser quantityParser) {
        this.superconductorsParser = superconductorsParser;
        this.quantityParser = quantityParser;
    }

    @Inject
    public AggregatedProcessing(SuperconductorsParser superconductorsParser) {
        this.superconductorsParser = superconductorsParser;
        this.quantityParser = QuantityParser.getInstance(true);
    }

    private List<Superconductor> linkSuperconductorsWithTc(List<Superconductor> superconductors, List<Measurement> measurements, List<LayoutToken> tokens) {
        if (measurements.size() == 0)
            return superconductors;


        List<Measurement> criticalTemperatures = measurements
                .stream()
                .filter(measurement -> measurement.getQuantifiedObject() != null
                        && StringUtils.equalsIgnoreCase("Critical temperature", measurement.getQuantifiedObject().getNormalizedName()))
                .collect(Collectors.toList());

        List<Pair<Quantity, Measurement>> criticalTemperaturesFlatten = flattenTemperatures(criticalTemperatures);

        List<Measurement> assignedTemperature = new ArrayList<>();

        for (Superconductor superconductor : superconductors) {
            List<LayoutToken> layoutTokensSupercon = superconductor.getLayoutTokens();
            Pair<Integer, Integer> extremitiesSuperconductor = getExtremitiesAsIndex(tokens,
                    layoutTokensSupercon.get(0).getOffset(), layoutTokensSupercon.get(layoutTokensSupercon.size() - 1).getOffset());

            extremitiesSuperconductor = adjustExtremities(extremitiesSuperconductor, superconductor, tokens);

            List<Pair<Quantity, Measurement>> criticalTemperaturesFlattenSorted
                    = criticalTemperaturesFlatten.stream().sorted((o1, o2) -> {
                int superconductorLayoutTokenLowerOffset = superconductor.getLayoutTokens().get(0).getOffset();
                int superconductorLayoutTokenHigherOffset = superconductor.getLayoutTokens().get(superconductor.getLayoutTokens().size() - 1).getOffset()
                        + superconductor.getLayoutTokens().get(superconductor.getLayoutTokens().size() - 1).getText().length();

                double superconductorCentroidOffset = ((double) (superconductorLayoutTokenHigherOffset + superconductorLayoutTokenLowerOffset)) / 2.0;

                Quantity quantityT1 = o1.getLeft();
                int t1LowerLayoutTokenOffset = quantityT1.getLayoutTokens().get(0).getOffset();
                int t1HigherLayoutTokenOffset = quantityT1.getLayoutTokens().get(quantityT1.getLayoutTokens().size() - 1).getOffset()
                        + quantityT1.getLayoutTokens().get(quantityT1.getLayoutTokens().size() - 1).getText().length();

                double t1CentroidOffset = ((double) (t1HigherLayoutTokenOffset + t1LowerLayoutTokenOffset)) / 2.0;

                Quantity quantityT2 = o2.getLeft();
                int t2LowerLayoutTokenOffset = quantityT2.getLayoutTokens().get(0).getOffset();
                int t2HigherLayoutTokenOffset = quantityT2.getLayoutTokens().get(quantityT2.getLayoutTokens().size() - 1).getOffset()
                        + quantityT2.getLayoutTokens().get(quantityT2.getLayoutTokens().size() - 1).getText().length();

                double t2CentroidOffset = ((double) (t2HigherLayoutTokenOffset + t2LowerLayoutTokenOffset)) / 2.0;

                double distanceT1Supercon = Math.abs(t1CentroidOffset - superconductorCentroidOffset);
                double distanceT2Supercon = Math.abs(t2CentroidOffset - superconductorCentroidOffset);

                if (distanceT1Supercon > distanceT2Supercon) {
                    return 1;
                } else if (distanceT2Supercon > distanceT1Supercon) {
                    return -1;
                } else {
                    return 0;
                }
            }).collect(Collectors.toList());

            List<LayoutToken> superconductorLayoutTokenWindow = tokens.subList(extremitiesSuperconductor.getLeft(), extremitiesSuperconductor.getRight());
            int offsetWindowStart = superconductorLayoutTokenWindow.get(0).getOffset();
            LayoutToken lastToken = superconductorLayoutTokenWindow.get(superconductorLayoutTokenWindow.size() - 1);
            int offsetWindowEnd = lastToken.getOffset();

            for (Pair<Quantity, Measurement> criticalTemperature : criticalTemperaturesFlattenSorted) {
                if (assignedTemperature.contains(criticalTemperature.getRight())) {
                    continue;
                }

                List<LayoutToken> criticalTemperatureLayoutTokens = criticalTemperature.getLeft().getLayoutTokens();
                int temperatureOffsetStart = criticalTemperatureLayoutTokens.get(0).getOffset();
                int temperatureOffsetEnd = criticalTemperatureLayoutTokens.get(criticalTemperatureLayoutTokens.size() - 1).getOffset();
                if ((temperatureOffsetStart < offsetWindowStart && temperatureOffsetEnd >= offsetWindowStart)
                        || (temperatureOffsetEnd > offsetWindowEnd && temperatureOffsetStart <= offsetWindowEnd)
                        || (temperatureOffsetStart >= offsetWindowStart && temperatureOffsetEnd < offsetWindowEnd)
                        || (temperatureOffsetStart > offsetWindowStart && temperatureOffsetEnd <= offsetWindowEnd)) {
                    superconductor.setCriticalTemperatureMeasurement(criticalTemperature.getRight());
                    assignedTemperature.add(criticalTemperature.getRight());
                    break;
                }
            }
        }

        return superconductors;
    }

    /**
     * we reduce the window if going on a separate sentence
     **/
    private Pair<Integer, Integer> adjustExtremities(Pair<Integer, Integer> extremitiesSuperconductor, Superconductor superconductor, List<LayoutToken> tokens) {

        List<Token> tokensNlp4j = tokens
                .stream()
                .map(token -> {
                    Token token1 = new Token(token.getText());
                    token1.setStartOffset(token.getOffset());
                    token1.setEndOffset(token.getOffset() + token.getText().length());
                    return token1;
                })
                .collect(Collectors.toList());

        Tokenizer tokenizer = new EnglishTokenizer();
        List<List<Token>> sentences = tokenizer.segmentize(tokensNlp4j);

//        List<List<Token>> sentences = new ArrayList<>();
//        sentences.add(tokensNlp4j);

        int superconductorOffsetLower = superconductor.getLayoutTokens().get(0).getOffset();
        int superconductorOffsetHigher = superconductor.getLayoutTokens().get(superconductor.getLayoutTokens().size() - 1).getOffset();

        //In which sentence is the superconductor?
        Optional<List<Token>> superconductorSentenceOptional = sentences
                .stream()
                .filter(sentence -> {
                    int startOffset = sentence.get(0).getStartOffset();
                    int endOffset = sentence.get(sentence.size() - 1).getEndOffset();

                    return superconductorOffsetHigher < endOffset && superconductorOffsetLower > startOffset;
                })
                .findFirst();


        if (!superconductorSentenceOptional.isPresent()) {
            return extremitiesSuperconductor;
        }

        List<Token> superconductorSentence = superconductorSentenceOptional.get();


        int startSentenceOffset = superconductorSentence.get(0).getStartOffset();
        int endSentenceOffset = superconductorSentence.get(superconductorSentence.size() - 1).getStartOffset();

        //Get the layout tokens they correspond
        Optional<LayoutToken> first = tokens.stream().filter(layoutToken -> layoutToken.getOffset() == startSentenceOffset).findFirst();
        Optional<LayoutToken> last = tokens.stream().filter(layoutToken -> layoutToken.getOffset() == endSentenceOffset).findFirst();

        if (!first.isPresent() || !last.isPresent()) {
            return extremitiesSuperconductor;
        }
        int newStart = extremitiesSuperconductor.getLeft();
        int newEnd = extremitiesSuperconductor.getRight();

        int adjustedStart = tokens.indexOf(first.get());
        int adjustedEnd = tokens.indexOf(last.get());

        if (extremitiesSuperconductor.getLeft() < adjustedStart) {
            newStart = adjustedStart;
        }
        if (extremitiesSuperconductor.getRight() > adjustedEnd) {
            newEnd = adjustedEnd;
        }

        return new ImmutablePair<>(newStart, newEnd);
    }

    private List<Pair<Quantity, Measurement>> flattenTemperatures(List<Measurement> criticalTemperatures) {
        List<Pair<Quantity, Measurement>> criticalTemperaturesFlatten = new ArrayList<>();

        for (Measurement measurement : criticalTemperatures) {
            if (measurement.getType().equals(UnitUtilities.Measurement_Type.VALUE)) {
                criticalTemperaturesFlatten.add(new ImmutablePair<>(measurement.getQuantityAtomic(), measurement));
            } else if (measurement.getType().equals(UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE)) {
                List<Pair<Quantity, Measurement>> list = new ArrayList<>();

                if (measurement.getQuantityBase() != null) {
                    list.add(new ImmutablePair<>(measurement.getQuantityBase(), measurement));
                }

                if (measurement.getQuantityRange() != null) {
                    list.add(new ImmutablePair<>(measurement.getQuantityRange(), measurement));
                }

                criticalTemperaturesFlatten.addAll(list);

            } else if (measurement.getType().equals(UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX)) {
                List<Pair<Quantity, Measurement>> list = new ArrayList<>();
                if (measurement.getQuantityMost() != null) {
                    list.add(new ImmutablePair<>(measurement.getQuantityMost(), measurement));
                }

                if (measurement.getQuantityLeast() != null) {
                    list.add(new ImmutablePair<>(measurement.getQuantityLeast(), measurement));
                }

                criticalTemperaturesFlatten.addAll(list);
            } else if (measurement.getType().equals(UnitUtilities.Measurement_Type.CONJUNCTION)) {
                List<Pair<Quantity, Measurement>> collect = measurement.getQuantityList()
                        .stream()
                        .map(q -> new ImmutablePair<>(q, measurement))
                        .collect(Collectors.toList());

                criticalTemperaturesFlatten.addAll(collect);
            }
        }
        return criticalTemperaturesFlatten;
    }

    protected List<Measurement> markCriticalTemperatures(List<Measurement> temperatures, List<LayoutToken> tokens, List<Pair<String, List<LayoutToken>>> tcExpressionList) {
        MeasurementOperations measurementOperations = new MeasurementOperations();

        if (CollectionUtils.isEmpty(tcExpressionList)) {
            tcExpressionList.add(new ImmutablePair<>("Tc", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("Tc")));
            tcExpressionList.add(new ImmutablePair<>("tc", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("tc")));
            tcExpressionList.add(new ImmutablePair<>("T c", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("T c")));
            tcExpressionList.add(new ImmutablePair<>("t c", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("t c")));
        }


        List<Pair<String, List<LayoutToken>>> closedTcExpressionList = new ArrayList<>();
//        closedTcExpressionList.add(new ImmutablePair<>("at", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("at")));
//        closedTcExpressionList.add(new ImmutablePair<>("around", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("around")));
        closedTcExpressionList.add(new ImmutablePair<>("superconducts at", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("superconducts at")));
        closedTcExpressionList.add(new ImmutablePair<>("superconducts around", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("superconducts around")));
        closedTcExpressionList.add(new ImmutablePair<>("superconductivity at", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("superconductivity at")));
        closedTcExpressionList.add(new ImmutablePair<>("superconductivity around", DeepAnalyzer.getInstance().tokenizeWithLayoutToken("superconductivity around")));

        outer: for (Measurement temperature : temperatures) {
            Pair<Integer, Integer> extremities = measurementOperations.calculateQuantityExtremities(tokens, temperature, 0);

            // Check that the lower index is at least two tokens before cause I will get to check them in the following for
//            if (extremities == null || (extremities.getRight() > 1 && extremities.getLeft() < tokens.size())) {
            if (extremities == null) {
                continue;
            }

            // Now I'm searching a bit everywhere... ideally

            //Searching for more constrained expressions
            for (Pair<String, List<LayoutToken>> tcExpression : closedTcExpressionList) {
                String tcString = tcExpression.getLeft();

                List<LayoutToken> tcLayoutTokens = tcExpression.getRight();
                String tcExpressionAsString = LayoutTokensUtil.toText(tcLayoutTokens);
                int size = tcLayoutTokens.size();

                int comparisonIndex = extremities.getLeft() - 1;
                if (comparisonIndex - size >= 0) {
                    String subString = LayoutTokensUtil.toText(tokens.subList(comparisonIndex - size, comparisonIndex));
                    if (StringUtils.equals(tcExpressionAsString, subString)) {
                        QuantifiedObject quantifiedObject = new QuantifiedObject(subString, "Critical Temperature");
                        temperature.setQuantifiedObject(quantifiedObject);
                        continue outer;
                    }
                }
            }

            //Searching for more general expression extracted from the paper

            for (Pair<String, List<LayoutToken>> tcExpression : tcExpressionList) {
                String tcString = tcExpression.getLeft();
                List<LayoutToken> tcLayoutTokens = tcExpression.getRight();
                String tcExpressionAsString = LayoutTokensUtil.toText(tcLayoutTokens);

                // from the temperature to the back
                for (int i = extremities.getLeft() - 1; i >= 0; i--) {
                    LayoutToken current = tokens.get(i);

                    int size = tcLayoutTokens.size();

                    // Make sure I don't go out of bound
                    if (i >= size) {
                        String subString = LayoutTokensUtil.toText(tokens.subList(i - size, i));

                        if (StringUtils.equals(tcExpressionAsString, subString)) {
                            QuantifiedObject quantifiedObject = new QuantifiedObject(subString, "Critical Temperature");
                            temperature.setQuantifiedObject(quantifiedObject);
                            continue outer;
                        }
                    }
                }


                //From the temperature forward
                for (int i = extremities.getRight() + 1; i < tokens.size(); i++) {
                    int size = tcLayoutTokens.size();

                    // Make sure I don't go out of bound
                    if (i + size < tokens.size()) {
                        String subString = LayoutTokensUtil.toText(tokens.subList(i, i + size));

                        if (StringUtils.equals(tcExpressionAsString, subString)) {
                            QuantifiedObject quantifiedObject = new QuantifiedObject(subString, "Critical Temperature");
                            temperature.setQuantifiedObject(quantifiedObject);
                            continue outer;
                        }

                    }
                }
            }
        }

        return temperatures;
    }

    public static int WINDOW_TC = Integer.MAX_VALUE;

    /* We work with offsets (so no need to increase by size of the text) and we return indexes in the token list */
    protected Pair<Integer, Integer> getExtremitiesAsIndex(List<LayoutToken> tokens, int centroidOffsetLower, int centroidOffsetHigher) {
        return getExtremitiesAsIndex(tokens, centroidOffsetLower, centroidOffsetHigher, WINDOW_TC);
    }


    protected Pair<Integer, Integer> getExtremitiesAsIndex(List<LayoutToken> tokens, int centroidOffsetLower, int centroidOffsetHigher, int windowlayoutTokensSize) {
        int start = 0;
        int end = tokens.size() - 1;

        List<LayoutToken> centralTokens = tokens.stream()
                .filter(layoutToken -> layoutToken.getOffset() == centroidOffsetLower
                        || (layoutToken.getOffset() > centroidOffsetLower && layoutToken.getOffset() < centroidOffsetHigher))
                .collect(Collectors.toList());

        if (isNotEmpty(centralTokens)) {
            int centroidLayoutTokenIndexStart = tokens.indexOf(centralTokens.get(0));
            int centroidLayoutTokenIndexEnd = tokens.indexOf(centralTokens.get(centralTokens.size() - 1));

            if (centroidLayoutTokenIndexStart > windowlayoutTokensSize) {
                start = centroidLayoutTokenIndexStart - windowlayoutTokensSize;
            }
            if (end - centroidLayoutTokenIndexEnd > windowlayoutTokensSize) {
                end = centroidLayoutTokenIndexEnd + windowlayoutTokensSize + 1;
            }
        }

        return new ImmutablePair<>(start, end);
    }

    public List<Measurement> filterTemperature(List<Measurement> process) {
        List<Measurement> temperatures = process.stream().filter(measurement -> {
            switch (measurement.getType()) {
                case VALUE:
                    return UnitUtilities.Unit_Type.TEMPERATURE.equals(measurement.getQuantityAtomic().getType());
                case CONJUNCTION:
                    return measurement.getQuantityList()
                            .stream().anyMatch(quantity -> UnitUtilities.Unit_Type.TEMPERATURE.equals(quantity.getType()));
                case INTERVAL_BASE_RANGE:
                    return UnitUtilities.Unit_Type.TEMPERATURE.equals(measurement.getQuantityBase().getType()) ||
                            UnitUtilities.Unit_Type.TEMPERATURE.equals(measurement.getQuantityRange().getType());

                case INTERVAL_MIN_MAX:
                    return (measurement.getQuantityMost() != null && UnitUtilities.Unit_Type.TEMPERATURE.equals(measurement.getQuantityMost().getType())) ||
                            (measurement.getQuantityLeast() != null && UnitUtilities.Unit_Type.TEMPERATURE.equals(measurement.getQuantityLeast().getType()));

            }

            return false;
        }).collect(Collectors.toList());

        return temperatures;
    }

    public static List<Measurement> filterMeasurements(List<Measurement> process, List<UnitUtilities.Unit_Type> typesToBeKept) {
        List<Measurement> filteredMeasurements = process.stream().filter(measurement -> {
            switch (measurement.getType()) {
                case VALUE:
                    return typesToBeKept.contains(measurement.getQuantityAtomic().getType());
                case CONJUNCTION:
                    return measurement.getQuantityList()
                            .stream().anyMatch(quantity -> typesToBeKept.contains(quantity.getType()));
                case INTERVAL_BASE_RANGE:
                    return typesToBeKept.contains(measurement.getQuantityBase().getType()) ||
                            typesToBeKept.contains(measurement.getQuantityRange().getType());

                case INTERVAL_MIN_MAX:
                    return (measurement.getQuantityMost() != null && typesToBeKept.contains(measurement.getQuantityMost().getType())) ||
                            (measurement.getQuantityLeast() != null && typesToBeKept.contains(measurement.getQuantityLeast().getType()));

            }

            return false;
        }).collect(Collectors.toList());

        return filteredMeasurements;
    }

    public OutputResponse process(String text) {
        List<LayoutToken> tokens = DeepAnalyzer.getInstance().tokenizeWithLayoutToken(text);

        return process(tokens);
    }


    public OutputResponse process(InputStream inputStream) {
        parsers = new EngineParsers();

        OutputResponse outputResponse = new OutputResponse();
        List<Superconductor> superconductorNamesList = new ArrayList<>();
        outputResponse.setSuperconductors(superconductorNamesList);
        List<Measurement> temperaturesList = new ArrayList<>();
        outputResponse.setTemperatures(temperaturesList);
        List<Abbreviation> abbreviationList = new ArrayList<>();
        outputResponse.setAbbreviations(abbreviationList);

        Document doc = null;
        File file = null;

        OutputResponse response = new OutputResponse();

        try {
            file = IOUtilities.writeInputFile(inputStream);
            GrobidAnalysisConfig config =
                    new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
                            .build();
            DocumentSource documentSource =
                    DocumentSource.fromPdf(file, config.getStartPage(), config.getEndPage());
            doc = parsers.getSegmentationParser().processing(documentSource, config);

            // In the following, we process the relevant textual content of the document

            // for refining the process based on structures, we need to filter
            // segment of interest (e.g. header, body, annex) and possibly apply
            // the corresponding model to further filter by structure types

            // from the header, we are interested in title, abstract and keywords
            SortedSet<DocumentPiece> documentParts = doc.getDocumentPart(SegmentationLabels.HEADER);
            if (documentParts != null) {
                org.apache.commons.lang3.tuple.Pair<String, List<LayoutToken>> headerStruct = parsers.getHeaderParser().getSectionHeaderFeatured(doc, documentParts, true);
                List<LayoutToken> tokenizationHeader = headerStruct.getRight();
                String header = headerStruct.getLeft();
                String labeledResult = null;
                if ((header != null) && (header.trim().length() > 0)) {
                    labeledResult = parsers.getHeaderParser().label(header);

                    BiblioItem resHeader = new BiblioItem();
                    //parsers.getHeaderParser().processingHeaderSection(false, doc, resHeader);
                    resHeader.generalResultMapping(doc, labeledResult, tokenizationHeader);

                    // title
                    List<LayoutToken> titleTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_TITLE);
                    if (titleTokens != null) {
                        outputResponse.extendEntities(process(titleTokens));
                    }

                    // abstract
                    List<LayoutToken> abstractTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_ABSTRACT);
                    if (abstractTokens != null) {
                        outputResponse.extendEntities(process(abstractTokens));
                    }

                    // keywords
                    List<LayoutToken> keywordTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_KEYWORD);
                    if (keywordTokens != null) {
                        outputResponse.extendEntities(process(keywordTokens));
                    }
                }
            }

            // we can process all the body, in the future figure and table could be the
            // object of more refined processing
            documentParts = doc.getDocumentPart(SegmentationLabels.BODY);
            if (documentParts != null) {
                org.apache.commons.lang3.tuple.Pair<String, LayoutTokenization> featSeg = parsers.getFullTextParser().getBodyTextFeatured(doc, documentParts);

                String fulltextTaggedRawResult = null;
                if (featSeg != null) {
                    String featureText = featSeg.getLeft();
                    LayoutTokenization layoutTokenization = featSeg.getRight();

                    if (StringUtils.isNotEmpty(featureText)) {
                        fulltextTaggedRawResult = parsers.getFullTextParser().label(featureText);
                    }

                    TaggingTokenClusteror clusteror = new TaggingTokenClusteror(GrobidModels.FULLTEXT, fulltextTaggedRawResult,
                            layoutTokenization.getTokenization(), true);

                    //Iterate and exclude figures and tables
                    for (TaggingTokenCluster cluster : Iterables.filter(clusteror.cluster(),
                            new TaggingTokenClusteror
                                    .LabelTypeExcludePredicate(TaggingLabels.TABLE_MARKER, TaggingLabels.EQUATION, TaggingLabels.CITATION_MARKER,
                                    TaggingLabels.FIGURE_MARKER, TaggingLabels.EQUATION_MARKER, TaggingLabels.EQUATION_LABEL))) {

                        if (cluster.getTaggingLabel().equals(TaggingLabels.FIGURE)) {
                            //apply the figure model to only get the caption
                            final Figure processedFigure = parsers.getFigureParser()
                                    .processing(cluster.concatTokens(), cluster.getFeatureBlock());

                            List<LayoutToken> tokens = processedFigure.getCaptionLayoutTokens();

                            outputResponse.extendEntities(process(tokens));

                        } else if (cluster.getTaggingLabel().equals(TaggingLabels.TABLE)) {
                            //apply the table model to only get the caption/description
                            final Table processedTable = parsers.getTableParser().processing(cluster.concatTokens(), cluster.getFeatureBlock());
                            List<LayoutToken> tokens = processedTable.getFullDescriptionTokens();

                            outputResponse.extendEntities(process(tokens));
                        } else {
                            final List<LabeledTokensContainer> labeledTokensContainers = cluster.getLabeledTokensContainers();

                            // extract all the layout tokens from the cluster as a list
                            List<LayoutToken> tokens = labeledTokensContainers.stream()
                                    .map(LabeledTokensContainer::getLayoutTokens)
                                    .flatMap(List::stream)
                                    .collect(Collectors.toList());

                            outputResponse.extendEntities(process(tokens));

                        }

                    }
                }
            }

            // we don't process references (although reference titles could be relevant)
            // acknowledgement?

            // we can process annexes
            documentParts = doc.getDocumentPart(SegmentationLabels.ANNEX);
            if (documentParts != null) {

                List<LayoutToken> tokens = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                outputResponse.extendEntities(process(tokens));
            }

            List<Page> pages = new ArrayList<>();
            for (org.grobid.core.layout.Page page : doc.getPages()) {
                pages.add(new Page(page.getHeight(), page.getWidth()));
            }

            outputResponse.setPages(pages);

        } catch (Exception e) {
            throw new GrobidException("Cannot process pdf file: " + file.getPath(), e);
        } finally {
            IOUtilities.removeTempFile(file);
        }

        return outputResponse;
    }

    public OutputResponse process(List<LayoutToken> tokens) {
        List<Superconductor> superconductorsNames = superconductorsParser.process(tokens);

        List<Superconductor> namedEntitiesList = superconductorsNames.stream()
                .filter(s -> s.getType().equals(GenericTaggerUtils.getPlainLabel(SuperconductorsTaggingLabels.SUPERCONDUCTORS_MATERIAL_LABEL)) ||
                        s.getType().equals(GenericTaggerUtils.getPlainLabel(SuperconductorsTaggingLabels.SUPERCONDUCTORS_CLASS_LABEL)) ||
                        s.getType().equals(GenericTaggerUtils.getPlainLabel(SuperconductorsTaggingLabels.SUPERCONDUCTORS_MEASUREMENT_METHOD_LABEL))
                )
                .collect(Collectors.toList());

        List<Pair<String, List<LayoutToken>>> tcExpressionList = superconductorsNames.stream()
                .filter(s -> s.getType().equals(GenericTaggerUtils.getPlainLabel(SuperconductorsTaggingLabels.SUPERCONDUCTORS_TC_LABEL)))
                .map(tc -> new ImmutablePair<>(tc.getName(), tc.getLayoutTokens()))
                .collect(Collectors.toList());

        List<Measurement> process = quantityParser.process(tokens);
        List<Measurement> temperatures = filterTemperature(process);
        List<Measurement> temperatureList = markCriticalTemperatures(temperatures, tokens, tcExpressionList);

        List<Superconductor> linkedSuperconductors = linkSuperconductorsWithTc(namedEntitiesList, temperatureList, tokens);

        return new OutputResponse(linkedSuperconductors, temperatureList, new ArrayList<>(), new ArrayList<>());
    }
}
