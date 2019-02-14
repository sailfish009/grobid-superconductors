package org.grobid.core.features;

import org.grobid.core.layout.LayoutToken;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class FeaturesVectorSuperconductorsTest {

    FeaturesVectorSuperconductors target;

    @Before
    public void setUp() throws Exception {
        target = new FeaturesVectorSuperconductors();
    }

    @Test
    public void printVector() {
        LayoutToken token = new LayoutToken();

        token.setText("token1");
        token.fontSize = 3;
        token.setFont("Arial");
        token.setItalic(false);
        token.setBold(true);

        LayoutToken previousToken = new LayoutToken();

        previousToken.setText("token1");
        previousToken.fontSize = 3;
        previousToken.setFont("Arial");
        previousToken.setItalic(false);
        previousToken.setBold(true);


        FeaturesVectorSuperconductors features = target.addFeatures(token, "bao",
                previousToken);

        assertThat(features.printVector(), is("token1 token1 t to tok toke 1 n1 en1 ken1 NOCAPS CONTAINDIGIT 0 NOPUNCT tokenX xxxd xd SAMEFONT SAMEFONTSIZE bao"));
    }
}