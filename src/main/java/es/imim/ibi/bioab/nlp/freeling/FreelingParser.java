package es.imim.ibi.bioab.nlp.freeling;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.backingdata.gateutils.GATEfiles;
import org.backingdata.gateutils.GATEinit;
import org.backingdata.gateutils.GATEutils;
import org.backingdata.gateutils.generic.GenericUtil;
import org.backingdata.gateutils.generic.PropertyManager;
import org.backingdata.nlp.utils.langres.wikifreq.LangENUM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AtomicDouble;

import edu.upc.freeling.ChartParser;
import edu.upc.freeling.DepTxala;
import edu.upc.freeling.HmmTagger;
import edu.upc.freeling.LangIdent;
import edu.upc.freeling.ListSentence;
import edu.upc.freeling.ListSentenceIterator;
import edu.upc.freeling.ListWord;
import edu.upc.freeling.ListWordIterator;
import edu.upc.freeling.Maco;
import edu.upc.freeling.MacoOptions;
import edu.upc.freeling.Nec;
import edu.upc.freeling.ParseTree;
import edu.upc.freeling.SWIGTYPE_p_splitter_status;
import edu.upc.freeling.Senses;
import edu.upc.freeling.Sentence;
import edu.upc.freeling.Splitter;
import edu.upc.freeling.Tokenizer;
import edu.upc.freeling.TreeConstPreorderIteratorNode;
import edu.upc.freeling.Ukb;
import edu.upc.freeling.Util;
import edu.upc.freeling.Word;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.RunTime;
import gate.util.InvalidOffsetException;
import gate.util.OffsetComparator;


/**
 * Freeling parser: analyze by Freeling the text considered.
 * http://nlp.lsi.upc.edu/freeling/
 * 
 * Given a specific language, the text of the document is split into sentences and tokens and then parsed by Freeling. It is possible to specify a sentence
 * annotation set and type if the user doesn't want to use the sentence split module of Freeling.
 * 
 * All the results are stored in the Freeling annotation set. When externally defined sentence annotations are used, these annotations are duplicated in the Freeling
 * annotation set.
 * 
 * 
 * MULTIPLE INSTANCES:
 * No duplicated resources - optimized multiple instances.
 * Instances synchronized on single resources (language detector, tokenizer, POS-tagger, etc.)
 * 
 * THREAD SAFETY OF EACH INSTANCE:
 * NOT thread safe (can't be used by two or more threads in parallel)
 * 
 * @author Francesco Ronzano
 */
