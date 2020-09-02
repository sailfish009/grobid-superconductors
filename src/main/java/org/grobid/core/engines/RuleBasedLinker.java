package org.grobid.core.engines;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import jep.Interpreter;
import jep.JepException;
import jep.SharedInterpreter;
import org.apache.commons.collections4.CollectionUtils;
import org.grobid.core.data.Link;
import org.grobid.core.data.ProcessedParagraph;
import org.grobid.core.data.Span;
import org.grobid.core.layout.BoundingBox;
import org.grobid.service.configuration.GrobidSuperconductorsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

import static org.grobid.core.data.Link.TC_PRESSURE_TYPE;
import static org.grobid.core.engines.label.SuperconductorsTaggingLabels.SUPERCONDUCTORS_MATERIAL_LABEL;

@Singleton
public class RuleBasedLinker {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuleBasedLinker.class);

    private GrobidSuperconductorsConfiguration configuration;
    private final JepEngine engineController;
    private boolean disabled = false;

    @Inject
    public RuleBasedLinker(GrobidSuperconductorsConfiguration configuration, JepEngine engineController) {
        this.configuration = configuration;
        this.engineController = engineController;
        init();
    }


    public void init() {
        if (this.engineController.getDisabled()) {
            LOGGER.info("The JEP engine wasn't initialised correct. Disabling all dependent modules. ");
            this.disabled = true;
            return;
        }
        try {
            try (Interpreter interp = new SharedInterpreter()) {
                interp.exec("import numpy as np");
                interp.exec("import spacy");
                interp.exec("from linking_module import RuleBasedLinker");
            }

        } catch (Exception e) {
            LOGGER.error("Loading JEP native library failed. The linking will be disabled.", e);
            this.disabled = true;
        }
    }

    public List<ProcessedParagraph> process(ProcessedParagraph paragraph) {
        if (CollectionUtils.isEmpty(paragraph.getSpans()) || disabled) {
            return Collections.singletonList(paragraph);
        }

        //Take out the bounding boxes
        Map<String, List<BoundingBox>> backupBoundingBoxes = new HashMap<>();
        paragraph.getSpans().forEach(s -> {
            backupBoundingBoxes.put(String.valueOf(s.getId()), s.getBoundingBoxes());
            s.setBoundingBoxes(new ArrayList<>());
        });

        //Take out the attributes
        Map<String, Map<String, String>> backupAttributes = new HashMap<>();
        paragraph.getSpans().forEach(s -> backupAttributes.put(String.valueOf(s.getId()), s.getAttributes()));

        // We convert the initial object in JSON to avoid problems
        Writer jsonWriter = new StringWriter();
        ObjectMapper oMapper = new ObjectMapper();
        try {
            oMapper.writeValue(jsonWriter, paragraph);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String jsonTarget = jsonWriter.toString();

        try (Interpreter interp = new SharedInterpreter()) {
            interp.exec("from linking_module import RuleBasedLinker");
            interp.set("paragraph", jsonTarget);
            interp.exec("ruleBasedLinker_material_tc = RuleBasedLinker()");
            interp.exec("extracted_links_materialTc = ruleBasedLinker_material_tc.process_paragraph_json(paragraph)");
            String extracted_links_materialTc_Json = interp.getValue("extracted_links_materialTc", String.class);

            interp.exec("ruleBasedLinker_tc_pressure = RuleBasedLinker(source='<pressure>', destination='<tcValue>')");
            interp.exec("extracted_links_tcPressure = ruleBasedLinker_tc_pressure.process_paragraph_json(paragraph)");
            String extracted_links_tcPressure_Json = interp.getValue("extracted_links_tcPressure", String.class);

            try {
                TypeReference<List<ProcessedParagraph>> mapType = new TypeReference<List<ProcessedParagraph>>() {
                };
                List<ProcessedParagraph> processedMaterialTc = oMapper.readValue(extracted_links_materialTc_Json, mapType);

                // put the bounding boxes back where they were
                processedMaterialTc.stream()
                    .forEach(p -> {
                        p.getSpans().stream()
                            .forEach(s -> {
                                s.setBoundingBoxes(backupBoundingBoxes.get(String.valueOf(s.getId())));
                                s.setAttributes(backupAttributes.get(String.valueOf(s.getId())));
                            });
                    });

                List<ProcessedParagraph> processedTcPressure = oMapper.readValue(extracted_links_tcPressure_Json, mapType);

                // put the bounding boxes back where they were
                Map<String, Span> spans = new HashMap<>();

                processedTcPressure.stream()
                    .forEach(p -> {
                        p.getSpans().stream()
                            .forEach(s -> {
                                s.setBoundingBoxes(backupBoundingBoxes.get(String.valueOf(s.getId())));
                                s.setAttributes(backupAttributes.get(String.valueOf(s.getId())));
                                spans.put(s.getId(), s);
                            });
                    });


                processedMaterialTc.stream().forEach(p -> {
                    p.getSpans().stream().forEach(s -> {
                        Span correspondingSpan = spans.get(s.getId());
                        if (correspondingSpan != null) {
                            List<Link> collect = correspondingSpan.getLinks().stream()
                                .filter(f -> !f.getType().equals("crf"))
                                .collect(Collectors.toList());
                            s.addLinks(collect);
                            return;
                        }
                    });
                });

                return processedMaterialTc;

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (JepException e) {
            e.printStackTrace();
        }

        return Arrays.asList(paragraph);
    }
}