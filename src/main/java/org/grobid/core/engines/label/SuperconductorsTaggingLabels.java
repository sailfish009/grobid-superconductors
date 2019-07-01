package org.grobid.core.engines.label;

import org.grobid.core.engines.SuperconductorsModels;

/**
 * Created by lfoppiano on 28/11/16.
 */
public class SuperconductorsTaggingLabels extends TaggingLabels {
    private SuperconductorsTaggingLabels() {
        super();
    }

    private static final String SUPERCONDUCTORS_CLASS_LABEL = "<class>";
    private static final String SUPERCONDUCTORS_MATERIAL_LABEL = "<material>";
    private static final String SUPERCONDUCTORS_SAMPLE_LABEL = "<sample>";
    private static final String SUPERCONDUCTORS_TC_LABEL = "<tc>";
    private static final String SUPERCONDUCTORS_TC_VALUE_LABEL = "<tcValue>";
    private static final String SUPERCONDUCTORS_PRESSURE_LABEL = "<pressure>";
    private static final String SUPERCONDUCTORS_MAGNETISATION_LABEL = "<magnetisation>";
    private static final String SUPERCONDUCTORS_SHAPE_LABEL = "<shape>";

    public static final TaggingLabel SUPERCONDUCTORS_CLASS = new TaggingLabelImpl(SuperconductorsModels.SUPERCONDUCTORS, SUPERCONDUCTORS_CLASS_LABEL);
    public static final TaggingLabel SUPERCONDUCTORS_MATERIAL = new TaggingLabelImpl(SuperconductorsModels.SUPERCONDUCTORS, SUPERCONDUCTORS_MATERIAL_LABEL);
    public static final TaggingLabel SUPERCONDUCTORS_SHAPE = new TaggingLabelImpl(SuperconductorsModels.SUPERCONDUCTORS, SUPERCONDUCTORS_SHAPE_LABEL);
    public static final TaggingLabel SUPERCONDUCTORS_SAMPLE = new TaggingLabelImpl(SuperconductorsModels.SUPERCONDUCTORS, SUPERCONDUCTORS_SAMPLE_LABEL);
    public static final TaggingLabel SUPERCONDUCTORS_TC = new TaggingLabelImpl(SuperconductorsModels.SUPERCONDUCTORS, SUPERCONDUCTORS_TC_LABEL);
    public static final TaggingLabel SUPERCONDUCTORS_TC_VALUE = new TaggingLabelImpl(SuperconductorsModels.SUPERCONDUCTORS, SUPERCONDUCTORS_TC_VALUE_LABEL);
    public static final TaggingLabel SUPERCONDUCTORS_TC_PRESSURE = new TaggingLabelImpl(SuperconductorsModels.SUPERCONDUCTORS, SUPERCONDUCTORS_PRESSURE_LABEL);
    public static final TaggingLabel SUPERCONDUCTORS_TC_MAGNETISATION = new TaggingLabelImpl(SuperconductorsModels.SUPERCONDUCTORS, SUPERCONDUCTORS_MAGNETISATION_LABEL);

    public static final TaggingLabel SUPERCONDUCTORS_OTHER = new TaggingLabelImpl(SuperconductorsModels.SUPERCONDUCTORS, OTHER_LABEL);

    private static final String ABBREVIATION_NAME_LABEL = "<acronym>";

    public static final TaggingLabel ABBREVIATION_VALUE_NAME = new TaggingLabelImpl(SuperconductorsModels.ABBREVIATIONS, ABBREVIATION_NAME_LABEL);
    public static final TaggingLabel ABBREVIATION_OTHER = new TaggingLabelImpl(SuperconductorsModels.ABBREVIATIONS, OTHER_LABEL);


    static {
        //Superconductor
        register(SUPERCONDUCTORS_CLASS);
        register(SUPERCONDUCTORS_MATERIAL);
        register(SUPERCONDUCTORS_SHAPE);
        register(SUPERCONDUCTORS_SAMPLE);
        register(SUPERCONDUCTORS_TC);
        register(SUPERCONDUCTORS_TC_VALUE);
        register(SUPERCONDUCTORS_TC_PRESSURE);
        register(SUPERCONDUCTORS_TC_MAGNETISATION);
        register(SUPERCONDUCTORS_OTHER);

        //Abbreviation
        register(ABBREVIATION_VALUE_NAME);
        register(ABBREVIATION_OTHER);

    }
}