@CreoleResource(name = "Freeling Parser")
public class FreelingParser extends AbstractLanguageAnalyser implements ProcessingResource, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = LoggerFactory.getLogger(FreelingParser.class);

	// *******************************************************************************************
	// Check the following paths in order to point the folders of your local Freeling installation
	private static final String FREELINGDIR = "/usr/local/FreeLing-4.0";
	private static String DATA = FREELINGDIR + "/share/freeling/";
	// *******************************************************************************************
	
	public static AtomicDouble totSecondsProcessing = new AtomicDouble(0d);
	public AtomicDouble localSecondsProcessing = new AtomicDouble(0d);

	// Names of the annotation set and annotation types generated by the FreelingParser in the input GATE document
	public static final String mainAnnSet = "Freeling";
	public static final String sentenceType = "Sentence";
	public static final String tokenType = "Token";
	public static final String chunkType = "Chunk";
	public static final String chunkType_labelFeatName = "label";
	public static final String chunkType_headWordStringFeatName = "headWord";
	public static final String chunkType_isChunkFeatName = "isChunk";
	public static final String tokenType_startSpanFeatName = "startSpan";
	public static final String tokenType_endSpanFeatName = "endSpan";
	public static final String tokenType_lemmaFeatName = "lemmaFeat";
	public static final String tokenType_positionFeatName = "positionFeat";
	public static final String tokenType_formStringFeatName = "formString";
	public static final String tokenType_POSFeatName = "POS";
	public static final String tokenType_phFormStringFeatName = "phForm";
	public static final String tokenType_chunkHeadIDFeat = "chunkID";

	private static LangIdent lgid;

	// Define analyzers:
	private static Map<LangENUM, Tokenizer> singletonLangTKMap = new HashMap<LangENUM, Tokenizer>();
	private static Map<LangENUM, Object> singletonLangTK_SYNCHmap = new HashMap<LangENUM, Object>();
	private static Map<LangENUM, Splitter> singletonLangSpMap = new HashMap<LangENUM, Splitter>();
	private static Map<LangENUM, Object> singletonLangSp_SYNCHmap = new HashMap<LangENUM, Object>();
	private static Map<LangENUM, Maco> singletonLangMfMap = new HashMap<LangENUM, Maco>();
	private static Map<LangENUM, Object> singletonLangMf_SYNCHmap = new HashMap<LangENUM, Object>();
	private static Map<LangENUM, HmmTagger> singletonLangTgMap = new HashMap<LangENUM, HmmTagger>();
	private static Map<LangENUM, Object> singletonLangTg_SYNCHmap = new HashMap<LangENUM, Object>();
	private static Map<LangENUM, ChartParser> singletonLangParserMap = new HashMap<LangENUM, ChartParser>();
	private static Map<LangENUM, Object> singletonLangParser_SYNCHmap = new HashMap<LangENUM, Object>();
	private static Map<LangENUM, DepTxala> singletonLangDepMap = new HashMap<LangENUM, DepTxala>();
	private static Map<LangENUM, Object> singletonLangDep_SYNCHmap = new HashMap<LangENUM, Object>();
	private static Map<LangENUM, Nec> singletonLangNeClassMap = new HashMap<LangENUM, Nec>();
	private static Map<LangENUM, Object> singletonLangNe_SYNCHmap = new HashMap<LangENUM, Object>();
	private static Map<LangENUM, Senses> singletonLangSenMap = new HashMap<LangENUM, Senses>();
	private static Map<LangENUM, Object> singletonLangSen_SYNCHmap = new HashMap<LangENUM, Object>();
	private static Map<LangENUM, Ukb> singletonLangDisMap = new HashMap<LangENUM, Ukb>();
	private static Map<LangENUM, Object> singletonLangDis_SYNCHmap = new HashMap<LangENUM, Object>();

	// Input set for annotation
	private String sentenceAnnotationSetToAnalyze = null;
	private String sentenceAnnotationTypeToAnalyze = null;
	private String analysisLang = null;
	private String addAnalysisLangToAnnSetName = null;
	private LangENUM analysisLangENUM = null;
	
	public String getSentenceAnnotationSetToAnalyze() {
		return sentenceAnnotationSetToAnalyze;
	}

	@RunTime
	@CreoleParameter(comment = "Set name of the annotation set where the sentences to parse are annotated")
	public void setSentenceAnnotationSetToAnalyze(
			String sentenceAnnotationSetToAnalyze) {
		this.sentenceAnnotationSetToAnalyze = sentenceAnnotationSetToAnalyze;
	}

	public String getSentenceAnnotationTypeToAnalyze() {
		return sentenceAnnotationTypeToAnalyze;
	}

	@RunTime
	@CreoleParameter(comment = "The type of sentence annotations")
	public void setSentenceAnnotationTypeToAnalyze(
			String sentenceAnnotationTypeToAnalyze) {
		this.sentenceAnnotationTypeToAnalyze = sentenceAnnotationTypeToAnalyze;
	}
	
	public String getAnalysisLang() {
		return analysisLang;
	}

	@RunTime
	@CreoleParameter(defaultValue = "SPA", comment = "The language of the text 'SPA', 'CAT' or 'ENG'.")
	public void setAnalysisLang(String analysisLang) {
		this.analysisLang = analysisLang;
	}

	public String getAddAnalysisLangToAnnSetName() {
		return addAnalysisLangToAnnSetName;
	}

	@RunTime
	@CreoleParameter(defaultValue = "false", comment = "If true, the analysis lang value is appended to the name of the annotation set (Freeling_analysisLang)")
	public void setAddAnalysisLangToAnnSetName(String addAnalysisLangToAnnSetName) {
		this.addAnalysisLangToAnnSetName = addAnalysisLangToAnnSetName;
	}

	/**
	 * Initialize Freeling and load resources in a specific language
	 * 
	 * @throws Exception
	 */
	private static void initiFreeling(LangENUM lang) throws Exception {

		// Instantiate Freeling resources if not already done
		if(!singletonLangTKMap.containsKey(lang) || singletonLangTKMap.get(lang) == null) {

			String resourcePath = PropertyManager.getProperty(PropertyManager.resourceFolder_fullPath);
			resourcePath = (resourcePath.endsWith(File.separator)) ? resourcePath : resourcePath + File.separator;

			DATA = resourcePath + "freeling/";

			// Set language
			String LANG = "en";
			switch(lang) {
			case English:
				LANG = "en";
				break;
			case Spanish:
				LANG = "es";
				break;
			case Catalan:
				LANG = "ca";
				break;
			default:
				LANG = "en";
			}

			logger.info("Initializing Freeling (language " + lang + ")...");

			// System.loadLibrary("libfreeling_javaAPI");
			System.load("/usr/local/FreeLing-4.0/lib/libfreeling-4.0.so");
			System.load("/home/ronzano/Downloads/FreeLing-4.0/APIs/java/libfreeling_javaAPI.so");

			Util.initLocale("default");

			if(lgid == null) {
				lgid = new LangIdent(DATA + "/common/lang_ident/ident.dat");
			}

			// Create options set for maco analyzer.
			// Default values are Ok, except for data files.
			MacoOptions op = new MacoOptions( LANG );

			op.setDataFiles( "", 
					DATA + "common/punct.dat",
					DATA + LANG + "/dicc.src",
					DATA + LANG + "/afixos.dat",
					"",
					DATA + LANG + "/locucions.dat", 
					DATA + LANG + "/np.dat",
					DATA + LANG + "/quantities.dat",
					DATA + LANG + "/probabilitats.dat");

			// Create analyzers.

			singletonLangTKMap.put(lang, new Tokenizer( DATA + LANG + "/tokenizer.dat" ));
			singletonLangTK_SYNCHmap.put(lang, new Object());

			singletonLangSpMap.put(lang, new Splitter( DATA + LANG + "/splitter.dat" ));
			singletonLangSp_SYNCHmap.put(lang, new Object());

			Maco mf = new Maco( op );
			mf.setActiveOptions(false, true, true, true,  // select which among created 
					true, true, false, true,  // submodules are to be used. 
					true, true, true, true);  // default: all created submodules 
			// are used
			singletonLangMfMap.put(lang, mf);
			singletonLangMf_SYNCHmap.put(lang, new Object());

			singletonLangTgMap.put(lang, new HmmTagger( DATA + LANG + "/tagger.dat", true, 2 ));
			singletonLangTg_SYNCHmap.put(lang, new Object());

			singletonLangParserMap.put(lang, new ChartParser( DATA + LANG + "/chunker/grammar-chunk.dat" ));
			singletonLangParser_SYNCHmap.put(lang, new Object());

			singletonLangDepMap.put(lang, new DepTxala( DATA + LANG + "/dep_txala/dependences.dat", singletonLangParserMap.get(lang).getStartSymbol() ));
			singletonLangDep_SYNCHmap.put(lang, new Object());

			singletonLangNeClassMap.put(lang, new Nec( DATA + LANG + "/nerc/nec/nec-ab-poor1.dat" ));
			singletonLangNe_SYNCHmap.put(lang, new Object());

			singletonLangSenMap.put(lang, new Senses( DATA + LANG + "/senses.dat" )); // sense dictionary
			singletonLangSen_SYNCHmap.put(lang, new Object());

			singletonLangDisMap.put(lang, new Ukb( DATA + LANG + "/ukb.dat" )); // sense disambiguator
			singletonLangDis_SYNCHmap.put(lang, new Object());

			logger.info("Freeling initialized (language " + lang + ").");

			// Init GATE
			GATEinit.initGate(PropertyManager.getProperty("gate.home"), PropertyManager.getProperty("gate.plugins"));
		}

	}

	@Override
	public Resource init() {
		try {
			String language = this.getAnalysisLang();

			language = (language == null || language.equals("")) ? "SPA" : language;

			this.setAnalysisLang(language);

			if(language != null && !language.trim().equals("")) {
				if(language.toLowerCase().equals("spa")) {
					initiFreeling(LangENUM.Spanish);
					analysisLangENUM = LangENUM.Spanish;
				}
				else if(language.toLowerCase().equals("cat")) {
					initiFreeling(LangENUM.Catalan);
					analysisLangENUM = LangENUM.Catalan;
				}
				else if(language.toLowerCase().equals("eng")) {
					initiFreeling(LangENUM.English);
					analysisLangENUM = LangENUM.English;
				}
				else {
					GenericUtil.notifyException("Impossible to initialize Freeling - language not supported", new Exception("Freeliong init except."), logger);
				}
			}
			else {
				GenericUtil.notifyException("Impossible to initialize Freeling - language not specified", new Exception("Freeliong init except."), logger);
			}

		} catch (Exception e) {
			GenericUtil.notifyException("Initializing Freeling", e, logger);
		}

		return this;
	}

	@Override
	public void execute() {

		localSecondsProcessing.set(0d);

		int parsedSentences = 0;

		long t1 = System.currentTimeMillis();

		// Reference to the current document to parse
		Document doc = getDocument();
		logger.debug("   - Start parsing document: " + ((doc.getName() != null && doc.getName().length() > 0) ? doc.getName() : "NO_NAME") );


		if(sentenceAnnotationSetToAnalyze != null && !sentenceAnnotationSetToAnalyze.equals("") &&
				sentenceAnnotationTypeToAnalyze != null && !sentenceAnnotationTypeToAnalyze.equals("")) {
			// Parse sentence by sentence with Freeling

			// Transferring sentences
			if(!sentenceAnnotationSetToAnalyze.equals(mainAnnSet)) {
				GATEutils.transferAnnotations(doc, sentenceAnnotationTypeToAnalyze, sentenceAnnotationTypeToAnalyze, sentenceAnnotationSetToAnalyze, mainAnnSet, null);
			}

			// Get all the sentence annotations (sentenceAnnotationSet) from the input annotation set (inputAnnotationSet)
			AnnotationSet inputAnnotationSet = document.getAnnotations(mainAnnSet);
			AnnotationSet sentenceAnnotationSet = inputAnnotationSet.get(sentenceAnnotationTypeToAnalyze);

			// Sort sentences
			List<Annotation> sentencesSorted = sortSetenceList(sentenceAnnotationSet);
			
			parsedSentences += annotateSentences(sentencesSorted, doc, this.analysisLangENUM);

			long needed = System.currentTimeMillis() - t1;
			logger.debug("   - End parsing document: " + doc.getName());
			logger.debug("     in (seconds): " + (needed / 1000) + ", parsed: " + parsedSentences + ", unparsed: " + (sentencesSorted.size() - parsedSentences) );
			logger.debug("********************************************");
		}
		else { // Identify and then parse sentences

			// Visiting all the sentences
			ListSentenceIterator sIt = null;
			try {
				sIt = new ListSentenceIterator(analyzeText(doc.getContent().getContent(0l, gate.Utils.lengthLong(doc)).toString(), analysisLangENUM));
			} catch (InvalidOffsetException e1) {
				GenericUtil.notifyException("Impossible to parse text by Freeling - language not supported", e1, logger);
			}


			// Add sentence annotations
			Map<Sentence, Annotation> sentenceIDannGATEMap = new HashMap<Sentence, Annotation>();
			while (sIt.hasNext()) {
				try {
					Sentence s = sIt.next();
					// DepTree dt = s.getDepTree(); - NOT USED
					// ParseTree pt = s.getParseTree(); - NOT USED

					Long stenStart = null;
					Long stenFinish = null;

					ListWordIterator wIt = new ListWordIterator(s);

					while (wIt.hasNext()) {
						Word w = wIt.next();

						if(w.getLemma() != null) {

							// Getting word features
							if(stenStart == null || stenStart > w.getSpanStart()) {
								stenStart = w.getSpanStart();
							}

							if(stenFinish == null || stenFinish < w.getSpanFinish()) {
								stenFinish = w.getSpanFinish();
							}

						}

					}

					Integer sentenceID = null;
					String finalMainAnnSet = mainAnnSet + ((this.getAddAnalysisLangToAnnSetName() != null && this.getAddAnalysisLangToAnnSetName().toLowerCase().trim().equals("true")) ? "_" + this.getAnalysisLang().trim() : "");
					try {
						sentenceID = doc.getAnnotations(finalMainAnnSet).add(stenStart, stenFinish, sentenceType, Factory.newFeatureMap());
					} catch (Exception e) {
						GenericUtil.notifyException("Impossible to add sentence annotation.", e, logger);
					}
					if(sentenceID != null) {
						Annotation sentAnn = doc.getAnnotations(finalMainAnnSet).get(sentenceID);
						sentenceIDannGATEMap.put(s, sentAnn);
					}

				}
				catch (Exception e) {
					GenericUtil.notifyException("Impossible to report annotations generated by Freeling.", e, logger);
				}
			}

			for(Entry<Sentence, Annotation> sentenceID : sentenceIDannGATEMap.entrySet()) {
				addSentenceWordAnnotations(sentenceID.getKey(), sentenceID.getValue(), doc, false);
			}
		}
	}

	/**
	 * Annotate by means of the Freeling parser a plain text in a specific language
	 * 
	 * @param text
	 * @param lang
	 * @return
	 */
	private ListSentence analyzeText(String text, LangENUM lang) {

		if(lang == null) {
			return null;
		}

		try {
			initiFreeling(lang);
		} catch (Exception e) {
			e.printStackTrace();
		}


		SWIGTYPE_p_splitter_status sid = null;
		synchronized(singletonLangSp_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			sid = singletonLangSpMap.get(lang).openSession();
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}	

		// Extract the tokens from the line of text
		ListWord l = null;
		synchronized(singletonLangTK_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			l = singletonLangTKMap.get(lang).tokenize(text);
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		ListSentence sentList = null;
		synchronized(singletonLangSp_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			sentList = singletonLangSpMap.get(lang).split(sid, l, false); // Original: true
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		// Perform morphological analysis
		synchronized(singletonLangMf_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			singletonLangMfMap.get(lang).analyze(sentList);
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		// Perform part-of-speech tagging.
		synchronized(singletonLangTg_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			singletonLangTgMap.get(lang).analyze(sentList);
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		// Perform named entity (NE) classification
		synchronized(singletonLangNe_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			singletonLangNeClassMap.get(lang).analyze(sentList);
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		// Sense dictionary tagger
		synchronized(singletonLangTg_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			singletonLangSenMap.get(lang).analyze(sentList);
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		// Sense disambiguator tagger
		synchronized(singletonLangDis_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			singletonLangDisMap.get(lang).analyze(sentList);
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		// Chunk parser
		synchronized(singletonLangParser_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			singletonLangParserMap.get(lang).analyze(sentList);
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		// Dependency parser
		synchronized(singletonLangDep_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			singletonLangDepMap.get(lang).analyze(sentList);
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		synchronized(singletonLangSp_SYNCHmap.get(lang)) {
			long startProc = System.currentTimeMillis();
			singletonLangSpMap.get(lang).closeSession(sid);
			totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
			localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
		}

		return sentList;

	}


	/**
	 * Annotate by means of the Freeling parser a set of sentences 
	 * 
	 * @param sentencesSorted list of sentences to annotate
	 * @param doc document the sentences belong to
	 * @param t threshold for the parser
	 * @return
	 */
	private int annotateSentences(List<Annotation> sentencesSorted, Document doc, LangENUM lang) {

		if(lang == null) {
			return 0;
		}
		
		try {
			initiFreeling(lang);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		int parsedSentences = 0;

		// Parse each sentence
		for (Annotation actualSentence : sentencesSorted) {

			try {

				String sentenceText = doc.getContent().getContent(actualSentence.getStartNode().getOffset(), actualSentence.getEndNode().getOffset()).toString();

				SWIGTYPE_p_splitter_status sid = null;
				synchronized(singletonLangSp_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					sid = singletonLangSpMap.get(analysisLangENUM).openSession();
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				// Extract the tokens from the line of text
				ListWord l = null; 
				synchronized(singletonLangTK_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					l = singletonLangTKMap.get(analysisLangENUM).tokenize(sentenceText);
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				Sentence sent = new Sentence(l);

				// Perform morphological analysis
				synchronized(singletonLangMf_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					singletonLangMfMap.get(analysisLangENUM).analyze(sent);
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				// Perform part-of-speech tagging.
				synchronized(singletonLangTg_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					singletonLangTgMap.get(analysisLangENUM).analyze(sent);
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				// Perform named entity (NE) classification
				synchronized(singletonLangNe_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					singletonLangNeClassMap.get(analysisLangENUM).analyze(sent);
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				// Sense dictionary tagger
				synchronized(singletonLangTg_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					singletonLangSenMap.get(analysisLangENUM).analyze(sent);
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				// Sense disambiguator tagger
				synchronized(singletonLangDis_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					singletonLangDisMap.get(analysisLangENUM).analyze(sent);
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				// Chunk parser
				synchronized(singletonLangParser_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					singletonLangParserMap.get(analysisLangENUM).analyze(sent);
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				// Dependency parser
				synchronized(singletonLangDep_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					singletonLangDepMap.get(analysisLangENUM).analyze(sent);
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				synchronized(singletonLangSp_SYNCHmap.get(analysisLangENUM)) {
					long startProc = System.currentTimeMillis();
					singletonLangSpMap.get(analysisLangENUM).closeSession(sid);
					totSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
					localSecondsProcessing.addAndGet(((double) (System.currentTimeMillis() - startProc) / 1000d));
				}

				addSentenceWordAnnotations(sent, actualSentence, doc, true);

				parsedSentences++;


			} catch (Exception e) {
				GenericUtil.notifyException("Error parsing sentence: " + ((GATEutils.getAnnotationText(actualSentence, doc).orElse(null) != null) ? GATEutils.getAnnotationText(actualSentence, doc).orElse("") : "NULL"), e, logger);
			}

		}

		return parsedSentences;
	}


	/**
	 * Given a set of Freeling parsed sentence, transfer the annotations to the sentence of the 
	 * original GATE document
	 * 
	 * @param freelingSent
	 * @param gateSentAnn
	 * @param doc
	 */
	private Long annNum = 0l;
	private void addSentenceWordAnnotations(Sentence freelingSent, Annotation gateSentAnn, Document doc, boolean considerBaseGATEoffset) {

		Long baseSentenceOffset = (gateSentAnn != null) ? gateSentAnn.getStartNode().getOffset() : null;

		// Maps to store the start offset, end offset, name and features of the annotations that will be added to the inputText in a GATE document
		Map<Long, Long> GATEann_StartOffset = new HashMap<Long, Long>();
		Map<Long, Long> GATEann_EndOffset = new HashMap<Long, Long>();
		Map<Long, String> GATEann_annName = new HashMap<Long, String>();
		Map<Long, FeatureMap> GATEann_FeatureMap = new HashMap<Long, FeatureMap>();

		// Grouping all the annotation features by means of the same integer
		annNum = 0l;

		Sentence s = freelingSent;
		ParseTree pt = s.getParseTree();

		// Iterating sentence words
		ListWordIterator wIt = new ListWordIterator(s);

		Long stenStart = null;
		Long stenFinish = null;

		Map<String, Long> wordAnnotationIdMap = new HashMap<String, Long>();
		while (wIt.hasNext()) {
			Word w = wIt.next();

			if(w.getLemma() != null) {
				try {

					// Getting word features
					if(stenStart == null || stenStart > w.getSpanStart()) {
						stenStart = w.getSpanStart();
					}

					if(stenFinish == null || stenFinish < w.getSpanFinish()) {
						stenFinish = w.getSpanFinish();
					}

					FeatureMap wordFeats = Factory.newFeatureMap();

					wordFeats.put(tokenType_startSpanFeatName, w.getSpanStart());
					wordFeats.put(tokenType_endSpanFeatName, w.getSpanFinish());
					wordFeats.put(tokenType_lemmaFeatName, w.getLemma());
					wordFeats.put(tokenType_positionFeatName, "" + w.getPosition());

					if(w.getForm() != null) {
						wordFeats.put(tokenType_formStringFeatName, w.getForm());
					}

					if(w.getTag() != null) {
						wordFeats.put(tokenType_POSFeatName, w.getTag());
					}

					if(w.getPhForm() != null && !w.getPhForm().equals("")) {
						wordFeats.put(tokenType_phFormStringFeatName, w.getPhForm());
					}

					Long spanSt = w.getSpanStart();
					Long spanF = w.getSpanFinish();

					wordAnnotationIdMap.put(spanSt + "_" + spanF, new Long(annNum));

					// *** Add word annotation ***
					GATEann_StartOffset.put(annNum, ((considerBaseGATEoffset) ? baseSentenceOffset : 0l) + spanSt);
					GATEann_EndOffset.put(annNum, ((considerBaseGATEoffset) ? baseSentenceOffset : 0l) + spanF);
					GATEann_annName.put(annNum, tokenType);
					GATEann_FeatureMap.put(annNum, wordFeats);
					annNum++;
				} catch (Exception e) {
					GenericUtil.notifyException("Error adding token annotations of sentence: " + ((GATEutils.getAnnotationText(gateSentAnn, doc).orElse(null) != null) ? GATEutils.getAnnotationText(gateSentAnn, doc).orElse("") : "NULL"), e, logger);
				}
			}
		}
		wIt.delete();

		/* https://github.com/TALP-UPC/FreeLing/blob/master/APIs/java/Analyzer.java
		 * Parse tree - chunker results
		 * Every node is a leaf or not and can subsume other nodes
		 */
		expandeParseTreeChunks(doc, pt, wordAnnotationIdMap,
				GATEann_StartOffset, GATEann_EndOffset, GATEann_annName, GATEann_FeatureMap,
				considerBaseGATEoffset, baseSentenceOffset);

		// Delete words
		wIt = new ListWordIterator(s);
		while (wIt.hasNext()) {
			Word w = wIt.next();
			w.delete();
		}
		wIt.delete();


		// Adding annotations to GATE document
		for(Entry<Long, Long> featID : GATEann_StartOffset.entrySet()) {
			Long featIDvalue = featID.getKey();

			if(GATEann_StartOffset.get(featIDvalue) != null && GATEann_EndOffset.get(featIDvalue) != null &&
					GATEann_annName.get(featIDvalue) != null && !GATEann_annName.get(featIDvalue).equals("") && GATEann_FeatureMap.get(featIDvalue) != null) {
				try {
					String finalMainAnnSet = mainAnnSet + ((this.getAddAnalysisLangToAnnSetName() != null && this.getAddAnalysisLangToAnnSetName().toLowerCase().trim().equals("true")) ? "_" + this.getAnalysisLang().trim() : "");
					doc.getAnnotations(finalMainAnnSet).add(GATEann_StartOffset.get(featIDvalue), GATEann_EndOffset.get(featIDvalue), GATEann_annName.get(featIDvalue), GATEann_FeatureMap.get(featIDvalue));
					logger.debug("Added feature: " + GATEann_annName.get(featIDvalue) + " (" + GATEann_StartOffset.get(featIDvalue) + ", " + GATEann_EndOffset.get(featIDvalue) + ") feature map size: " + GATEann_FeatureMap.get(featIDvalue).size());
				} catch (Exception e) {
					GenericUtil.notifyException("Error adding feature of type: " + ((GATEann_annName.get(featIDvalue) != null) ? GATEann_annName.get(featIDvalue) : "NULL"), e, logger);
				}
			}
			else {
				logger.warn("******** IMPOSSIBLE TO ADD ANNOTATION ******** TYPE: " + ((GATEann_annName.get(featIDvalue) != null) ? GATEann_annName.get(featIDvalue) : "NULL"));
			}
		}
	}

	private void expandeParseTreeChunks(Document gateDoc, ParseTree pt, Map<String, Long> wordAnnotationIdMap,
			Map<Long, Long> GATEann_StartOffset, Map<Long, Long> GATEann_EndOffset, Map<Long, String> GATEann_annName, Map<Long, FeatureMap> GATEann_FeatureMap,
			boolean considerBaseGATEoffset, Long baseSentenceOffset) {

		if(pt == null) {
			logger.warn("Null chunck tree!");
			return;
		}

		long numChildren = pt.numChildren();

		if(numChildren == 0) {
			// it's a leaf
			Integer chunkID = new Integer(annNum.intValue());
			annNum++;

			Word ptRootWoord = pt.begin().getInformation().getWord();
			long spanSt = ptRootWoord.getSpanStart();
			long spanF = ptRootWoord.getSpanFinish();
			String wordIdentifier = spanSt + "_" + spanF;

			if(pt.begin().getInformation().isHead() && wordAnnotationIdMap.containsKey(wordIdentifier) && GATEann_FeatureMap.get(wordAnnotationIdMap.get(wordIdentifier)) != null) {
				GATEann_FeatureMap.get(wordAnnotationIdMap.get(wordIdentifier)).put(tokenType_chunkHeadIDFeat + "_" + chunkID, chunkID + "");
				if(pt.begin().getLabel() != null) {
					GATEann_FeatureMap.get(wordAnnotationIdMap.get(wordIdentifier)).put(chunkType_labelFeatName + "_" + chunkID, pt.begin().getLabel());
				}
			}	
		}
		else {
			// it's NOT a leaf

			long chunkStart = getLowestStartNodeParseTreeChunks(pt);
			long chunkEnd = getHighestEndNodeParseTreeChunks(pt);

			// logger.debug("LABEL: " + pt.begin().getLabel() + " - FROM: " + chunkStart + " ---> TO: " + chunkEnd);

			if(chunkStart < chunkEnd) {
				Integer chunkID = new Integer(annNum.intValue());
				annNum++;

				FeatureMap chunkFm = Factory.newFeatureMap();

				// Chunk label
				if(pt.begin().getLabel() != null) {
					chunkFm.put(chunkType_labelFeatName, pt.begin().getLabel());
					chunkFm.put(tokenType_chunkHeadIDFeat + "_" + chunkID, chunkID + "");
					chunkFm.put(chunkType_isChunkFeatName, pt.begin().isChunk());

				}

				// Chunk head word
				Word headWord = ParseTree.getHeadWord(new TreeConstPreorderIteratorNode(pt.begin()));
				if(headWord != null) {
					long spanSt = headWord.getSpanStart();
					long spanF = headWord.getSpanFinish();
					String wordIdentifier = spanSt + "_" + spanF;

					if(wordAnnotationIdMap.containsKey(wordIdentifier) && GATEann_FeatureMap.get(wordAnnotationIdMap.get(wordIdentifier)) != null) {
						GATEann_FeatureMap.get(wordAnnotationIdMap.get(wordIdentifier)).put(tokenType_chunkHeadIDFeat + "_" + chunkID, chunkID + "");
					}
					else {
						logger.warn("NULL WORD CORRESPONDENCE! " + pt.begin().getInformation().isHead() + " > " + wordIdentifier);
					}

					// logger.debug("       > HEAD WORD: " + headWord.getForm());
					chunkFm.put(chunkType_headWordStringFeatName + "_" + chunkID, headWord.getForm() + "");
				}
				else {
					logger.warn("NULL HEAD WORD!");
				}


				// *** Add word annotation ***
				// logger.debug("Chunk annotation:  from: " + (((considerBaseGATEoffset) ? baseSentenceOffset : 0l) + chunkStart) + " to " + (((considerBaseGATEoffset) ? baseSentenceOffset : 0l) + chunkEnd));
				GATEann_StartOffset.put(annNum, (((considerBaseGATEoffset) ? baseSentenceOffset : 0l) + chunkStart));
				GATEann_EndOffset.put(annNum, (((considerBaseGATEoffset) ? baseSentenceOffset : 0l) + chunkEnd));
				GATEann_annName.put(annNum, chunkType);
				GATEann_FeatureMap.put(annNum, chunkFm);
				annNum++;
			}

			for (int i=0; i < numChildren; i++) {
				ParseTree child = pt.nthChildRef(i);

				if (child != null) {
					expandeParseTreeChunks(gateDoc, child, wordAnnotationIdMap,
							GATEann_StartOffset, GATEann_EndOffset, GATEann_annName, GATEann_FeatureMap, considerBaseGATEoffset, baseSentenceOffset);
				}
				else {
					logger.warn("Unexpected parse tree null child!");
				}
			}

		}
	}

	private long getLowestStartNodeParseTreeChunks(ParseTree pt) {
		long numChildren = pt.numChildren();
		if(numChildren == 0) {
			return pt.begin().getInformation().getWord().getSpanStart();
		}
		else {
			Long lowestSpanGlobal = null;
			for (int i=0; i < numChildren; i++) {
				ParseTree child = pt.nthChildRef(i);

				if (child != null) {
					long lowestSpan = getLowestStartNodeParseTreeChunks(child);
					if(lowestSpanGlobal == null || lowestSpan < lowestSpanGlobal) {
						lowestSpanGlobal = lowestSpan;
					}
				}
				else {
					logger.warn("Unexpected parse tree null child!");
				}

			}

			return lowestSpanGlobal;
		}
	}

	private long getHighestEndNodeParseTreeChunks(ParseTree pt) {
		long numChildren = pt.numChildren();
		if(numChildren == 0) {
			return pt.begin().getInformation().getWord().getSpanFinish();
		}
		else {
			Long highestSpanGlobal = null;
			for (int i=0; i < numChildren; i++) {
				ParseTree child = pt.nthChildRef(i);

				if (child != null) {
					long highestSpan = getHighestEndNodeParseTreeChunks(child);
					if(highestSpanGlobal == null || highestSpan > highestSpanGlobal) {
						highestSpanGlobal = highestSpan;
					}
				}
				else {
					logger.warn("Unexpected parse tree null child!");
				}

			}

			return highestSpanGlobal;
		}
	}

	/**
	 * Given a gate.AnnotationSet instance, returns a sorted list of its elements.
	 * Sorting is done by position (offset) in the document.
	 * 
	 * @param sentences gate.Annotation
	 * 
	 * @return Sorted list of gate.Annotation} instances.
	 */
	public List<Annotation> sortSetenceList(AnnotationSet sentences) {
		List<Annotation> sentencesSorted = new ArrayList<Annotation>(sentences);
		Collections.sort(sentencesSorted, new OffsetComparator());
		return sentencesSorted;
	}


	public boolean resetAnnotations() {
		document.removeAnnotationSet(mainAnnSet);
		return true;
	}

	/**
	 * Example of usage of the FreelingParser to parse a Spanish and an English text
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		// Set the full path to the BioAB Miner property file
		PropertyManager.setPropertyFilePath("/full/path/to/BioAbMinerConfig.properties");
		
		// Parsing the following SPanish text (ESstr) by Freeling
		String strES = "El nuevo coche tiene las ventanas más grandes.";

		try {
			GATEinit.initGate(PropertyManager.getProperty("gate.home"), PropertyManager.getProperty("gate.plugins"));
			Gate.getCreoleRegister().registerComponent(FreelingParser.class);

			FeatureMap FreelingParserfm = Factory.newFeatureMap();
			FreelingParserfm.put("analysisLang", "SPA");
			FreelingParser FreelingParser_Resource = (FreelingParser) gate.Factory.createResource(FreelingParser.class.getName(), FreelingParserfm);

			Document gateDoc = gate.Factory.newDocument(strES);
			FreelingParser_Resource.setDocument(gateDoc);
			FreelingParser_Resource.execute();
			
			// Store the results of the parsed text as a 
			String storageFilePath = "/path/to/store/annotated/text/Freeling_ESstr.xml";
			GATEfiles.storeGateXMLToFile(gateDoc, storageFilePath);

			System.out.println("Stored to '" + storageFilePath + "' the parsed text - original text:\n" + strES);
		} 
		catch(Exception e) {
			e.printStackTrace();
		}
		
		// Parsing the following English text (ENstr) by Freeling
		String strEN = "The new car has bigger windows.";

		try {
			GATEinit.initGate(PropertyManager.getProperty("gate.home"), PropertyManager.getProperty("gate.plugins"));
			Gate.getCreoleRegister().registerComponent(FreelingParser.class);

			FeatureMap FreelingParserfm = Factory.newFeatureMap();
			FreelingParserfm.put("analysisLang", "ENG");
			FreelingParser FreelingParser_Resource = (FreelingParser) gate.Factory.createResource(FreelingParser.class.getName(), FreelingParserfm);

			Document gateDoc = gate.Factory.newDocument(strES);
			FreelingParser_Resource.setDocument(gateDoc);
			FreelingParser_Resource.execute();
			
			String storageFilePath = "/path/to/store/annotated/text/Freeling_ENstr.xml";
			GATEfiles.storeGateXMLToFile(gateDoc, storageFilePath);

			System.out.println("Stored to '" + storageFilePath + "' the parsed text - original text:\n" + strEN);
		} 
		catch(Exception e) {
			e.printStackTrace();
		}
	}

}