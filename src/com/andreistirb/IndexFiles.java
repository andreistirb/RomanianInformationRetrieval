package com.andreistirb;

import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.parser.epub.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import static org.apache.commons.lang3.StringEscapeUtils.unescapeJava;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by andrei on 05.04.2017.
 */
public class IndexFiles {

    private IndexFiles(){}
    private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

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
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext pcontext = new ParseContext();
            PDFParser pdfparser;
            EpubParser epubParser;
            Date modifiedDate = new Date(lastModified);

            Field pathField = new StringField("path", file.toString(), Field.Store.YES);
            doc.add(pathField);

            //doc.add(new LongPoint("modified", lastModified));
            String modifiedDateString = dateFormat.format(modifiedDate);
            System.out.println(modifiedDateString);
            doc.add(new TextField("modified",modifiedDateString, Field.Store.YES));

            if(file.toString().endsWith(".pdf")){
                try {

                    //parsing the document using PDF parser
                    pdfparser = new PDFParser();
                    pdfparser.parse(stream, handler, metadata, pcontext);

                }
                catch(Exception e){
                    System.out.println(e.getMessage());
                }
                //getting the content of the document
                doc.add(new TextField("contents", unescapeJava(handler.toString()), Field.Store.YES));
                doc.add(new StringField("filetype","pdf", Field.Store.YES));
            }
            else if(file.toString().endsWith(".epub")){
                try{
                    //parsing the document using Epub Parser
                    epubParser = new EpubParser();
                    epubParser.parse(stream, handler, metadata, pcontext);
                }
                catch(Exception e){
                    System.out.println(e.getMessage());
                }
                doc.add(new TextField("contents", unescapeJava(handler.toString()), Field.Store.YES));
                doc.add(new StringField("filetype","epub", Field.Store.YES));
            }
            else{
                doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(stream))));
                doc.add(new StringField("filetype","txt", Field.Store.YES));
            }
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
