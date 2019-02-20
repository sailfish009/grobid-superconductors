package org.grobid.trainer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.grobid.core.engines.AbbreviationsModels;
import org.grobid.core.engines.SuperconductorsModels;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.UnicodeUtil;
import org.grobid.trainer.sax.AbbreviationsAnnotationSaxHandler;
import org.grobid.trainer.sax.SuperconductorsAnnotationSaxHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Patrice Lopez
 */
public class AbbreviationsTrainer extends AbstractTrainer {


    public AbbreviationsTrainer() {
        super(AbbreviationsModels.ABBREVIATIONS);
        // adjusting CRF training parameters for this model
        epsilon = 0.000001;
        window = 20;
    }

    /**
     * Add the selected features to the model training
     */
    public int createCRFPPData(final File corpusDir,
                               final File trainingOutputPath,
                               final File evalOutputPath,
                               double splitRatio) {
        int totalExamples = 0;
        Writer trainingOutputWriter = null;
        Writer evaluationOutputWriter = null;

        File corpusDirPatched = Paths.get(corpusDir.getAbsolutePath()).getParent().getParent().resolve("superconductors").toFile();

        try {

            File adaptedCorpusDir = new File(corpusDirPatched.getAbsolutePath() + File.separator + "corpus" + File.separator + "staging");
            LOGGER.info("sourcePathLabel: " + adaptedCorpusDir);
            if (trainingOutputPath != null)
                LOGGER.info("outputPath for training data: " + trainingOutputPath);
            if (evalOutputPath != null)
                LOGGER.info("outputPath for evaluation data: " + evalOutputPath);

            // the file for writing the training data
            OutputStream os2 = null;

            if (trainingOutputPath != null) {
                os2 = new FileOutputStream(trainingOutputPath);
                trainingOutputWriter = new OutputStreamWriter(os2, UTF_8);
            }

            // the file for writing the evaluation data
            OutputStream os3 = null;

            if (evalOutputPath != null) {
                os3 = new FileOutputStream(evalOutputPath);
                evaluationOutputWriter = new OutputStreamWriter(os3, UTF_8);
            }

            // then we convert the tei files into the usual CRF label format
            // we process all tei files in the output directory
            File[] refFiles = adaptedCorpusDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".tei") || name.toLowerCase().endsWith(".tei.xml")
            );

            if (refFiles == null) {
                return 0;
            }

            LOGGER.info(refFiles.length + " files");

            // get a factory for SAX parser
            SAXParserFactory spf = SAXParserFactory.newInstance();

            String name;

            for (int n = 0; n < refFiles.length; n++) {
                File thefile = refFiles[n];
                name = thefile.getName();
                LOGGER.info(name);

                AbbreviationsAnnotationSaxHandler handler = new AbbreviationsAnnotationSaxHandler();

                //get a new instance of parser
                SAXParser p = spf.newSAXParser();
                p.parse(thefile, handler);

                List<Pair<String, String>> labeled = handler.getLabeledResult();

                // we can now add the features
                // we open the featured file
                File theRawFile = new File(adaptedCorpusDir + File.separator +
                        name.replace(".tei.xml", ".features.txt"));
                if (!theRawFile.exists()) {
                    LOGGER.warn("Raw file " + theRawFile + " does not exist. Please have a look!");
                    continue;
                }
                int q = 0;
                BufferedReader bis = new BufferedReader(
                        new InputStreamReader(new FileInputStream(theRawFile), UTF_8));
                Writer writer = dispatchExample(trainingOutputWriter, evaluationOutputWriter, splitRatio);
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = bis.readLine()) != null) {
                    int ii = line.indexOf('\t');
                    if (ii == -1) {
                        ii = line.indexOf(' ');
                    }
                    String token = null;
                    if (ii != -1) {
                        token = line.substring(0, ii).trim();
                        // unicode normalisation of the token - it should not be necessary if the training data
                        // has been generated by a recent version of grobid
                        token = UnicodeUtil.normaliseTextAndRemoveSpaces(token);
                    }
                    // we get the label in the labelled data file for the same token
                    for (int pp = q; pp < labeled.size(); pp++) {
                        String tag = labeled.get(pp).getB();

                        if (tag == null || StringUtils.length(StringUtils.trim(tag)) == 0) {
                            output.append("\n");
                            writer.write(output.toString() + "\n");
                            output = new StringBuilder();
                            continue;
                        }

                        String localToken = labeled.get(pp).getA();
                        // unicode normalisation of the token - it should not be necessary if the training data
                        // has been gnerated by a recent version of grobid
                        localToken = UnicodeUtil.normaliseTextAndRemoveSpaces(localToken);
                        if (localToken.equals(token)) {
                            line = line.replace("\t", " ").replace("  ", " ");
                            output.append(line).append(" ").append(tag).append("\n");
                            q = pp + 1;
                            pp = q + 10;
                        }

                        if (pp - q > 5) {
                            break;
                        }
                    }
                }
                bis.close();

                writer.write(output.toString() + "\n");
                writer.write("\n");
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occurred while running Grobid.", e);
        } finally {
            IOUtils.closeQuietly(evaluationOutputWriter, trainingOutputWriter);
        }
        return totalExamples;
    }

    /**
     * Add the selected features to the model training for bio entities
     */
    public int createCRFPPData(File sourcePathLabel,
                               File outputPath) {

        return createCRFPPData(sourcePathLabel, outputPath, null, 1.0);

    }

    /**
     * Dispatch the example to the training or test data, based on the split ration and the drawing of
     * a random number
     */
    private Writer dispatchExample(Writer writerTraining, Writer writerEvaluation, double splitRatio) {
        Writer writer = null;
        if ((writerTraining == null) && (writerEvaluation != null)) {
            writer = writerEvaluation;
        } else if ((writerTraining != null) && (writerEvaluation == null)) {
            writer = writerTraining;
        } else {
            if (Math.random() <= splitRatio)
                writer = writerTraining;
            else
                writer = writerEvaluation;
        }
        return writer;
    }

    /**
     * Command line execution. Assuming grobid-home is in ../grobid-home.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        GrobidProperties.getInstance();

        Trainer trainer = new AbbreviationsTrainer();
//        AbstractTrainer.runSplitTrainingEvaluation(trainer, 0.8);
        AbstractTrainer.runTraining(trainer);
        AbstractTrainer.runEvaluation(trainer);
    }
}