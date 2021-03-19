/**
 * This file is part of CERMINE project.
 * Copyright (c) 2011-2018 ICM-UW
 *
 * CERMINE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CERMINE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with CERMINE. If not, see <http://www.gnu.org/licenses/>.
 */

package pl.edu.icm.cermine;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import pl.edu.icm.cermine.bibref.model.BibEntry;
import pl.edu.icm.cermine.configuration.ExtractionConfigRegister;
import pl.edu.icm.cermine.configuration.ExtractionConfigProperty;
import pl.edu.icm.cermine.content.citations.ContentStructureCitationPositions;
import pl.edu.icm.cermine.content.model.BxContentStructure;
import pl.edu.icm.cermine.content.model.ContentStructure;
import pl.edu.icm.cermine.content.transformers.BxContentToDocContentConverter;
import pl.edu.icm.cermine.exception.AnalysisException;
import pl.edu.icm.cermine.exception.TransformationException;
import pl.edu.icm.cermine.metadata.model.DocumentAffiliation;
import pl.edu.icm.cermine.metadata.model.DocumentMetadata;
import pl.edu.icm.cermine.structure.model.BxDocument;
import pl.edu.icm.cermine.tools.timeout.TimeoutRegister;

/**
 * Extraction utility class
 *
 * @author Dominika Tkaczyk (d.tkaczyk@icm.edu.pl)
 */
public class ExtractionUtils {

    private static void debug(double start, String msg) {
        if (ExtractionConfigRegister.get().getBooleanProperty(ExtractionConfigProperty.DEBUG_PRINT_TIME)) {
            double elapsed = (System.currentTimeMillis() - start) / 1000.;
            System.out.println(msg + ": " + elapsed);
        }
    }
    
    //1.1 Character extraction
    public static BxDocument extractCharacters(ComponentConfiguration conf, InputStream stream) 
            throws AnalysisException {
        long start = System.currentTimeMillis();
        BxDocument doc = conf.getCharacterExtractor().extractCharacters(stream);// これで doc => <BxPage> => <BxChunk> にテキストと座標がはいる
        System.out.println("1.1 Character extraction 　ExtractionUtil L61");
        //System.out.println(doc.toText());
        debug(start, "1.1 Character extraction");
        return doc;
    }
    
    //1.2 Page segmentation
    public static BxDocument segmentPages(ComponentConfiguration conf, BxDocument doc) 
            throws AnalysisException {
        long start = System.currentTimeMillis();
        TimeoutRegister.get().check();
        // DocstrumSegmenter L48
        // 
        doc = conf.getDocumentSegmenter().segmentDocument(doc);//ここで <BxChunk> から <BxWord>, <BxLine>, <BxZone> を生成している，まだ１ページ目の日本語は存在
        //この時点で2段組が1段に認識されている。
        System.out.println("1.2 Page segmentation　ExtractionUtil L73");//２段組の２段がつながってしまうのはここが原因ぽい
        //System.out.println(doc.toText());//ここでtoText()が動くようになる and まだ日本語が残ってる and 消えた文章もまだある
        debug(start, "1.2 Page segmentation");
        return doc;
    }
    
    //1.3 Reading order resolving
    public static BxDocument resolveReadingOrder(ComponentConfiguration conf, BxDocument doc) 
            throws AnalysisException {
        long start = System.currentTimeMillis();
        doc = conf.getReadingOrderResolver().resolve(doc);//BxZoneの順序が変わってる？？
        //HierarchicalReadingOrderResolver L68．
        System.out.println("1.3 Reading order resolving　ExtractionUtil L84");
        //System.out.println(doc.toText());//消えている文章が存在、順番ごちゃ
        debug(start, "1.3 Reading order resolving");
        return doc;
    }
    
