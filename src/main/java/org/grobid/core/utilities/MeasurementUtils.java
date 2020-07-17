package org.grobid.core.utilities;

import com.google.common.collect.Iterables;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.grobid.core.data.Measurement;
import org.grobid.core.data.Quantity;
import org.grobid.core.data.Span;
import org.grobid.core.engines.QuantitiesModels;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class MeasurementUtils {
    public static int WINDOW_TC = 20;

    /* We work with offsets (so no need to increase by size of the text) and we return indexes in the token list */
//    protected static Pair<Integer, Integer> getExtremitiesAsIndex(List<LayoutToken> tokens, int centroidOffsetLower, int centroidOffsetHigher) {
//        return getExtremitiesAsIndex(tokens, centroidOffsetLower, centroidOffsetHigher, WINDOW_TC);
//    }

    public static int getLayoutTokenListStartOffset(List<LayoutToken> tokens) {
        if (isEmpty(tokens)) {
            return 0;
        }

        LayoutToken firstToken = tokens.get(0);
        return firstToken.getOffset();
    }

    public static int getLayoutTokenListEndOffset(List<LayoutToken> tokens) {
        if (isEmpty(tokens)) {
            return 0;
        }

        LayoutToken lastToken = tokens.get(tokens.size() - 1);
        return lastToken.getOffset() + lastToken.getText().length();
    }

    public static int getLayoutTokenEndOffset(LayoutToken layoutToken) {
        return layoutToken.getOffset() + layoutToken.getText().length();
    }

    public static int getLayoutTokenStartOffset(LayoutToken layoutToken) {
        return layoutToken.getOffset();
    }

    /**
     * Get the index of the layout token referring to the to the startOffset, endOffset tokens in the
     * supplied token list.
     * <p>
     * The returned are (start, end) with end excluded (same as usual java stuff).
     */
    public static Pair<Integer, Integer> getExtremitiesAsIndex(List<LayoutToken> tokens, int startOffset, int endOffset) {

        if (isEmpty(tokens)) {
            return Pair.of(0, 0);
        }

        if (startOffset > getLayoutTokenListEndOffset(tokens) || endOffset < getLayoutTokenListStartOffset(tokens)) {
            throw new IllegalArgumentException("StartOffset and endOffset are outside the offset boundaries of the layoutTokens. ");
        }
        int start = 0;
        int end = tokens.size() - 1;

        List<LayoutToken> centralTokens = tokens.stream()
            .filter(layoutToken -> (layoutToken.getOffset() >= startOffset && getLayoutTokenEndOffset(layoutToken) <= endOffset)
                    || (layoutToken.getOffset() >= startOffset && layoutToken.getOffset() < endOffset
                    || (getLayoutTokenEndOffset(layoutToken) > startOffset && getLayoutTokenEndOffset(layoutToken) < endOffset)
                )
            )
            .collect(Collectors.toList());

        int layoutTokenIndexStart = start;
        int layoutTokenIndexEnd = end;

        if (isNotEmpty(centralTokens)) {
            layoutTokenIndexStart = tokens.indexOf(centralTokens.get(0));
            layoutTokenIndexEnd = tokens.indexOf(Iterables.getLast(centralTokens));
        }

        // Making it exclusive as any java stuff
        return Pair.of(layoutTokenIndexStart, layoutTokenIndexEnd + 1);
    }

    /**
     * use getExtremitiesAsIndex(List<LayoutToken> tokens, int startOffset, int endOffset)
     **/
    @Deprecated
    protected static Pair<Integer, Integer> getExtremitiesAsIndex(List<LayoutToken> tokens, int centroidOffsetLower, int centroidOffsetHigher, int windowlayoutTokensSize) {
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

        return new ImmutablePair<>(start, end);
    }

    /**
     * Return measurement extremities as the index of the layout token. The upper index is exclusive.
     * It's useful if we want to combine measurement and layoutToken content.
     */
    public static Pair<Integer, Integer> calculateExtremitiesAsIndex(Measurement measurement, List<LayoutToken> tokens) {
        Pair<Integer, Integer> extremities = null;
        switch (measurement.getType()) {
            case VALUE:
                Quantity quantity = measurement.getQuantityAtomic();
                List<LayoutToken> layoutTokens = quantity.getLayoutTokens();

                extremities = getExtremitiesAsIndex(tokens, getLayoutTokenListStartOffset(layoutTokens), getLayoutTokenListEndOffset(layoutTokens));

                break;
            case INTERVAL_BASE_RANGE:
                if (measurement.getQuantityBase() != null && measurement.getQuantityRange() != null) {
                    Quantity quantityBase = measurement.getQuantityBase();
                    Quantity quantityRange = measurement.getQuantityRange();

                    extremities = getExtremitiesAsIndex(tokens, getLayoutTokenListStartOffset(quantityBase.getLayoutTokens()), getLayoutTokenListEndOffset(quantityRange.getLayoutTokens()));
                } else {
                    Quantity quantityTmp;
                    if (measurement.getQuantityBase() == null) {
                        quantityTmp = measurement.getQuantityRange();
                    } else {
                        quantityTmp = measurement.getQuantityBase();
                    }

                    extremities = getExtremitiesAsIndex(tokens, getLayoutTokenListStartOffset(quantityTmp.getLayoutTokens()), getLayoutTokenListEndOffset(quantityTmp.getLayoutTokens()));
                }

                break;

            case INTERVAL_MIN_MAX:
                if (measurement.getQuantityLeast() != null && measurement.getQuantityMost() != null) {
                    Quantity quantityLeast = measurement.getQuantityLeast();
                    Quantity quantityMost = measurement.getQuantityMost();

                    extremities = getExtremitiesAsIndex(tokens, getLayoutTokenListStartOffset(quantityLeast.getLayoutTokens()), getLayoutTokenListEndOffset(quantityMost.getLayoutTokens()));
                } else {
                    Quantity quantityTmp;
                    if (measurement.getQuantityLeast() == null) {
                        quantityTmp = measurement.getQuantityMost();
                    } else {
                        quantityTmp = measurement.getQuantityLeast();
                    }

                    extremities = getExtremitiesAsIndex(tokens, getLayoutTokenListStartOffset(quantityTmp.getLayoutTokens()), getLayoutTokenListEndOffset(quantityTmp.getLayoutTokens()));
                }
                break;

            case CONJUNCTION:
                List<Quantity> quantityList = measurement.getQuantityList();
                Quantity firstQuantity = quantityList.get(0);
                if (quantityList.size() > 1) {
                    Quantity quantityLast = quantityList.get(quantityList.size() - 1);
                    extremities = getExtremitiesAsIndex(tokens, getLayoutTokenListStartOffset(firstQuantity.getLayoutTokens()), getLayoutTokenListEndOffset(quantityLast.getLayoutTokens()));
                } else {
                    extremities = getExtremitiesAsIndex(tokens, getLayoutTokenListStartOffset(firstQuantity.getLayoutTokens()), getLayoutTokenListEndOffset(firstQuantity.getLayoutTokens()));
                }

                break;
        }
        return extremities;
    }

    /**
     * Return the offset of the measurement - useful for matching into sentences or lexicons
     */
    public static OffsetPosition calculateExtremitiesAsOffsets(Measurement measurement) {
        List<OffsetPosition> offsets = new ArrayList<>();

        switch (measurement.getType()) {
            case VALUE:
                CollectionUtils.addAll(offsets, QuantityOperations.getOffsets(measurement.getQuantityAtomic()));
                break;
            case INTERVAL_BASE_RANGE:
                CollectionUtils.addAll(offsets, QuantityOperations.getOffsets(measurement.getQuantityBase()));
                CollectionUtils.addAll(offsets, QuantityOperations.getOffsets(measurement.getQuantityRange()));

                break;
            case INTERVAL_MIN_MAX:
                CollectionUtils.addAll(offsets, QuantityOperations.getOffsets(measurement.getQuantityLeast()));
                CollectionUtils.addAll(offsets, QuantityOperations.getOffsets(measurement.getQuantityMost()));
                break;

            case CONJUNCTION:
                CollectionUtils.addAll(offsets, QuantityOperations.getOffsets(measurement.getQuantityList()));

                break;
        }
        return QuantityOperations.getContainingOffset(offsets);
    }

    /**
     * Given a list of measurements, retain only the type requested in the second parameter
     **/
    public static List<Measurement> filterMeasurementsByUnitValue(List<Measurement> process, List<String> valuesToBeKept) {
        if (isEmpty(process)) {
            return process;
        }
        List<Measurement> filteredMeasurements = process.stream().filter(measurement -> {
            switch (measurement.getType()) {
                case VALUE:
                    if (measurement.getQuantityAtomic().getRawUnit() != null) {
                        return valuesToBeKept.stream().anyMatch(measurement.getQuantityAtomic().getRawUnit().getRawName()::equalsIgnoreCase);
                    }
                    return false;
                case CONJUNCTION:
                    return measurement.getQuantityList()
                        .stream()
                        .anyMatch(quantity -> {
                                if (quantity.getRawUnit() != null) {
                                    return valuesToBeKept.stream().anyMatch(quantity.getRawUnit().getRawName()::equalsIgnoreCase);
                                }
                                return false;
                            }
                        );
                case INTERVAL_BASE_RANGE:
                    boolean baseMatch = false;
                    if (measurement.getQuantityBase() != null && measurement.getQuantityBase().getRawUnit() != null) {
                        baseMatch = valuesToBeKept.stream().anyMatch(measurement.getQuantityBase().getRawUnit().getRawName()::equalsIgnoreCase);
                    }

                    boolean rangeMatch = false;
                    if (measurement.getQuantityRange() != null && measurement.getQuantityRange().getRawUnit() != null) {
                        rangeMatch = valuesToBeKept.stream().anyMatch(measurement.getQuantityRange().getRawUnit().getRawName()::equalsIgnoreCase);
                    }

                    if (baseMatch || rangeMatch) {
                        return true;
                    } else {
                        return false;
                    }
                case INTERVAL_MIN_MAX:
                    boolean mostMatch = false;
                    if (measurement.getQuantityMost() != null && measurement.getQuantityMost().getRawUnit() != null) {
                        mostMatch = valuesToBeKept.stream().anyMatch(measurement.getQuantityMost().getRawUnit().getRawName()::equalsIgnoreCase);
                    }

                    boolean leastMatch = false;
                    if (measurement.getQuantityLeast() != null && measurement.getQuantityLeast().getRawUnit() != null) {
                        leastMatch = valuesToBeKept.stream().anyMatch(measurement.getQuantityLeast().getRawUnit().getRawName()::equalsIgnoreCase);
                    }

                    if (mostMatch || leastMatch) {
                        return true;
                    } else {
                        return false;
                    }
            }

            return false;
        }).collect(Collectors.toList());

        return filteredMeasurements;
    }


    /**
     * Given a list of measurements, retain only the type requested in the second parameter
     **/
    public static List<Measurement> filterMeasurementsByUnitType(List<Measurement> measurementList, List<UnitUtilities.Unit_Type> typesToBeKept) {
        List<Measurement> filteredMeasurements = measurementList.stream().filter(measurement -> {
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

    /**
     * Given a list of measurements, retain only the type requested in the second parameter
     **/
    public static List<Measurement> filterMeasurementsByUnitType(List<Measurement> measurementList, UnitUtilities.Unit_Type typeToBeKept) {
        return MeasurementUtils.filterMeasurementsByUnitType(measurementList, Arrays.asList(typeToBeKept));
    }

    /**
     * Transform the complex Measurement object in a list of Pair(Quantity, Measurement)
     */
    public static List<Pair<Quantity, Measurement>> flattenMeasurements(List<Measurement> measurements) {
        return measurements.stream().flatMap(m -> MeasurementUtils.flattenMeasurement(m).stream()).collect(Collectors.toList());
    }

    public static List<Pair<Quantity, Measurement>> flattenMeasurement(Measurement measurement) {
        List<Pair<Quantity, Measurement>> results = new ArrayList<>();

        if (measurement.getType().equals(UnitUtilities.Measurement_Type.VALUE)) {
            results.add(Pair.of(measurement.getQuantityAtomic(), measurement));
        } else if (measurement.getType().equals(UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE)) {

            if (measurement.getQuantityBase() != null) {
                results.add(Pair.of(measurement.getQuantityBase(), measurement));
            }

            if (measurement.getQuantityRange() != null) {
                results.add(Pair.of(measurement.getQuantityRange(), measurement));
            }

        } else if (measurement.getType().equals(UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX)) {
            if (measurement.getQuantityLeast() != null) {
                results.add(Pair.of(measurement.getQuantityLeast(), measurement));
            }

            if (measurement.getQuantityMost() != null) {
                results.add(Pair.of(measurement.getQuantityMost(), measurement));
            }

        } else if (measurement.getType().equals(UnitUtilities.Measurement_Type.CONJUNCTION)) {
            List<Pair<Quantity, Measurement>> collect = measurement.getQuantityList()
                .stream()
                .map(q -> Pair.of(q, measurement))
                .collect(Collectors.toList());

            results.addAll(collect);
        }

        return results;
    }

    public static Span toSpan(Measurement measurement, List<LayoutToken> tokens, String outputLabel) {

        List<Quantity> quantityList = QuantityOperations.toQuantityList(measurement);
        List<LayoutToken> layoutTokens = QuantityOperations.getLayoutTokens(quantityList);

        int start = layoutTokens.get(0).getOffset();
        int end = Iterables.getLast(layoutTokens).getOffset();

        // Token start and end
        Pair<Integer, Integer> extremitiesQuantityAsIndex = MeasurementUtils
            .getExtremitiesAsIndex(tokens,
                Math.min(start, end), Math.max(start, end) + 1);

        //Offset start and end
        List<OffsetPosition> offsets = QuantityOperations.getOffsets(quantityList);
        List<OffsetPosition> sortedOffsets = offsets.stream()
            .sorted(Comparator.comparingInt(o -> o.start))
            .collect(Collectors.toList());

        List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(layoutTokens);

        int lowerOffset = sortedOffsets.get(0).start;
        int higherOffset = Iterables.getLast(sortedOffsets).end;

        String type = outputLabel;
        String source = QuantitiesModels.QUANTITIES.getModelName();

        return new Span(LayoutTokensUtil.toText(tokens.subList(extremitiesQuantityAsIndex.getLeft(), extremitiesQuantityAsIndex.getRight())),
                type,
                source,
                lowerOffset - tokens.get(0).getOffset(),
                higherOffset - tokens.get(0).getOffset(),
                extremitiesQuantityAsIndex.getLeft(), extremitiesQuantityAsIndex.getRight(),
                boundingBoxes);
    }
}
