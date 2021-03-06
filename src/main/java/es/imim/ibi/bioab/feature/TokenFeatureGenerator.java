/**
 * Biomedical Abbreviation Miner (BioAB Miner)
 * 
 */
package es.imim.ibi.bioab.feature;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.backingdata.gateutils.GATEfiles;
import org.backingdata.gateutils.GATEinit;
import org.backingdata.gateutils.GATEutils;
import org.backingdata.gateutils.generic.PropertyManager;
import org.backingdata.mlfeats.FeatUtil;
import org.backingdata.mlfeats.FeatureSet;
import org.backingdata.mlfeats.NominalW;
import org.backingdata.mlfeats.NumericW;
import org.backingdata.mlfeats.StringW;
import org.backingdata.mlfeats.exception.FeatSetConsistencyException;
import org.backingdata.nlp.utils.Manage;
import org.backingdata.nlp.utils.langres.wikifreq.LangENUM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.imim.ibi.bioab.feature.generator.CharNumber;
import es.imim.ibi.bioab.feature.generator.CharPercentage;
import es.imim.ibi.bioab.feature.generator.ClassLFGetterBOI;
import es.imim.ibi.bioab.feature.generator.ClassSFGetterABBTYPE;
import es.imim.ibi.bioab.feature.generator.ClassSFGetterBOI;
import es.imim.ibi.bioab.feature.generator.DocumentID;
import es.imim.ibi.bioab.feature.generator.FeatureRepetitionsOfContext;
import es.imim.ibi.bioab.feature.generator.FeatureValueOfContext;
import es.imim.ibi.bioab.feature.generator.IsFirstLastCharSpecial;
import es.imim.ibi.bioab.feature.generator.SentenceID;
import es.imim.ibi.bioab.feature.generator.StringInList;
import es.imim.ibi.bioab.feature.generator.WikiFreqOfContext;
import gate.Annotation;
import gate.Document;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToNominal;

/**
 * Read from a folder (inputFolder) the GATE XML training documents (with Abbrevitaion annotations and processed by 
 * the Freeling annotator es.imim.ibi.bioab.nlp.FreelingParser) and generate/store ARFF files in which each instance is a 
 * token described by a the set of features specified by means of the feature generator classes instantiated by the method 
 * {@link #generateFeatureSet() generateFeatureSet}.
 * 
 * The ARFF files generated by this class:
 * - includes as token labels (last 3 features described by the {@link #generateFeatureSet() generateFeatureSet} method):
 *  1) the fact that a token belongs or not to a SHORT form (feature with name "CLASS_BOI_SF_All" - BOI encoding, see OBIclassValues set of class values)
 *  2) the fact that a token belongs or not to a LONG form (feature with name "CLASS_SF_ABBTYPE" - see OBIclassValues set of class values)
 *  3) the kind of short form of the token (feature with name "CLASS_BOI_LF" -BOI encoding, see OBIclassValues set of class values)
 *  - is useful to train any token classification approach by means of a Weka classifiers
 * 
 * The ARFF files stored by this class are useful to train token classifiers in order
 * 
 * @author Francesco Ronzano
 *
 */
public class TokenFeatureGenerator {

	private static Logger logger = LoggerFactory.getLogger(TokenFeatureGenerator.class);

	// Input folder - including all GATE XML texts annotated by Freeling (Freeling annotator implemented by the class: es.imim.ibi.bioab.nlp.FreelingParser)
	private static String inputFolder = "/full/path/to/folder_with_GATE_XML_training_documents";

	// Folder where to store the output ARFF
	private static String outputFolder = "/full/path/to/folder_where_store_ARFF_files";

	// Version name to add to the output ARFF file names
	private static String version = "v1";

	// If token features are sentence scoped (the context of a token is described by considering only the info from the sentence where the token occurs)
	private static boolean isSentenceScopedFeature = true;

