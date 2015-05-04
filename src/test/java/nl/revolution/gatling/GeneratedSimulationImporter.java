package nl.revolution.gatling;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class GeneratedSimulationImporter {

    public static final String PROJECT_BASE_DIR = "/Users/bertjan/IdeaProjects/sandbox/gatling-har-import";
    public static final String RECORDER_BASE_DIR = "/Users/bertjan/Downloads/gatling/gatling-charts-highcharts-bundle-2.1.5";
    public static final String SIMULATION_INPUT_PATH = RECORDER_BASE_DIR + "/user-files/simulations/simulations/RecordedSimulation.scala";
    public static final String REQUEST_BODIES_INPUT_PATH = RECORDER_BASE_DIR + "/user-files/bodies/";
    public static final String REQUEST_BODIES_TARGET_PATH = PROJECT_BASE_DIR + "/src/test/resources/request-bodies";
    public static final String SIMULATION_OUTPUT_PATH = PROJECT_BASE_DIR + "/src/test/scala/simulations/RecordedSimulation.scala";

    public static void main(String... args) throws IOException {
        new GeneratedSimulationImporter().performImport();
    }

    public void performImport() throws IOException {
        cleanOldData();
        processNewData();

        System.out.println("Done.");
    }


    /**
     * Performs cleanup of old simulation data.
     */
    private void cleanOldData() throws IOException {
        // Step 1: delete request bodies.
        System.out.println("Deleting old request bodies.");
        Path directory = Paths.get(REQUEST_BODIES_TARGET_PATH);
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().contains("RecordedSimulation")) {
                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // Step 2: delete previous simulation.
        System.out.println("Deleting old simulation.");
        try {
            Files.delete(Paths.get(SIMULATION_OUTPUT_PATH));
        } catch (NoSuchFileException e) {
            // no problem, bro.
        }
    }


    /**
     * Copies and processes new simulation data.
     */
    private void processNewData() throws IOException {
        // Step 3: copy request bodies
        System.out.println("Copying new request bodies.");
        Path reqBodies = Paths.get(REQUEST_BODIES_INPUT_PATH);
        Files.walkFileTree(reqBodies, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().contains("RecordedSimulation")) {
                    Files.copy(file, Paths.get(REQUEST_BODIES_TARGET_PATH + "/" + file.getFileName()));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // Step 4: process generated Scala code.
        System.out.println("Processing generated simulation.");
        List<String> inputLines = Files.readAllLines(Paths.get(SIMULATION_INPUT_PATH));
        List<String> outputLines = new ArrayList<>();

        int index = 0;
        int dtdlLoginIndex = 0;
        for (String line : inputLines) {

            // Replace hard-coded xsrf tokens with dummy headers.
            if (line.contains("\"x-xsrf-token\" -> ")) {
                line = line.replaceAll("\"x-xsrf-token\" -> \"(.)*\"", "\"dummy\" -> \"dummy\"");
            }

            // Set all pauses to 0 secs.
            if (line.contains(".pause(")) {
                line = line.replaceAll(".pause((.)*)", ".pause(0)");
            }

            // Add xsrf token and content-type to all posts.
            if (line.contains(".post(")) {
                line = line.replaceAll(".post((.)*)", "$0.header(\"x-xsrf-token\", session => session(\"xsrf-token\").as[String]).header(\"Content-Type\", \"application/json\")");
            }

            // Keep line number of dtdl login request.
            if (line.contains("\"/dtdl/login")) {
                dtdlLoginIndex = index;
            }

            // Set request name based on HTTP method and URI.
            if (line.contains("http(\"request_")) {
                String nextLine = inputLines.get(index + 1);
                String method = nextLine.split("\\.")[1].split("\\(")[0];
                String uri = nextLine.split("\"")[1];
                line = line.replaceAll("\"request_(.)+\"", "\"" + method.toUpperCase() + " " + uri + "\"");
            }

            // Remove generic auth & content-type headersd and connection keepalive.
            if (!line.contains(".authorizationHeader(\"")
                    && !line.contains(".contentTypeHeader(\"")
                    && !line.contains(".connection(\"")
                    && !line.contains("\"cdn.mathjax.org\"")
                    && !line.contains("\"www.google-analytics.com\"")) {
                outputLines.add(line);
            }

            // Insert xsrf-token processing after pause statement when /dtdl/login has been processed.
            if (dtdlLoginIndex > 0 && line.contains("pause") && (index-dtdlLoginIndex < 4)) {
                // add xsrf token
                outputLines.add("\t\t.exec(session => {");
                outputLines.add("\t\t\timport io.gatling.http.cookie._");
                outputLines.add("\t\t\tsession(\"gatling.http.cookies\").validate[CookieJar].map {");
                outputLines.add("\t\t\t\tcookieJar => session.set(\"xsrf-token\", cookieJar.store.get(CookieKey(\"xsrf-token\", \"p3.tst.malmberg.nl\", \"/\")).orNull.cookie.getValue)");
                outputLines.add("\t\t\t}");
                outputLines.add("\t\t})");
            }

            index++;
        };

        // Write changed file to disk
        Files.write(Paths.get(SIMULATION_OUTPUT_PATH), outputLines);
    }
}
