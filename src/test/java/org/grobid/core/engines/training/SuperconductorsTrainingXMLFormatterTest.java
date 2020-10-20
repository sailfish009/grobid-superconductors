package org.grobid.core.engines.training;

import nu.xom.Element;
import org.grobid.core.analyzers.DeepAnalyzer;
import org.grobid.core.data.DocumentBlock;
import org.grobid.core.data.Span;
import org.grobid.core.layout.LayoutToken;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.grobid.core.engines.label.SuperconductorsTaggingLabels.*;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SuperconductorsTrainingXMLFormatterTest {

    SuperconductorsTrainingXMLFormatter target;

    @Before
    public void setUp() throws Exception {
        target = new SuperconductorsTrainingXMLFormatter();
    }

    @Test
    public void testTrainingData_value() throws Exception {
        List<Span> superconductorList = new ArrayList<>();
        Span superconductor = new Span();
        superconductor.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        superconductor.setOffsetStart(19);
        superconductor.setOffsetEnd(30);
        superconductor.setText("(TMTSF)2PF6");

        String text = "The Bechgaard salt (TMTSF)2PF6 (TMTSF = tetra- methyltetraselenafulvalene) was";

        superconductorList.add(superconductor);

        Element out = target.trainingExtraction(superconductorList, DeepAnalyzer.getInstance().tokenizeWithLayoutToken(text));
        assertThat(out.toXML(), is("<p xmlns=\"http://www.tei-c.org/ns/1.0\">The Bechgaard salt <material>(TMTSF)2PF6</material> (TMTSF = tetra- methyltetraselenafulvalene) was</p>"));
    }


    @Test
    public void testTrainingData2_value() throws Exception {
        String text = "Specific-Heat Study of Superconducting and Normal States in FeSe 1-x Te x (0.6 ≤ x ≤ 1) Single Crystals: Strong-Coupling Superconductivity, Strong Electron-Correlation, and Inhomogeneity ";

        List<LayoutToken> layoutTokens = DeepAnalyzer.getInstance().tokenizeWithLayoutToken(text);

        layoutTokens.stream().forEach(l -> {
            l.setOffset(l.getOffset() + 4);
        });

        List<Span> superconductorList = new ArrayList<>();
        Span superconductor = new Span();
        superconductor.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        superconductor.setOffsetStart(64);
        superconductor.setOffsetEnd(77);
        superconductor.setText("FeSe 1-x Te x");

        Span superconductor2 = new Span();
        superconductor2.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        superconductor2.setOffsetStart(79);
        superconductor2.setOffsetEnd(90);
        superconductor2.setText("0.6 ≤ x ≤ 1");

        superconductorList.add(superconductor);
        superconductorList.add(superconductor2);

        //This will ensure that next time I modify the principle on which the offsets are calculated, will fail
        int startingOffset = layoutTokens.get(0).getOffset();
        assertThat(text.substring(superconductor.getOffsetStart() - startingOffset, superconductor.getOffsetEnd() - startingOffset), is(superconductor.getText()));
        assertThat(text.substring(superconductor2.getOffsetStart() - startingOffset, superconductor2.getOffsetEnd() - startingOffset), is(superconductor2.getText()));

        Element out = target.trainingExtraction(superconductorList, layoutTokens);

        assertThat(out.toXML(), is("<p xmlns=\"http://www.tei-c.org/ns/1.0\">Specific-Heat Study of Superconducting and Normal States in <material>FeSe 1-x Te x</material> (<material>0.6 ≤ x ≤ 1</material>) Single Crystals: Strong-Coupling Superconductivity, Strong Electron-Correlation, and Inhomogeneity</p>"));
    }


    @Test
    public void testTrainingData3_value() throws Exception {
        String text = "Specific-Heat Study of Superconducting and Normal States in FeSe 1-x Te x (0.6 ≤ x ≤ 1) Single Crystals: Strong-Coupling Superconductivity, Strong Electron-Correlation, and Inhomogeneity ";

        List<LayoutToken> layoutTokens = DeepAnalyzer.getInstance().tokenizeWithLayoutToken(text);

        layoutTokens.stream().forEach(l -> {
            l.setOffset(l.getOffset() + 4);
        });

        List<Span> spanList = new ArrayList<>();
        Span span1 = new Span();
        span1.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        span1.setOffsetStart(64);
        span1.setOffsetEnd(77);
        span1.setText("FeSe 1-x Te x");

        Span span2 = new Span();
        span2.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        span2.setOffsetStart(79);
        span2.setOffsetEnd(90);
        span2.setText("0.6 ≤ x ≤ 1");

        spanList.add(span1);
        spanList.add(span2);

        List<DocumentBlock> documentBlocks = new ArrayList<>();
        documentBlocks.add(new DocumentBlock(layoutTokens, spanList));

        //This will ensure that next time I modify the principle on which the offsets are calculated, will fail
        int startingOffset = layoutTokens.get(0).getOffset();
        for (Span span : spanList) {
            assertThat(text.substring(span.getOffsetStart() - startingOffset, span.getOffsetEnd() - startingOffset), is(span.getText()));
        }

        String output = target.format(documentBlocks, 1);
        assertThat(output,
            endsWith("<text xml:lang=\"en\"><p>Specific-Heat Study of Superconducting and Normal States in <material>FeSe 1-x Te x</material> (<material>0.6 ≤ x ≤ 1</material>) Single Crystals: Strong-Coupling Superconductivity, Strong Electron-Correlation, and Inhomogeneity</p></text></tei>"));
    }

    @Test
    public void testTrainingData4_value() throws Exception {
        String text = "The electronic specific heat of as-grown and annealed single-crystals of FeSe 1-x Te x (0.6 ≤ x ≤ 1) has been investigated. It has been found that annealed single-crystals with x = 0.6 -0.9 exhibit bulk superconductivity with a clear specific-heat jump at the superconducting (SC) transition temperature, T c . Both 2Δ 0 /k B T c [Δ 0 : the SC gap at 0 K estimated using the single-band BCS s-wave model] and ⊿C/(γ n -γ 0 )T c [⊿C: the specific-heat jump at T c , γ n : the electronic specific-heat coefficient in the normal state, γ 0 : the residual electronic specific-heat coefficient at 0 K in the SC state] are largest in the well-annealed single-crystal with x = 0.7, i.e., 4.29 and 2.76, respectively, indicating that the superconductivity is of the strong coupling. The thermodynamic critical field has also been estimated. γ n has been found to be one order of magnitude larger than those estimated from the band calculations and increases with increasing x at x = 0.6 -0.9, which is surmised to be due to the increase in the electronic effective mass, namely, the enhancement of the electron correlation. It has been found that there remains a finite value of γ 0 in the SC state even in the well-annealed single-crystals with x = 0.8 -0.9, suggesting an inhomogeneous electronic state in real space and/or momentum space.";

        List<LayoutToken> layoutTokens = DeepAnalyzer.getInstance().tokenizeWithLayoutToken(text);

        //Simulating a stream of token that is in the middle of the document
        layoutTokens.stream().forEach(l -> {
            l.setOffset(l.getOffset() + 372);
        });

        List<Span> spanList = new ArrayList<>();
        Span Span = new Span();
        Span.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        Span.setOffsetStart(445);
        Span.setOffsetEnd(458);
        Span.setText("FeSe 1-x Te x");
        spanList.add(Span);

        Span Span2 = new Span();
        Span2.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        Span2.setOffsetStart(460);
        Span2.setOffsetEnd(471);
        Span2.setText("0.6 ≤ x ≤ 1");
        spanList.add(Span2);

        Span Span3 = new Span();
        Span3.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        Span3.setOffsetStart(549);
        Span3.setOffsetEnd(561);
        Span3.setText("x = 0.6 -0.9");
        spanList.add(Span3);

        Span Span4 = new Span();
        Span4.setType(SUPERCONDUCTORS_TC_VALUE_LABEL);
        Span4.setOffsetStart(562);
        Span4.setOffsetEnd(569);
        Span4.setText("exhibit");
        spanList.add(Span4);

        Span Span5 = new Span();
        Span5.setType(SUPERCONDUCTORS_TC_LABEL);
        Span5.setOffsetStart(570);
        Span5.setOffsetEnd(592);
        Span5.setText("bulk superconductivity");
        spanList.add(Span5);

        Span Span6 = new Span();
        Span6.setType(SUPERCONDUCTORS_TC_LABEL);
        Span6.setOffsetStart(632);
        Span6.setOffsetEnd(647);
        Span6.setText("superconducting");
        spanList.add(Span6);

        Span Span7 = new Span();
        Span7.setType(SUPERCONDUCTORS_TC_LABEL);
        Span7.setOffsetStart(653);
        Span7.setOffsetEnd(675);
        Span7.setText("transition temperature");
        spanList.add(Span7);

        List<DocumentBlock> documentBlocks = new ArrayList<>();
        documentBlocks.add(new DocumentBlock(layoutTokens, spanList));

        //This will ensure that next time I modify the principle on which the offsets are calculated, will fail
        int startingOffset = layoutTokens.get(0).getOffset();
        for (Span span : spanList) {
            assertThat(text.substring(span.getOffsetStart() - startingOffset, span.getOffsetEnd() - startingOffset), is(span.getText()));
        }

        String output = target.format(documentBlocks, 1);

        assertThat(output.substring(output.indexOf("<text xml:lang=\"en\">")),
            is("<text xml:lang=\"en\"><p>The electronic specific heat of as-grown and annealed single-crystals of <material>FeSe 1-x Te x</material> (<material>0.6 ≤ x ≤ 1</material>) has been investigated. It has been found that annealed single-crystals with <material>x = 0.6 -0.9</material> <tcValue>exhibit</tcValue> <tc>bulk superconductivity</tc> with a clear specific-heat jump at the <tc>superconducting</tc> (SC) <tc>transition temperature</tc>, T c . Both 2Δ 0 /k B T c [Δ 0 : the SC gap at 0 K estimated using the single-band BCS s-wave model] and ⊿C/(γ n -γ 0 )T c [⊿C: the specific-heat jump at T c , γ n : the electronic specific-heat coefficient in the normal state, γ 0 : the residual electronic specific-heat coefficient at 0 K in the SC state] are largest in the well-annealed single-crystal with x = 0.7, i.e., 4.29 and 2.76, respectively, indicating that the superconductivity is of the strong coupling. The thermodynamic critical field has also been estimated. γ n has been found to be one order of magnitude larger than those estimated from the band calculations and increases with increasing x at x = 0.6 -0.9, which is surmised to be due to the increase in the electronic effective mass, namely, the enhancement of the electron correlation. It has been found that there remains a finite value of γ 0 in the SC state even in the well-annealed single-crystals with x = 0.8 -0.9, suggesting an inhomogeneous electronic state in real space and/or momentum space.</p></text></tei>"));
    }

    @Test(expected = RuntimeException.class)
    public void testTrainingData5_wrongOffsets_shoudlThrowException() throws Exception {
        String text = "The electronic specific heat of as-grown and annealed single-crystals of FeSe 1-x Te x (0.6 ≤ x ≤ 1) has been investigated. It has been found that annealed single-crystals with x = 0.6 -0.9 exhibit bulk superconductivity with a clear specific-heat jump at the superconducting (SC) transition temperature, T c . Both 2Δ 0 /k B T c [Δ 0 : the SC gap at 0 K estimated using the single-band BCS s-wave model] and ⊿C/(γ n -γ 0 )T c [⊿C: the specific-heat jump at T c , γ n : the electronic specific-heat coefficient in the normal state, γ 0 : the residual electronic specific-heat coefficient at 0 K in the SC state] are largest in the well-annealed single-crystal with x = 0.7, i.e., 4.29 and 2.76, respectively, indicating that the superconductivity is of the strong coupling. The thermodynamic critical field has also been estimated. γ n has been found to be one order of magnitude larger than those estimated from the band calculations and increases with increasing x at x = 0.6 -0.9, which is surmised to be due to the increase in the electronic effective mass, namely, the enhancement of the electron correlation. It has been found that there remains a finite value of γ 0 in the SC state even in the well-annealed single-crystals with x = 0.8 -0.9, suggesting an inhomogeneous electronic state in real space and/or momentum space.";

        List<LayoutToken> layoutTokens = DeepAnalyzer.getInstance().tokenizeWithLayoutToken(text);

        layoutTokens.stream().forEach(l -> {
            l.setOffset(l.getOffset() + 372);
        });

        List<Span> spanList = new ArrayList<>();
        Span Span = new Span();
        Span.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        Span.setOffsetStart(445);
        Span.setOffsetEnd(458);
        Span.setText("FeSe 1-x Te x");
        spanList.add(Span);

        Span Span2 = new Span();
        Span2.setType(SUPERCONDUCTORS_MATERIAL_LABEL);
        Span2.setOffsetStart(460);
        Span2.setOffsetEnd(472);
        Span2.setText("0.6 ≤ x ≤ 1");
        spanList.add(Span2);

        List<DocumentBlock> documentBlocks = new ArrayList<>();
        documentBlocks.add(new DocumentBlock(layoutTokens, spanList));

        target.format(documentBlocks, 1);
    }
}