package org.grobid.core.engines;

import org.grobid.core.GrobidModel;
import org.grobid.core.GrobidModels;

/**
 * Created by lfoppiano on 28/11/16.
 */
public class SuperconductorsModels {
    public static final GrobidModel SUPERCONDUCTORS = GrobidModels.modelFor("superconductors");
    public static final GrobidModel MATERIAL = GrobidModels.modelFor("material");
    public static final GrobidModel ENTITY_LINKER_MATERIAL_TC = GrobidModels.modelFor("entityLinker-material-tcValue");
    public static final GrobidModel ENTITY_LINKER_TC_PRESSURE = GrobidModels.modelFor("entityLinker-tcValue-pressure");
    public static final GrobidModel ENTITY_LINKER_TC_ME_METHOD = GrobidModels.modelFor("entityLinker-tcValue-me_method");

}
