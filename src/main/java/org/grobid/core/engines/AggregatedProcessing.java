package org.grobid.core.engines;

import com.google.common.collect.Iterables;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.grobid.core.GrobidModels;
import org.grobid.core.data.*;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.tokenization.LabeledTokensContainer;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.IOUtilities;
import org.grobid.core.utilities.LayoutTokensUtil;
import org.grobid.core.utilities.UnitUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.emptyCollection;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Singleton
public class AggregatedProcessing {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatedProcessing.class);

    private EngineParsers parsers;

    private SuperconductorsParser superconductorsParser;
    private AbbreviationsParser abbreviationsParser;
    private QuantityParser quantityParser;


    public AggregatedProcessing(SuperconductorsParser superconductorsParser, AbbreviationsParser abbreviationsParser, QuantityParser quantityParser) {
        this.superconductorsParser = superconductorsParser;
        this.abbreviationsParser = abbreviationsParser;
        this.quantityParser = quantityParser;
    }

    @Inject
    public AggregatedProcessing(SuperconductorsParser superconductorsParser, AbbreviationsParser abbreviationsParser) {
        this.superconductorsParser = superconductorsParser;
        this.abbreviationsParser = abbreviationsParser;
        this.quantityParser = QuantityParser.getInstance(true);
    }

    private List<Measurement> processQuantity(String text) {
        List<Measurement> process = quantityParser.process(text);

        return filterTemperature(process);
    }

    private List<Superconductor> linkSuperconductorsWithTc(List<Superconductor> superconductors, List<Measurement> measurements, List<LayoutToken> tokens) {
        if (measurements.size() == 0)
            return superconductors;


        List<Measurement> criticalTemperatures = measurements
                .stream()
                .filter(measurement -> measurement.getQuantifiedObject() != null
                        && StringUtils.equalsIgnoreCase("Critical temperature", measurement.getQuantifiedObject().getNormalizedName()))
                .collect(Collectors.toList());

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

        for (Superconductor superconductor : superconductors) {
            List<LayoutToken> layoutTokensSupercon = superconductor.getLayoutTokens();
            Pair<Integer, Integer> extremitiesSuperconductor = getExtremitiesAsIndex(tokens,
                    layoutTokensSupercon.get(0).getOffset(), layoutTokensSupercon.get(layoutTokensSupercon.size() - 1).getOffset());

            List<LayoutToken> layoutTokenWindow = tokens.subList(extremitiesSuperconductor.getLeft(), extremitiesSuperconductor.getRight());
            int offsetWindowStart = layoutTokenWindow.get(0).getOffset();
            LayoutToken lastToken = layoutTokenWindow.get(layoutTokenWindow.size() - 1);
            int offsetWindowEnd = lastToken.getOffset();

            for (Pair<Quantity, Measurement> criticalTemperature : criticalTemperaturesFlatten) {

                List<LayoutToken> criticalTemperatureLayoutTokens = criticalTemperature.getLeft().getLayoutTokens();
                int temperatureOffsetStart = criticalTemperatureLayoutTokens.get(0).getOffset();
                int temperatureOffsetEnd = criticalTemperatureLayoutTokens.get(criticalTemperatureLayoutTokens.size() - 1).getOffset();
                if ((temperatureOffsetStart < offsetWindowStart && temperatureOffsetEnd >= offsetWindowStart)
                        || (temperatureOffsetEnd > offsetWindowEnd && temperatureOffsetStart <= offsetWindowEnd)
                        || (temperatureOffsetStart >= offsetWindowStart && temperatureOffsetEnd < offsetWindowEnd)
                        || (temperatureOffsetStart > offsetWindowStart && temperatureOffsetEnd <= offsetWindowEnd)) {
                    superconductor.setCriticalTemperature(criticalTemperature.getRight());
                    break;
                }
            }
        }

        return superconductors;
    }

    private List<Measurement> processQuantity(List<LayoutToken> tokens) {
        List<Measurement> process = quantityParser.process(tokens);

        List<Measurement> temperatures = filterTemperature(process);

        for (Measurement temperature : temperatures) {
            Quantity quantity = null;
            Pair<Integer, Integer> extremities = null;
            switch (temperature.getType()) {
                case VALUE:
                    quantity = temperature.getQuantityAtomic();
                    List<LayoutToken> layoutTokens = quantity.getLayoutTokens();

                    extremities = getExtremitiesAsIndex(tokens, layoutTokens.get(0).getOffset(), layoutTokens.get(layoutTokens.size() - 1).getOffset());


                    break;
                case INTERVAL_BASE_RANGE:
                    if (temperature.getQuantityBase() != null && temperature.getQuantityRange() != null) {
                        Quantity quantityBase = temperature.getQuantityBase();
                        Quantity quantityRange = temperature.getQuantityRange();

                        extremities = getExtremitiesAsIndex(tokens, quantityBase.getLayoutTokens().get(0).getOffset(), quantityRange.getLayoutTokens().get(quantityRange.getLayoutTokens().size() - 1).getOffset());
                    } else {
                        Quantity quantityTmp;
                        if (temperature.getQuantityBase() == null) {
                            quantityTmp = temperature.getQuantityRange();
                        } else {
                            quantityTmp = temperature.getQuantityBase();
                        }

                        extremities = getExtremitiesAsIndex(tokens, quantityTmp.getLayoutTokens().get(0).getOffset(), quantityTmp.getLayoutTokens().get(0).getOffset());
                    }

                    break;

                case INTERVAL_MIN_MAX:
                    if (temperature.getQuantityLeast() != null && temperature.getQuantityMost() != null) {
                        Quantity quantityLeast = temperature.getQuantityLeast();
                        Quantity quantityMost = temperature.getQuantityMost();

                        extremities = getExtremitiesAsIndex(tokens, quantityLeast.getLayoutTokens().get(0).getOffset(), quantityMost.getLayoutTokens().get(quantityMost.getLayoutTokens().size() - 1).getOffset());
                    } else {
                        Quantity quantityTmp;
                        if (temperature.getQuantityLeast() == null) {
                            quantityTmp = temperature.getQuantityMost();
                        } else {
                            quantityTmp = temperature.getQuantityLeast();
                        }

                        extremities = getExtremitiesAsIndex(tokens, quantityTmp.getLayoutTokens().get(0).getOffset(), quantityTmp.getLayoutTokens().get(quantityTmp.getLayoutTokens().size() - 1).getOffset());
                    }
                    break;

                case CONJUNCTION:
                    List<Quantity> quantityList = temperature.getQuantityList();
                    if (quantityList.size() > 1) {
                        extremities = getExtremitiesAsIndex(tokens, quantityList.get(0).getLayoutTokens().get(0).getOffset(), quantityList.get(quantityList.size() - 1).getLayoutTokens().get(0).getOffset());
                    } else {
                        extremities = getExtremitiesAsIndex(tokens, quantityList.get(0).getLayoutTokens().get(0).getOffset(), quantityList.get(0).getLayoutTokens().get(0).getOffset());
                    }

                    break;
            }


            if (extremities == null) {
                continue;
            }
            List<LayoutToken> temperatureWindow = tokens.subList(extremities.getLeft(), extremities.getRight());

            if (isNotEmpty(temperatureWindow)) {
                ListIterator<LayoutToken> it = temperatureWindow.listIterator();

                // searching for Tc
                while (it.hasNext()) {
                    LayoutToken token = it.next();
                    if (StringUtils.equalsAny(token.getText(), "temperature")) {
                        QuantifiedObject quantifiedObject = new QuantifiedObject(token.getText());
                        temperature.setQuantifiedObject(quantifiedObject);
                    } else if (token.getText().equalsIgnoreCase("tc") || ((token.getText().equalsIgnoreCase("t")) && it.nextIndex() + 1 < temperatureWindow.size() && (temperatureWindow.get(it.nextIndex() + 1).getText().equalsIgnoreCase("c")))) {
                        String rawName = LayoutTokensUtil.toText(Arrays.asList(token, temperatureWindow.get(it.nextIndex()), temperatureWindow.get(it.nextIndex() + 1)));
                        QuantifiedObject quantifiedObject = new QuantifiedObject(rawName, "Critical Temperature");
                        temperature.setQuantifiedObject(quantifiedObject);
                    } else if (token.getText().equalsIgnoreCase("superconductivity") && temperatureWindow.get(it.nextIndex() + 1).getText().equalsIgnoreCase("at")) {
                        String rawName = LayoutTokensUtil.toText(Arrays.asList(token, temperatureWindow.get(it.nextIndex()), temperatureWindow.get(it.nextIndex() + 1)));
                        QuantifiedObject quantifiedObject = new QuantifiedObject(rawName, "Critical Temperature");
                        temperature.setQuantifiedObject(quantifiedObject);
                    }
                }
            }


        }

        return temperatures;
    }

    public static int WINDOW_TC = 20;

    /* We work with offsets (so no need to increase by size of the text) and we return indexes in the token list */
    protected Pair<Integer, Integer> getExtremitiesAsIndex(List<LayoutToken> tokens, int centroidOffsetLower, int centroidOffsetHigher) {
        return getExtremitiesAsIndex(tokens, centroidOffsetLower, centroidOffsetHigher, WINDOW_TC);
    }


    protected Pair<Integer, Integer> getExtremitiesAsIndex(List<LayoutToken> tokens, int centroidOffsetLower, int centroidOffsetHigher, int windowlayoutTokensSize) {
        int start = 0;
        int end = tokens.size() - 1;

        List<LayoutToken> centralTokens = tokens.stream().filter(layoutToken -> layoutToken.getOffset() == centroidOffsetLower || (layoutToken.getOffset() > centroidOffsetLower && layoutToken.getOffset() < centroidOffsetHigher)).collect(Collectors.toList());

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

        return new ImmutablePair(start, end);
    }

    private List<Measurement> filterTemperature(List<Measurement> process) {
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

    public OutputResponse process(String text) {
        OutputResponse output = new OutputResponse();
        output.setTemperatures(processQuantity(text));
        output.setSuperconductors(superconductorsParser.process(text));
        output.setAbbreviations(abbreviationsParser.process(text));

        return output;
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
        List<Measurement> temperatureList = processQuantity(tokens);
        List<Abbreviation> abbreviationsList = abbreviationsParser.process(tokens);

        List<Superconductor> linkedSuperconductors = linkSuperconductorsWithTc(superconductorsNames, temperatureList, tokens);

        return new OutputResponse(linkedSuperconductors, temperatureList, abbreviationsList);
    }
}