	// Other variables
	private static Set<String> OBIclassValues = new HashSet<String>();
	private static Set<String> ABBTYPEclassValues = new HashSet<String>();
	static {
		OBIclassValues.add("B");
		OBIclassValues.add("I");
		OBIclassValues.add("O");

		// SHORT, MULTIPLE, GLOBAL, CONTEXTUAL, DERIVED, NONE
		ABBTYPEclassValues.add("SHORT");
		ABBTYPEclassValues.add("GLOBAL");
		ABBTYPEclassValues.add("MULTIPLE");
		ABBTYPEclassValues.add("CONTEXTUAL");
		ABBTYPEclassValues.add("DERIVED");
		ABBTYPEclassValues.add("NONE");
	}

	public static void main(String args[]) {

		// Set the full path of the configuration file of BioAB Miner
		PropertyManager.setPropertyFilePath("/home/ronzano/Desktop/Hackathon_PLN/BioAbMinerConfig.properties");
		
		// Init NLP-utils library by passing the BioAB miner resource folder
		// Resource folder can be downloaded at: http://backingdata.org/bioab/BioAB-resources-1.0.tar.gz
		try {
			Manage.setResourceFolder(PropertyManager.getProperty("resourceFolder.fullPath"));
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Init GATE
		try {
			GATEinit.initGate(PropertyManager.getProperty("gate.home"), PropertyManager.getProperty("gate.plugins"));
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		// Execution settings
		inputFolder = "/home/ronzano/Desktop/Hackathon_PLN/TrainingDocuments_BARR";
		outputFolder = "/home/ronzano/Desktop/Hackathon_PLN/ARFF_FILES";
		version = "BARR17_train_and_test";
		isSentenceScopedFeature = true;
		executeFeatGen();

	}

	public static void executeFeatGen() {
		
		inputFolder = (inputFolder.endsWith(File.separator)) ? inputFolder : inputFolder + File.separator;
		outputFolder = (outputFolder.endsWith(File.separator)) ? outputFolder : outputFolder + File.separator;
		
		System.out.println("\n");
		System.out.println("**************************************************");
		System.out.println("Geenrating ARFF files from the GATE XML files contained in the inputFolder : " + inputFolder);
		System.out.println("Storing ARFF to outputFolder : " + outputFolder);
		System.out.println("Version : " + version);
		System.out.println("Is the context of a token described by considering only the info from the sentence where the token occurs? " + isSentenceScopedFeature);
		System.out.println("**************************************************");

		// Init NLP-utils library by passing the BioAB miner resource folder
		// Resource folder can be downloaded at: http://backingdata.org/bioab/BioAB-resources-1.0.tar.gz
		FeatureSet<Document, TokenFeatureGenerationContext> featSet = null;
		try {
			featSet = generateFeatureSet(PropertyManager.getProperty("resourceFolder.fullPath"));
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Stats
		int gateDocCount = 0;
		int tokenCount = 0;

		List<File> inputFolderFiles = new ArrayList<File>();
		try {
			inputFolderFiles = Files.walk(Paths.get(inputFolder)).filter(p -> p.toString().endsWith(".xml")).map(pathElem -> pathElem.toFile()).collect(Collectors.toList());
			// Collection<File> inputFolderFiles = FileUtils.listFiles(new File(inputFolder), null, false);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		for(File inputFolderFile : inputFolderFiles) {
			if(inputFolderFile.getName().endsWith("_PROC.xml")) {

				if(inputFolderFile.getAbsolutePath().contains("BARR_IBEREVAL/test") && !inputFolderFile.getName().contains("_MANanno_")) {
					System.out.println("\n-\nSKIPPED: " + inputFolderFile.getName() + "(" + inputFolderFile.getAbsolutePath() + ")" + 
							"\n > is an IBEREVAL test file that does not contain any manual annotation.");
					continue;
				}

				Document gateDoc = GATEfiles.loadGATEfromXMLfile(inputFolderFile.getAbsolutePath());

				System.out.println("\n - Processed " + gateDocCount + " documents over " + inputFolderFiles.size() + "...");

				gateDocCount++;

				System.out.println("Processing: " + inputFolderFile.getName() + "...");

				Integer sentenceIDappo = Integer.MAX_VALUE;
				
				// Computing document level features
				List<Annotation> documentTokenAnnList = GATEutils.getAnnInDocOrder(gateDoc, TokenAnnConst.tokenAnnSet, TokenAnnConst.tokenType);
				
				// Start processing token level features: create Token ID -> Sentence ID map
				Map<Integer, Integer> tokenIDtoSentenceIDAPPOmap = new HashMap<Integer, Integer>();
				for(Annotation documentTokenAnn : documentTokenAnnList) {

					// Set Sentence ID
					List<Annotation> sentenceAnnList = GATEutils.getAnnInDocOrderIntersectAnn(gateDoc, TokenAnnConst.sentenceAnnSet, TokenAnnConst.sentenceType, documentTokenAnn);
					if(sentenceAnnList != null && sentenceAnnList.size() > 0) {
						tokenIDtoSentenceIDAPPOmap.put(documentTokenAnn.getId(), sentenceAnnList.get(0).getId());

						if(sentenceAnnList.size() > 1) {
							logger.warn("Multiple sentence id for token!");
						}
					}
					else {
						logger.warn("No sentence id for token! Generating a random one");
						tokenIDtoSentenceIDAPPOmap.put(documentTokenAnn.getId(), --sentenceIDappo);
					}
				}

				// Create a training example for each token of the document - in the documentTokenAnnList
				for(Annotation documentTokenAnn : documentTokenAnnList) {
					
					// Set training context
					TokenFeatureGenerationContext trCtx = new TokenFeatureGenerationContext(gateDoc);
					trCtx.setCoreTokenAnn(documentTokenAnn);

					// Set document global features
					trCtx.setDocumentTokenList(documentTokenAnnList);
					trCtx.setTokenIDtoSentenceIDmap(tokenIDtoSentenceIDAPPOmap);

					// Set Sentence ID
					trCtx.setSentenceID(gateDocCount + "_" + tokenIDtoSentenceIDAPPOmap.get(documentTokenAnn.getId()));
					trCtx.setGATEsentenceID(tokenIDtoSentenceIDAPPOmap.get(documentTokenAnn.getId()));

					// Set Document ID
					trCtx.setDocumentID(gateDocCount + "_" + inputFolderFile.getName().replace("_PROC.xml", ""));

					featSet.addElement(gateDoc, trCtx);
					tokenCount++;

					System.out.print("+");
					if(tokenCount % 100 == 0) {
						System.out.print("\n > " + tokenCount + " tokens > ");
					} 
				}

				// OPTIONAL: store the GATE XML document that has been exploited to generate features
				if(documentTokenAnnList != null && documentTokenAnnList.size() > 0 && TokenFeatureGenerationContext.reportFeatInGate) {
					GATEfiles.storeGateXMLToFile(gateDoc, inputFolderFile.getAbsolutePath().replace("GATE_NLP_PROC", "GATE_NLP_PROC_FEATS").replace(".xml", "_FEATS.xml"));
				}

				gateDoc.cleanup();
				gateDoc = null;
			}
		}
		logger.info("GATE DOCS PROCESSED: " + gateDocCount + " > TOKENS PROCESSED: " + tokenCount);

		// Check for the consistency of the features
		logger.debug("Feature consistency check...");
		boolean featConsistency = true;
		try {
			featConsistency = FeatUtil.featureListConsistencyCheck(featSet);
		} catch (FeatSetConsistencyException e) {
			e.printStackTrace();
			featConsistency = false;
		}
		finally {
			if(!featConsistency) {
				logger.error("Feature consistency problem:");
				logger.error(FeatUtil.printFeatureStats(featSet));
				return;
			}
		}
		logger.debug("Feature consistency check passed.");

		// --- STORE Token ARFF:
		logger.info("STORING ARFF...");
		String outputARFFpath = outputFolder + "abbrv_v_" + version + "_sentScop_" + isSentenceScopedFeature + ".arff";
		try {
			ArffSaver saver = new ArffSaver();
			saver.setInstances(FeatUtil.wekaInstanceGeneration(featSet, "abbrv_v_" + version + "_sentScop_" + isSentenceScopedFeature) );
			saver.setFile(new File(outputARFFpath));
			saver.writeBatch();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (FeatSetConsistencyException e) {
			e.printStackTrace();
		}

		// --- STORE FILTERED Token ARFF (all numeric or nominal features for compatibility with some of weka classifiers):
		String outputARFFpathFiltered = outputARFFpath.replace(".arff", "_FILTERED.arff");
		try {
			// Instantiate filters
			MultiFilter multiFilter = new MultiFilter();
			
			multiFilter.setFilters(getFilterArrayForARFF());

			// Load ARFF
			Instances ARFFinstances = null;
			try {
				BufferedReader reader_training = new BufferedReader(new FileReader(outputARFFpath));
				ARFFinstances = new Instances(reader_training);
				logger.info("\nLoaded ARFF file: " + outputARFFpath + " ...");
				reader_training.close();
				ARFFinstances.setClassIndex(ARFFinstances.numAttributes() - 1);
			}
			catch (Exception e) {
				logger.info("\nError loading ARFF file: " + outputARFFpath + " ---> " + e.getMessage());
				e.printStackTrace();
			}

			// Apply filter
			multiFilter.setInputFormat(ARFFinstances);
			Instances ARFFinstancesFiltered = Filter.useFilter(ARFFinstances, multiFilter);
			ARFFinstancesFiltered.setClassIndex(ARFFinstancesFiltered.numAttributes() - 1);

			// Store filtered ARFF
			ArffSaver saver = new ArffSaver();
			saver.setInstances(ARFFinstancesFiltered);
			saver.setFile(new File(outputARFFpathFiltered));
			saver.writeBatch();

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.gc();

		logger.info("END PROCESSING - generated ARFF file including " + tokenCount + " tokens.");
	}
	
	public static Filter[] getFilterArrayForARFF() throws Exception {
		
		Filter[] filtersArray = new Filter[3];
		Remove removeFilter = new Remove();
		removeFilter.setOptions(weka.core.Utils.splitOptions("-R 1,2,10-23,94-100"));
		filtersArray[0] = removeFilter;

		StringToNominal StringToNominalFilter1 = new StringToNominal();
		StringToNominalFilter1.setOptions(weka.core.Utils.splitOptions("-R 1-7"));
		filtersArray[1] = StringToNominalFilter1;

		StringToNominal StringToNominalFilter2 = new StringToNominal();
		StringToNominalFilter2.setOptions(weka.core.Utils.splitOptions("-R 57-63"));
		filtersArray[2] = StringToNominalFilter2;
		
		return filtersArray;
	}

	/**
	 * Instantiate the set of Feature generator classes in charge to generate the features describing each token.
	 * 
	 * @return
	 */
	public static FeatureSet<Document, TokenFeatureGenerationContext> generateFeatureSet(String NLPuitlResourceFolder) {

		// Check if the NLP-utils library has been initialized (it must be initialized to correctly execute feature generators)
		Manage.setResourceFolder(NLPuitlResourceFolder);

		// Chack if GATE is initialized
		try {
			GATEinit.initGate(PropertyManager.getProperty("gate.home"), PropertyManager.getProperty("gate.plugins"));
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		// Generate feature set
		FeatureSet<Document, TokenFeatureGenerationContext> featSet = new FeatureSet<Document, TokenFeatureGenerationContext>();

		try {
			// Feature generators: document and sentence IDs
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("DOC_ID", new DocumentID()));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("SENT_ID", new SentenceID()));

			// Feature generators: POS / word / lemma based token features
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("POS", new FeatureValueOfContext(0, TokenAnnConst.tokenPOSFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("POS_B1", new FeatureValueOfContext(-1, TokenAnnConst.tokenPOSFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("POS_B2", new FeatureValueOfContext(-2, TokenAnnConst.tokenPOSFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("POS_B3", new FeatureValueOfContext(-3, TokenAnnConst.tokenPOSFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("POS_A1", new FeatureValueOfContext(1, TokenAnnConst.tokenPOSFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("POS_A2", new FeatureValueOfContext(2, TokenAnnConst.tokenPOSFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("POS_A3", new FeatureValueOfContext(3, TokenAnnConst.tokenPOSFeat, isSentenceScopedFeature)));

			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("WORD", new FeatureValueOfContext(0, null, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("WORD_B1", new FeatureValueOfContext(-1, null, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("WORD_B2", new FeatureValueOfContext(-2, null, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("WORD_B3", new FeatureValueOfContext(-3, null, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("WORD_A1", new FeatureValueOfContext(1, null, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("WORD_A2", new FeatureValueOfContext(2, null, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("WORD_A3", new FeatureValueOfContext(3, null, isSentenceScopedFeature)));

			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("LEMMA", new FeatureValueOfContext(0, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("LEMMA_B1", new FeatureValueOfContext(-1, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("LEMMA_B2", new FeatureValueOfContext(-2, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("LEMMA_B3", new FeatureValueOfContext(-3, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("LEMMA_A1", new FeatureValueOfContext(1, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("LEMMA_A2", new FeatureValueOfContext(2, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("LEMMA_A3", new FeatureValueOfContext(3, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			
			// Feature generators: Shallow Text token features
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("NUM_CHARS", new CharNumber(0, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("NUM_CHARS_B1", new CharNumber(-1, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("NUM_CHARS_B2", new CharNumber(-2, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("NUM_CHARS_B3", new CharNumber(-3, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("NUM_CHARS_A1", new CharNumber(1, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("NUM_CHARS_A2", new CharNumber(2, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("NUM_CHARS_A3", new CharNumber(3, isSentenceScopedFeature)));

			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_UPCASE", new CharPercentage(0, CharPercentage.PercentageType.UPPERCASE, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_UPCASE_B1", new CharPercentage(-1, CharPercentage.PercentageType.UPPERCASE, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_UPCASE_B2", new CharPercentage(-2, CharPercentage.PercentageType.UPPERCASE, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_UPCASE_B3", new CharPercentage(-3, CharPercentage.PercentageType.UPPERCASE, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_UPCASE_A1", new CharPercentage(1, CharPercentage.PercentageType.UPPERCASE, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_UPCASE_A2", new CharPercentage(2, CharPercentage.PercentageType.UPPERCASE, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_UPCASE_A3", new CharPercentage(3, CharPercentage.PercentageType.UPPERCASE, isSentenceScopedFeature)));

			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_NUMERIC", new CharPercentage(0, CharPercentage.PercentageType.NUMERIC, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_NUMERIC_B1", new CharPercentage(-1, CharPercentage.PercentageType.NUMERIC, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_NUMERIC_B2", new CharPercentage(-2, CharPercentage.PercentageType.NUMERIC, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_NUMERIC_B3", new CharPercentage(-3, CharPercentage.PercentageType.NUMERIC, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_NUMERIC_A1", new CharPercentage(1, CharPercentage.PercentageType.NUMERIC, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_NUMERIC_A2", new CharPercentage(2, CharPercentage.PercentageType.NUMERIC, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_NUMERIC_A3", new CharPercentage(3, CharPercentage.PercentageType.NUMERIC, isSentenceScopedFeature)));

			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_PUNCT", new CharPercentage(0, CharPercentage.PercentageType.PUNCTUATION, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_PUNCT_B1", new CharPercentage(-1, CharPercentage.PercentageType.PUNCTUATION, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_PUNCT_B2", new CharPercentage(-2, CharPercentage.PercentageType.PUNCTUATION, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_PUNCT_B3", new CharPercentage(-3, CharPercentage.PercentageType.PUNCTUATION, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_PUNCT_A1", new CharPercentage(1, CharPercentage.PercentageType.PUNCTUATION, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_PUNCT_A2", new CharPercentage(2, CharPercentage.PercentageType.PUNCTUATION, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("PERC_CHARS_PUNCT_A3", new CharPercentage(3, CharPercentage.PercentageType.PUNCTUATION, isSentenceScopedFeature)));

			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("FIRST_CHAR_UPCASE", new IsFirstLastCharSpecial(0, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.FIRST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("FIRST_CHAR_UPCASE_B1", new IsFirstLastCharSpecial(-1, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.FIRST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("FIRST_CHAR_UPCASE_B2", new IsFirstLastCharSpecial(-2, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.FIRST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("FIRST_CHAR_UPCASE_B3", new IsFirstLastCharSpecial(-3, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.FIRST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("FIRST_CHAR_UPCASE_A1", new IsFirstLastCharSpecial(1, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.FIRST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("FIRST_CHAR_UPCASE_A2", new IsFirstLastCharSpecial(2, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.FIRST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("FIRST_CHAR_UPCASE_A3", new IsFirstLastCharSpecial(3, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.FIRST, isSentenceScopedFeature)));

			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_UPCASE", new IsFirstLastCharSpecial(0, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_UPCASE_B1", new IsFirstLastCharSpecial(-1, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_UPCASE_B2", new IsFirstLastCharSpecial(-2, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_UPCASE_B3", new IsFirstLastCharSpecial(-3, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_UPCASE_A1", new IsFirstLastCharSpecial(1, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_UPCASE_A2", new IsFirstLastCharSpecial(2, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_UPCASE_A3", new IsFirstLastCharSpecial(3, IsFirstLastCharSpecial.CharCheckType.UPPERCASE, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));

			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_PUNCT", new IsFirstLastCharSpecial(0, IsFirstLastCharSpecial.CharCheckType.PUNCTUATION, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_PUNCT_B1", new IsFirstLastCharSpecial(-1, IsFirstLastCharSpecial.CharCheckType.PUNCTUATION, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_PUNCT_B2", new IsFirstLastCharSpecial(-2, IsFirstLastCharSpecial.CharCheckType.PUNCTUATION, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_PUNCT_B3", new IsFirstLastCharSpecial(-3, IsFirstLastCharSpecial.CharCheckType.PUNCTUATION, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LASTT_CHAR_PUNCT_A1", new IsFirstLastCharSpecial(1, IsFirstLastCharSpecial.CharCheckType.PUNCTUATION, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_PUNCT_A2", new IsFirstLastCharSpecial(2, IsFirstLastCharSpecial.CharCheckType.PUNCTUATION, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("LAST_CHAR_PUNCT_A3", new IsFirstLastCharSpecial(3, IsFirstLastCharSpecial.CharCheckType.PUNCTUATION, IsFirstLastCharSpecial.CharConsideredType.LAST, isSentenceScopedFeature)));

			// Feature generators: dependency tree token features
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("FUNDEP", new FeatureValueOfContext(0, TokenAnnConst.tokenDepFunctFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("FUNDEP_B1", new FeatureValueOfContext(-1, TokenAnnConst.tokenDepFunctFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("FUNDEP_B2", new FeatureValueOfContext(-2, TokenAnnConst.tokenDepFunctFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("FUNDEP_B3", new FeatureValueOfContext(-3, TokenAnnConst.tokenDepFunctFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("FUNDEP_A1", new FeatureValueOfContext(1, TokenAnnConst.tokenDepFunctFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("FUNDEP_A2", new FeatureValueOfContext(2, TokenAnnConst.tokenDepFunctFeat, isSentenceScopedFeature)));
			featSet.addFeature(new StringW<Document, TokenFeatureGenerationContext>("FUNDEP_A3", new FeatureValueOfContext(3, TokenAnnConst.tokenDepFunctFeat, isSentenceScopedFeature)));

			// Feature generators: abbreviation list based token features
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("IS_ABBRV", new StringInList(0, "/langres/sedom/abbrvList_5_2_2018.csv", false, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("IS_ABBRV_B1", new StringInList(-1, "/langres/sedom/abbrvList_5_2_2018.csv", false, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("IS_ABBRV_B2", new StringInList(-2, "/langres/sedom/abbrvList_5_2_2018.csv", false, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("IS_ABBRV_B3", new StringInList(-3, "/langres/sedom/abbrvList_5_2_2018.csv", false, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("IS_ABBRV_A1", new StringInList(1, "/langres/sedom/abbrvList_5_2_2018.csv", false, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("IS_ABBRV_A2", new StringInList(2, "/langres/sedom/abbrvList_5_2_2018.csv", false, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("IS_ABBRV_A3", new StringInList(3, "/langres/sedom/abbrvList_5_2_2018.csv", false, isSentenceScopedFeature)));
			
			// Feature generators: Repetition based token features
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS", new FeatureRepetitionsOfContext(0, null, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_B1", new FeatureRepetitionsOfContext(-1, null, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_B2", new FeatureRepetitionsOfContext(-2, null, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_B3", new FeatureRepetitionsOfContext(-3, null, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_A1", new FeatureRepetitionsOfContext(1, null, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_A2", new FeatureRepetitionsOfContext(2, null, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_A3", new FeatureRepetitionsOfContext(3, null, isSentenceScopedFeature)));

			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_LEMMA", new FeatureRepetitionsOfContext(0, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_LEMMA_B1", new FeatureRepetitionsOfContext(-1, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_LEMMA_B2", new FeatureRepetitionsOfContext(-2, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_LEMMA_B3", new FeatureRepetitionsOfContext(-3, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_LEMMA_A1", new FeatureRepetitionsOfContext(1, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_LEMMA_A2", new FeatureRepetitionsOfContext(2, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("REPETITIONS_LEMMA_A3", new FeatureRepetitionsOfContext(3, TokenAnnConst.tokenLemmaFeat, isSentenceScopedFeature)));

			// Feature generators: Corpus frequency token features 
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("WIKI_FREQ", new WikiFreqOfContext(0, LangENUM.Spanish, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("WIKI_FREQ_B1", new WikiFreqOfContext(-1, LangENUM.Spanish, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("WIKI_FREQ_B2", new WikiFreqOfContext(-2, LangENUM.Spanish, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("WIKI_FREQ_B3", new WikiFreqOfContext(-3, LangENUM.Spanish, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("WIKI_FREQ_A1", new WikiFreqOfContext(1, LangENUM.Spanish, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("WIKI_FREQ_A2", new WikiFreqOfContext(2, LangENUM.Spanish, isSentenceScopedFeature)));
			featSet.addFeature(new NumericW<Document, TokenFeatureGenerationContext>("WIKI_FREQ_A3", new WikiFreqOfContext(3, LangENUM.Spanish, isSentenceScopedFeature)));

			// Feature generators: Class: SHORT OBI
			featSet.addFeature(new NominalW<Document, TokenFeatureGenerationContext>("CLASS_BOI_SF_All", OBIclassValues, new ClassSFGetterBOI(null)));

			// Feature generators: Class: Abbreviation Type
			featSet.addFeature(new NominalW<Document, TokenFeatureGenerationContext>("CLASS_SF_ABBTYPE", ABBTYPEclassValues, new ClassSFGetterABBTYPE()));

			// Feature generators: Class: LONG OBI
			featSet.addFeature(new NominalW<Document, TokenFeatureGenerationContext>("CLASS_BOI_LF", OBIclassValues, new ClassLFGetterBOI(ClassLFGetterBOI.AbbreviationType.LONG)));

		} catch (Exception e) {
			logger.debug("Error instantiating feature generation template.");
			e.printStackTrace();
			return null;
		}

		return featSet;
	}
	
}
