package me.shib.security.codefender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class Codefender {

    private static final transient Gson gson = new GsonBuilder().setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create();
    private static final transient Set<Codefender> codefenders = new HashSet<>();

    private transient CodefenderConfig config;
    private transient CodefenderResult result;

    public Codefender(CodefenderConfig config) {
        this.config = config;
        this.result = new CodefenderResult(config.getProject(), getLang(),
                getContext(), getTool(), config.getScanDirPath());
    }

    public static void addScanner(Codefender codefender) {
        codefenders.add(codefender);
    }

    private static synchronized List<Codefender> getCodefends(CodefenderConfig config) {
        List<Codefender> qualifiedClasses = new ArrayList<>();
        System.out.println("Attempting to run for Language: " + config.getLang());
        if (config.getLang() != null) {
            for (Codefender codefender : codefenders) {
                try {
                    if (codefender.getLang() != null && codefender.getLang() == config.getLang()) {
                        if (config.getTool() == null || config.getTool().isEmpty() ||
                                config.getTool().equalsIgnoreCase(codefender.getTool())) {
                            qualifiedClasses.add(codefender);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return qualifiedClasses;
    }

    private static void processResults(List<CodefenderResult> results) {
        for (CodefenderResult result : results) {
            List<CodefenderFinding> vulnerabilities = result.getVulnerabilities();
            StringBuilder content = new StringBuilder();
            content.append("Project:\t").append(result.getProject()).append("\n");
            content.append("Context:\t").append(result.getContext()).append("\n");
            content.append("Language:\t").append(result.getLang()).append("\n");
            content.append("Scanner:\t").append(result.getScanner()).append("\n");
            content.append("Count:\t").append(vulnerabilities.size()).append("\n");
            content.append("Vulnerabilities:").append("\n");
            for (CodefenderFinding vulnerability : vulnerabilities) {
                content.append("\n").append(vulnerability);
            }
            System.out.println(content);
        }
        try {
            writeToFile(gson.toJson(results), new File("codefender-results.json"));
        } catch (FileNotFoundException e) {
            throw new CodefenderException(e);
        }
    }

    static synchronized void start(CodefenderConfig config) throws CodefenderException {
        if (config == null) {
            config = CodefenderConfig.getInstance();
        }
        List<Codefender> codefenders = Codefender.getCodefends(config);
        List<CodefenderResult> scannedResults = new ArrayList<>();
        if (codefenders.size() > 0) {
            try {
                buildProject(config.getBuildScript(), config.getScanDir());
                for (Codefender codefender : codefenders) {
                    try {
                        codefender.getResult().setProject(config.getProject());
                        System.out.println("Now running scanner: " + codefender.getTool());
                        codefender.scan();
                        scannedResults.add(codefender.getResult());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException | InterruptedException e) {
                throw new CodefenderException(e);
            }
            processResults(scannedResults);
        } else {
            System.out.println("No scanners available to scan this code.");
        }
    }

    private static synchronized void buildProject(String buildScript, File scanDir) throws IOException, InterruptedException, CodefenderException {
        if (buildScript != null) {
            System.out.println("Running: " + buildScript);
            CommandRunner commandRunner = new CommandRunner(buildScript, scanDir, "Building Project");
            if (commandRunner.execute() != 0) {
                throw new CodefenderException("Build Failed!");
            }
        }
    }

    protected static void writeToFile(String content, File file) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(file);
        pw.append(content);
        pw.close();
    }

    protected CodefenderFinding newVulnerability(String title, int priority) {
        return result.newVulnerability(title, priority);
    }

    protected String runCommand(String command) throws IOException, InterruptedException {
        CommandRunner commandRunner = new CommandRunner(command, config.getScanDir(), getTool());
        commandRunner.execute();
        return commandRunner.getResult();
    }

    private List<String> readLinesFromFile(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        if (file.exists() && !file.isDirectory()) {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
            br.close();
        }
        return lines;
    }

    protected String readFromFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String line : readLinesFromFile(file)) {
            content.append(line).append("\n");
        }
        return content.toString();
    }

    private CodefenderResult getResult() {
        return result;
    }

    protected boolean isParserOnly() {
        return config.isParseOnly();
    }

    protected abstract Lang getLang();

    public abstract String getTool();

    public abstract Context getContext();

    public abstract void scan() throws Exception;

    public enum Context {
        SAST, SCA
    }

}
