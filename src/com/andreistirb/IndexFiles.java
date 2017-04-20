package com.andreistirb;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

/**
 * Created by andrei on 05.04.2017.
 */
public class IndexFiles {

    private IndexFiles(){}


    static void indexDocs(final IndexWriter writer, Path path) throws IOException{
        if(Files.isDirectory(path)){
            Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException{
                    try{
                        indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
                    } catch(Exception e){

                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else{
            indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
        }
    }

    //index a single document
    static void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
        try(InputStream stream = Files.newInputStream(file)){
            Document doc = new Document();

            Field pathField = new StringField("path", file.toString(), Field.Store.YES);
            doc.add(pathField);

            doc.add(new LongPoint("modified", lastModified));

            doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))));

            if(writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE){
                System.out.println("adding " + file);
                writer.addDocument(doc);
            } else {
                System.out.println("updating " + file);
                writer.updateDocument(new Term("path", file.toString()), doc);
            }
        }
    }
}
