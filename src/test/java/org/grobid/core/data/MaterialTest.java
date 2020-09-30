package org.grobid.core.data;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

public class MaterialTest {

    @Test
    public void testResolveVariable_1() throws Exception {
        Material material = new Material();
        material.setFormula("Fe1-xCuxO2");
        material.getVariables().put("x", Arrays.asList("0.1", "0.2", "0.3"));
        List<String> outputMaterials = Material.resolveVariables(material);

        assertThat(outputMaterials, hasSize(3));
        assertThat(outputMaterials.get(0), is("Fe0.9Cu0.1O2"));
        assertThat(outputMaterials.get(1), is("Fe0.8Cu0.2O2"));
        assertThat(outputMaterials.get(2), is("Fe0.7Cu0.3O2"));
    }

    @Test
    public void testResolveVariable_2() throws Exception {
        Material material = new Material();
        material.setFormula("Fe1-xCuyO2");
        material.getVariables().put("x", Arrays.asList("0.1", "0.2", "0.3"));
        material.getVariables().put("y", Arrays.asList("-1", "-0.2", "0.3", "0.5"));

        List<String> outputMaterials = Material.resolveVariables(material);

        assertThat(outputMaterials, hasSize(12));
        assertThat(outputMaterials.get(0), is("Fe0.9Cu-1O2"));
        assertThat(outputMaterials.get(1), is("Fe0.9Cu-0.2O2"));
        assertThat(outputMaterials.get(2), is("Fe0.9Cu0.3O2"));
        assertThat(outputMaterials.get(3), is("Fe0.9Cu0.5O2"));
        assertThat(outputMaterials.get(4), is("Fe0.8Cu-1O2"));
        assertThat(outputMaterials.get(5), is("Fe0.8Cu-0.2O2"));
        assertThat(outputMaterials.get(6), is("Fe0.8Cu0.3O2"));
        assertThat(outputMaterials.get(7), is("Fe0.8Cu0.5O2"));
        assertThat(outputMaterials.get(8), is("Fe0.7Cu-1O2"));
        assertThat(outputMaterials.get(9), is("Fe0.7Cu-0.2O2"));
        assertThat(outputMaterials.get(10), is("Fe0.7Cu0.3O2"));
        assertThat(outputMaterials.get(11), is("Fe0.7Cu0.5O2"));

    }

    @Test
    public void testResolveVariable_3() throws Exception {
        Material material = new Material();
        material.setFormula("Li x (NH 3 ) y Fe 2 (Te z Se 1−z ) 2");
        material.getVariables().put("x", Arrays.asList("0.1"));
        material.getVariables().put("y", Arrays.asList("0.1"));
        material.getVariables().put("z", Arrays.asList("0.1"));
        List<String> outputMaterials = Material.resolveVariables(material);

        assertThat(outputMaterials, hasSize(1));
        assertThat(outputMaterials.get(0), is("Li 0.1 (NH 3 ) 0.1 Fe 2 (Te 0.1 Se 0.9 ) 2"));
    }

    @Test
    public void testReplaceVariable() {
        String output = Material.replaceVariable("Fe1-xCuxO2", "x", "0.8");

        assertThat(output, is("Fe0.2Cu0.8O2"));
    }

    @Test
    public void testReplaceVariable2() {
        String output = Material.replaceVariable("Fe-xCu1-xO2", "x", "0.8");

        assertThat(output, is("Fe-0.8Cu0.2O2"));
    }

    @Test
    public void testReplaceVariable3() {
        String output = Material.replaceVariable("FexCuxO2", "x", "0.8");

        assertThat(output, is("Fe0.8Cu0.8O2"));
    }

    @Test
    public void testReplaceVariable4() {
        String output = Material.replaceVariable("LnFeAs(O1−x Fx)", "Ln", "Pr");

        assertThat(output, is("PrFeAs(O1−x Fx)"));
    }

    @Test
    public void testReplaceVariable5() {
        String output = Material.replaceVariable("1-x Ru x", "x", "0.2");

        assertThat(output, is("0.8 Ru 0.2"));
    }

    @Test
    @Ignore("Not testing much")
    public void testToJson() {
        Material material = new Material();

        material.setName("Material Name");
        material.setDoping("Doping!");
        material.setShape("shape!");
        material.setFormula("Cu x Fe y");
        material.addVariable("x", Arrays.asList("1", "2", "3"));
        material.addVariable("y", Arrays.asList("1", "2", "3"));

//        List<String> outputMaterials = Material.resolveVariables(material);

//        System.out.println(material.toJson());
        Map<String, String> x = Material.asAttributeMap(material);
//        assertThat();
        System.out.println(x);
        Map<String, String> y = Material.asAttributeMap(material, "bao123");
        System.out.println(y);
    }

    @Test
    public void testGeneratePermutations() {
        String formula = "Li x (NH 3 ) y Fe 2 (Te z Se 1−z ) 2";

        Map<String, List<String>> variables = new HashMap<>();
        variables.put("x", Arrays.asList("0.1"));
        variables.put("y", Arrays.asList("0.1"));
        variables.put("z", Arrays.asList("0.1"));
        List<String> result = new ArrayList<>();
        Material.generatePermutations(variables, new ArrayList<>(variables.keySet()), result,
            Pair.of(0, 0), formula);

        assertThat(result, hasSize(1));
        assertThat(result.get(0), is("Li 0.1 (NH 3 ) 0.1 Fe 2 (Te 0.1 Se 0.9 ) 2"));

    }

    @Test
    public void testGeneratePermutations_2() {
        String formula = "Li x (NH 3 ) y Fe 2 (Te z Se 1−z ) 2";

        Map<String, List<String>> variables = new HashMap<>();
        variables.put("x", Arrays.asList("0.1", "0.2"));
        variables.put("y", Arrays.asList("0.1", "0.2"));
        variables.put("z", Arrays.asList("0.1"));
        List<String> result = new ArrayList<>();
        Material.generatePermutations(variables, new ArrayList<>(variables.keySet()), result,
            Pair.of(0, 0), formula);

        assertThat(result, hasSize(4));
        assertThat(result.get(0), is("Li 0.1 (NH 3 ) 0.1 Fe 2 (Te 0.1 Se 0.9 ) 2"));
        assertThat(result.get(1), is("Li 0.1 (NH 3 ) 0.2 Fe 2 (Te 0.1 Se 0.9 ) 2"));
        assertThat(result.get(2), is("Li 0.2 (NH 3 ) 0.1 Fe 2 (Te 0.1 Se 0.9 ) 2"));
        assertThat(result.get(3), is("Li 0.2 (NH 3 ) 0.2 Fe 2 (Te 0.1 Se 0.9 ) 2"));

    }

}