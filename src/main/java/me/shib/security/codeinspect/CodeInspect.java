package me.shib.security.codeinspect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.shib.security.codeinspect.scanners.java.dependencycheck.DependencyCheck;
import me.shib.security.codeinspect.scanners.java.findsecbugs.FindSecBugsScanner;
import me.shib.security.codeinspect.scanners.javascript.retirejs.RetirejsScanner;
import me.shib.security.codeinspect.scanners.ruby.brakeman.BrakemanScanner;
import me.shib.security.codeinspect.scanners.ruby.bundleraudit.BundlerAudit;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class CodeInspect {

    private static final transient String cveBaseURL = "https://nvd.nist.gov/vuln/detail/";
    private static final transient Gson gson = new GsonBuilder().setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create();
    private static final transient Set<CodeInspect> inspectors = new HashSet<>();

    private final transient CodeInspectConfig config;
    private final transient CodeInspectResult result;

    public CodeInspect(CodeInspectConfig config) {
        this.config = config;
        this.result = new CodeInspectResult(config.getProject(), getLang(),
                getContext(), getTool(), config.getScanDirPath());
    }

    static synchronized void addScanner(CodeInspect codeinspect) {
        inspectors.add(codeinspect);
    }

    private static synchronized List<CodeInspect> getCodeInspects(CodeInspectConfig config) {
        List<CodeInspect> qualifiedClasses = new ArrayList<>();
        System.out.println("Attempting to run for Language: " + config.getLang());
        if (config.getLang() != null) {
            for (CodeInspect codeinspect : inspectors) {
                try {
                    if (codeinspect.getLang() != null && config.getLang() == codeinspect.getLang()) {
                        if (config.getTool() == null || config.getTool().isEmpty() ||
                                config.getTool().equalsIgnoreCase(codeinspect.getTool())) {
                            if (config.getContext() == null || config.getContext() == codeinspect.getContext()) {
                                qualifiedClasses.add(codeinspect);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return qualifiedClasses;
    }

    private static synchronized void prepareScanners(CodeInspectConfig config) {
        CodeInspect.addScanner(new BrakemanScanner(config));
        CodeInspect.addScanner(new BundlerAudit(config));
        CodeInspect.addScanner(new RetirejsScanner(config));
        CodeInspect.addScanner(new DependencyCheck(config));
        CodeInspect.addScanner(new FindSecBugsScanner(config));
    }

    public static synchronized List<CodeInspect> getScanners(CodeInspectConfig config) throws CodeInspectException {
        prepareScanners(config);
        List<CodeInspect> scanners = CodeInspect.getCodeInspects(config);
        if (scanners.size() > 0) {
            try {
                buildProject(config.getBuildScript(), config.getScanDir());
                for (CodeInspect codeinspect : scanners) {
                    codeinspect.result.setProject(config.getProject());
                }
            } catch (IOException | InterruptedException e) {
                throw new CodeInspectException(e);
            }
        } else {
            System.out.println("No scanners available to scan this code.");
        }
        return scanners;
    }

    private static synchronized void buildProject(String buildScript, File scanDir) throws IOException, InterruptedException, CodeInspectException {
        if (buildScript != null) {
            System.out.println("Running: " + buildScript);
            CommandRunner commandRunner = new CommandRunner(buildScript, scanDir, "Building Project");
            if (commandRunner.execute() != 0) {
                throw new CodeInspectException("Build Failed!");
            }
        }
    }

    protected static void writeToFile(String content, File file) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(file);
        pw.append(content);
        pw.close();
    }

    protected String getUrlForCVE(String cve) throws CodeInspectException {
        if (cve != null && cve.toUpperCase().startsWith("CVE")) {
            return cveBaseURL + cve;
        }
        throw new CodeInspectException("CVE provided is not valid");
    }

    protected String getHash(File file, int lineNo, String type, String[] args) throws IOException {
        return getHash(file, lineNo, lineNo, type, args);
    }

    protected String getHash(File file, int lineNo, String type) throws IOException {
        return getHash(file, lineNo, lineNo, type, null);
    }

    protected String getHash(File file, int startLineNo, int endLineNo, String type) throws IOException {
        return getHash(file, startLineNo, endLineNo, type, null);
    }

    protected String getHash(File file, int startLineNo, int endLineNo, String type, String[] args) throws IOException {
        class HashableContent {
            private String filePath;
            private String snippet;
            private String type;
            private String[] args;
        }
        List<String> lines = readLinesFromFile(file);
        if (startLineNo <= endLineNo && endLineNo <= lines.size() && startLineNo > 0) {
            StringBuilder snippet = new StringBuilder();
            snippet.append(lines.get(startLineNo - 1));
            for (int i = startLineNo; i < endLineNo; i++) {
                snippet.append("\n").append(lines.get(i));
            }
            HashableContent hashableContent = new HashableContent();
            hashableContent.type = type;
            hashableContent.filePath = file.getAbsolutePath().replaceFirst(config.getScanDir().getAbsolutePath(), "");
            hashableContent.snippet = snippet.toString();
            hashableContent.args = args;
            return DigestUtils.sha1Hex(gson.toJson(hashableContent));
        }
        return null;
    }

    protected CodeInspectFinding newFinding(String title, CodeInspectPriority priority) {
        return result.newFinding(title, priority);
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

    protected CodeInspectConfig getConfig() {
        return config;
    }

    public String getProject() {
        return result.getProject();
    }

    public String getScanner() {
        return result.getScanner();
    }

    public String getScanDirPath() {
        return result.getScanDirPath();
    }

    public List<CodeInspectFinding> getFindings() {
        return result.getFindings();
    }

    public abstract Lang getLang();

    public abstract String getTool();

    public abstract Context getContext();

    protected abstract void scan() throws Exception;

    public enum Context {
        SAST("CodeInspect-SAST"),
        SCA("CodeInspect-SCA");

        private final String label;

        Context(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return getLabel();
        }
    }

}
