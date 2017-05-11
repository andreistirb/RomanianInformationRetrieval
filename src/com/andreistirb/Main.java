package com.andreistirb;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Created by andrei on 06.04.2017.
 */
public class Main {

    public static void main(String[] args){
        String usage = "java org.apache.lucene.demo.IndexFiles"
                + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                + "in INDEX_PATH that can be searched with SearchFiles";
        List<String> stopWords = new ArrayList<>();
        String indexPath = "index";
        String docsPath = null;
        boolean create = true;
        for(int i=0;i<args.length;i++){
            if("-index".equals(args[i])){
                indexPath = args[i+1];
                i++;
            } else if("-docs".equals(args[i])){
                docsPath = args[i+1];
                i++;
            } else if("-update".equals(args[i])){
                create = false;
            }
        }

        if(docsPath == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        final Path docDir = Paths.get(docsPath);
        if(!Files.isReadable(docDir)){
            System.out.println("Document directory " + docDir.toAbsolutePath() + " does not exist" +
                    " or is not readable, please check the path");
            System.exit(1);
        }

        //extract stop words from file
        Stream<String> lineStream;

        try(
                InputStream fis = new FileInputStream("stopwords.txt");
                InputStreamReader isr = new InputStreamReader(fis, Charset.defaultCharset());
                BufferedReader br = new BufferedReader(isr)
                ){
            lineStream = br.lines();
            for(Iterator<String> i = lineStream.iterator();i.hasNext();){
                String stopword = i.next();
                stopWords.add(stopword);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }

        Set wordstop = StopFilter.makeStopSet(stopWords);

        Date start = new Date();

        try{
            System.out.println("Indexing to directory " + indexPath);

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new MyAnalyzer(CharArraySet.unmodifiableSet(CharArraySet.copy(wordstop)));//stopWordsSet);
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            System.out.println(create);

            if(create) {
                //create a new index in the directory, removing any other previous index
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            } else{
                //Add new documents to existing index
                iwc.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
            }

            IndexWriter writer = new IndexWriter(dir, iwc);
            IndexFiles.indexDocs(writer, docDir);

            writer.close();

            Date end = new Date();

            System.out.println(end.getTime() - start.getTime() + " total miliseconds");

        } catch(Exception e){
            System.out.println(e.getMessage());
        }

    }

}
