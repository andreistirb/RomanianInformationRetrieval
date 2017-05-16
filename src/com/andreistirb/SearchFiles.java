package com.andreistirb;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.Buffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by andrei on 23.04.2017.
 */
public class SearchFiles {

    private static boolean filtering = false;

    private SearchFiles() {
    }

    /**
     * Simple command-line based search demo.
     */
    public static void main(String[] args) throws Exception {
        String usage =
                "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-queries file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }

        String index = "index";
        String field = "contents";
        String queries = null;
        int repeat = 0;
        boolean raw = false;
        String queryString = null;
        int hitsPerPage = 10;
        String[] fileTypes = null;
        Date fileDate = null;
        Set<String> mFilesSet = new HashSet<>();

        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                index = args[i + 1];
                i++;
            } else if ("-field".equals(args[i])) {
                field = args[i + 1];
                i++;
            } else if ("-queries".equals(args[i])) {
                queries = args[i + 1];
                i++;
            } else if ("-query".equals(args[i])) {
                queryString = args[i + 1];
                i++;
            } else if ("-repeat".equals(args[i])) {
                repeat = Integer.parseInt(args[i + 1]);
                i++;
            } else if ("-raw".equals(args[i])) {
                raw = true;
            } else if ("-paging".equals(args[i])) {
                hitsPerPage = Integer.parseInt(args[i + 1]);
                if (hitsPerPage <= 0) {
                    System.err.println("There must be at least 1 hit per page.");
                    System.exit(1);
                }
                i++;
            } else if("-filter".equals(args[i])){
                filtering = true;
            }
        }

        if(filtering) {
            //read the filters file
            try (
                    InputStream fis = new FileInputStream("filters.txt");
                    InputStreamReader isr = new InputStreamReader(fis, Charset.defaultCharset());
                    BufferedReader br = new BufferedReader(isr);
            ) {
                //read first line which contains the types of the files to be searched
                Stream<String> lines;
                lines = br.lines();

                DateFormat dateFormat = new SimpleDateFormat("YYYY/MM/DD");
                for (Iterator<String> i = lines.iterator(); i.hasNext(); ) {
                    String line;
                    line = i.next();
                    if (line.startsWith("#")) { //ignore comments in filters file
                    } else if (line.startsWith("files: ")) {
                        line = line.replaceFirst("files: ", "");
                        fileTypes = line.split(",");
                        mFilesSet.add("pdf");
                        mFilesSet.add("txt");
                        mFilesSet.add("epub");
                        for (int j = 0; j < fileTypes.length; j++) {
                            mFilesSet.remove(fileTypes[j]); //remove from the hashset those file types parsed from the user file
                        }
                    } else if (line.startsWith("date:")) {
                        line = line.replaceFirst("date: ", "");
                        fileDate = dateFormat.parse(line);
                        System.out.println(fileDate.toString());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new MyAnalyzer();

        BufferedReader in = null;

        if (queries != null) {
            in = Files.newBufferedReader(Paths.get(queries), StandardCharsets.UTF_8);
        } else {
            in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }

        QueryParser parser = new QueryParser(field, analyzer);
        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
        BooleanQuery booleanQuery = booleanQueryBuilder.build();
        while (true) {
            if (queries == null && queryString == null) {// prompt the user
                System.out.println("Enter query: ");
            }

            String line = queryString != null ? queryString : in.readLine();

            if (line == null || line.length() == -1) {
                break;
            }

            line = line.trim();
            if (line.length() == 0) {
                break;
            }

            Query query;

            if(filtering) {
                for (Iterator<String> iterator = mFilesSet.iterator(); iterator.hasNext(); ) {
                    String mString = iterator.next();
                    Query mQuery = new TermQuery(new Term("filetype", mString));
                    booleanQueryBuilder.add(mQuery, BooleanClause.Occur.MUST_NOT);
                }
                query = parser.parse(line);
                //Date currentDate = new Date();
                //Query dateQuery = IntPoint.newRangeQuery("modified", ((int) fileDate.getTime()),(int)currentDate.getTime());
                booleanQueryBuilder.add(query, BooleanClause.Occur.MUST);
                //booleanQueryBuilder.add(dateQuery, BooleanClause.Occur.MUST);

                booleanQuery = booleanQueryBuilder.build();
                System.out.println("Query: " + booleanQuery.toString());
                System.out.println("Searching for: " + query.toString(field));

            }
            else {
                query = parser.parse(line);
            }

            if (repeat > 0) {                           // repeat & time as benchmark
                Date start = new Date();
                for (int i = 0; i < repeat; i++) {
                    searcher.search(query, 100);
                }
                Date end = new Date();
                System.out.println("Time: " + (end.getTime() - start.getTime()) + "ms");
            }

            if(filtering){
                doPagingSearch(in, searcher, booleanQuery, hitsPerPage, raw, queries == null && queryString == null);
            }
            else {
                doPagingSearch(in, searcher, query, hitsPerPage, raw, queries == null && queryString == null);
            }
            if (queryString != null) {
                break;
            }
        }
        reader.close();

    }

    public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query,
                                      int hitsPerPage, boolean raw, boolean interactive) throws IOException {
        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5 * hitsPerPage);
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = results.totalHits;
        System.out.println(numTotalHits + " total matching documents");

        int start = 0;
        int end = Math.min(numTotalHits, hitsPerPage);

        while (true) {
            if (end > hits.length) {
                System.out.println("Only results 1 - " + hits.length + " of " + numTotalHits + " total matching documents collected.");
                System.out.println("Collect more (y/n) ?");
                String line = in.readLine();
                if (line.length() == 0 || line.charAt(0) == 'n') {
                    break;
                }
                hits = searcher.search(query, numTotalHits).scoreDocs;
            }

            end = Math.min(hits.length, start + hitsPerPage);

            for (int i = start; i < end; i++) {
                if (raw) {
                    System.out.println("doc=" + hits[i].doc + " score= " + hits[i].score);
                    continue;
                }

                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                if (path != null) {
                    System.out.println((i + 1) + ". " + path);
                    String title = doc.get("title");
                    if (title != null) {
                        System.out.println("    Title: " + doc.get("title"));
                    }
                } else {
                    System.out.println((i + 1) + ". " + "No path for this document");
                }
            }

            if (!interactive || end == 0) {
                break;
            }

            if (numTotalHits >= end) {
                boolean quit = false;
                while (true) {
                    System.out.print("Press ");
                    if (start - hitsPerPage >= 0) {
                        System.out.print("(p)revious page, ");
                    }
                    if (start + hitsPerPage < numTotalHits) {
                        System.out.print("(n)ext page, ");
                    }
                    System.out.println("(q)uit or enter number to jump to a page.");

                    String line = in.readLine();
                    if (line.length() == 0 || line.charAt(0) == 'q') {
                        quit = true;
                        break;
                    }
                    if (line.charAt(0) == 'p') {
                        start = Math.max(0, start - hitsPerPage);
                        break;
                    } else if (line.charAt(0) == 'n') {
                        if (start + hitsPerPage < numTotalHits) {
                            start += hitsPerPage;
                        }
                        break;
                    } else {
                        int page = Integer.parseInt(line);
                        if ((page - 1) * hitsPerPage < numTotalHits) {
                            start = (page - 1) * hitsPerPage;
                            break;
                        } else {
                            System.out.println("No such page");
                        }
                    }
                }
                if (quit) break;
                end = Math.min(numTotalHits, start + hitsPerPage);
            }
        }
    }
}