    //1.4 Initial classification
    public static BxDocument classifyInitially(ComponentConfiguration conf, BxDocument doc) 
            throws AnalysisException {
        long start = System.currentTimeMillis();
        doc = conf.getInitialClassifier().classifyZones(doc);//ここでも１ページ目日本語ある
        //System.out.println("1.4 Initial classification　ExtractionUtil L95");
        debug(start, "1.4 Initial classification");
        //System.out.println(doc.toText());//ここで順番ごちゃごちゃ
        return doc;
    }
    
    //2.1 Metadata classification
    public static BxDocument classifyMetadata(ComponentConfiguration conf, BxDocument doc) 
            throws AnalysisException {
        long start = System.currentTimeMillis();
        doc = conf.getMetadataClassifier().classifyZones(doc);//ここでも１ページ目日本語あるが，BIB_INFO, GEN_OTHER, CORRESPONDENCEってラベルがついてる
        System.out.println("2.1 Metadata classification ExtractionUtil L105");
        debug(start, "2.1 Metadata classification");
        return doc;
    }
    
    //2.2 Metadata cleaning
    public static DocumentMetadata cleanMetadata(ComponentConfiguration conf, BxDocument doc) 
            throws AnalysisException {
        long start = System.currentTimeMillis();
        //EnhancedMetadataEXtractor L97
        //ここが原因ぽい
        DocumentMetadata metadata = conf.getMetadataExtractor().extractMetadata(doc);//ここも１ページ目日本語ある
        System.out.println("2.1 Metadata cleaning ExtractionUtil L115");
        debug(start, "2.2 Metadata cleaning");
        return metadata;
    }
    
    //2.3 Affiliation parsing
    public static DocumentMetadata parseAffiliations(ComponentConfiguration conf, DocumentMetadata metadata)
            throws AnalysisException {
        long start = System.currentTimeMillis();
    	for (DocumentAffiliation aff : metadata.getAffiliations()) {
            conf.getAffiliationParser().parse(aff);//ここで日本語がくなってる
        }
        System.out.println("2.3 Affiliation parsing ExtractionUtil L127");
        debug(start, "2.3 Affiliation parsing");
        return metadata;
    }
    
    //3.1 Reference extraction
    public static List<String> extractRefStrings(ComponentConfiguration conf, BxDocument doc)
            throws AnalysisException {
        long start = System.currentTimeMillis();
        String[] refs = conf.getBibRefExtractor().extractBibReferences(doc);
        List<String> references = Lists.newArrayList(refs);
        System.out.println("3.1 Reference extraction ExtractionUtil L138");//ここもdocの１ページ目日本語ある
        debug(start, "3.1 Reference extraction");
        return references;
    }

    //3.2 Reference parsing
    public static List<BibEntry> parseReferences(ComponentConfiguration conf, List<String> refs)
            throws AnalysisException {
        long start = System.currentTimeMillis();
        List<BibEntry> parsedRefs = new ArrayList<BibEntry>();
        for (String ref : refs) {
            parsedRefs.add(conf.getBibRefParser().parseBibReference(ref));
        }
        System.out.println("3.2 Reference parsing ExtractionUtil L151");
        debug(start, "3.2 Reference parsing");
        return parsedRefs;
    }
    
    //4.1 Content filtering
    public static BxDocument filterContent(ComponentConfiguration conf, BxDocument doc) 
            throws AnalysisException {
        long start = System.currentTimeMillis();
        doc = conf.getContentFilter().filter(doc);
        System.out.println("4.1 Content filtering ExtractionUtil L161");//ここも１ページ目日本語ある
        debug(start, "4.1 Content filtering");
        //System.out.println(doc.toText());//ここでも消えてる文章ある、既に順番はごちゃごちゃ
        return doc;
    }
    
    //4.2 Headers extraction
    public static BxContentStructure extractHeaders(ComponentConfiguration conf, BxDocument doc) 
            throws AnalysisException {
        long start = System.currentTimeMillis();
        //extractHeaders は HeuristicContentHeadersExtractor.java
        BxContentStructure contentStructure = conf.getContentHeaderExtractor().extractHeaders(doc);//ここで章のタイトルが出てる，日本語論文だと出てこない
        System.out.println("4.2 Headers extraction ExtractionUtil L171");//ここも１ページ目日本語ある
        debug(start, "4.2 Headers extraction");
        return contentStructure;
    }
    
    //4.3 Headers clustering
    public static BxContentStructure clusterHeaders(ComponentConfiguration conf, BxContentStructure contentStructure) //このcontentStructure に１ページ目の日本語が入ってない
            throws AnalysisException {
        long start = System.currentTimeMillis();
        conf.getContentHeaderClusterizer().clusterHeaders(contentStructure);
        System.out.println("4.3 Headers clustering ExtractionUtil L181");
        debug(start, "4.3 Headers clustering");
        return contentStructure;
    }
    
    //4.4 Content cleaner
    public static ContentStructure cleanStructure(ComponentConfiguration conf, BxContentStructure contentStructure) 
            throws AnalysisException {
        try {
            long start = System.currentTimeMillis();
            conf.getContentCleaner().cleanupContent(contentStructure);
            BxContentToDocContentConverter converter = new BxContentToDocContentConverter();
            ContentStructure structure = converter.convert(contentStructure);
            System.out.println("4.4 Content cleaning ExtractionUtil L194");

            debug(start, "4.4 Content cleaning");
            return structure;
        } catch (TransformationException ex) {
            throw new AnalysisException(ex);
        }
    }

    //4.5 Citation positions finding
    public static ContentStructureCitationPositions findCitationPositions(ComponentConfiguration conf, 
            ContentStructure struct, List<BibEntry> citations) {
        long start = System.currentTimeMillis();
        ContentStructureCitationPositions positions = conf.getCitationPositionFinder().findReferences(struct, citations);
        System.out.println("4.5 Citation positions finding ExtractionUtil L207");
        debug(start, "4.5 Citation positions finding");
        return positions;
    }
  
    public enum Step {
        
        CHARACTER_EXTRACTION    (null),
        
        PAGE_SEGMENTATION       (setOf(CHARACTER_EXTRACTION)),
        
        READING_ORDER           (setOf(PAGE_SEGMENTATION)),
        
        INITIAL_CLASSIFICATION  (setOf(READING_ORDER)),
     
        METADATA_CLASSIFICATION (setOf(INITIAL_CLASSIFICATION)),
        
        METADATA_CLEANING       (setOf(METADATA_CLASSIFICATION)),
        
        AFFILIATION_PARSING     (setOf(METADATA_CLEANING)),
        
        REFERENCE_EXTRACTION    (setOf(INITIAL_CLASSIFICATION)),
        
        REFERENCE_PARSING       (setOf(REFERENCE_EXTRACTION)),
        
        CONTENT_FILTERING       (setOf(INITIAL_CLASSIFICATION)),
        
        HEADER_DETECTION        (setOf(CONTENT_FILTERING)),
        
        TOC_EXTRACTION          (setOf(HEADER_DETECTION)),
        
        CONTENT_CLEANING        (setOf(TOC_EXTRACTION)),
        
        CITPOS_DETECTION        (setOf(CONTENT_CLEANING, REFERENCE_PARSING));
        
        private Set<Step> prerequisites;

        Step(Set<Step> prerequisites) {
            this.prerequisites = prerequisites;
        }

        public Set<Step> getPrerequisites() {
            return prerequisites;
        }
        
        private static Set<Step> setOf(Step... steps) {
            return Sets.newHashSet(steps);
        }

        static {
            for (Step s : values()) {
                if (s.prerequisites == null) {
                    s.prerequisites = EnumSet.noneOf(Step.class);
                } else {
                    s.prerequisites = EnumSet.copyOf(s.prerequisites);
                }
            }
        }
        
    }
    
}
