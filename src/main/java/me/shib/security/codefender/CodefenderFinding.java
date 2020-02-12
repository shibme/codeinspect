package me.shib.security.codefender;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public final class CodefenderFinding {

    private static final transient String cveBaseURL = "https://nvd.nist.gov/vuln/detail/";
    private transient CodefenderResult result;

    private String title;
    private int priority;
    private Map<String, String> fields;
    private Set<String> keys;
    private Set<String> tags;

    CodefenderFinding(CodefenderResult result, String title, int priority) {
        this.result = result;
        this.title = title;
        this.priority = priority;
        this.fields = new HashMap<>();
        this.keys = new HashSet<>();
        this.tags = new HashSet<>();
    }

    public void update() {
        result.updateVulnerability(this);
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

    public void addKey(String key) throws CodefenderException {
        if (key == null || key.isEmpty()) {
            throw new CodefenderException("Null or Empty key cannot be processed");
        }
        this.keys.add(key);
        this.addTag(key);
    }

    public void addTag(String tag) {
        this.tags.add(tag);
    }

    public String getTitle() {
        return this.title;
    }

    public int getPriority() {
        return priority;
    }

    public void setCVE(String cve) {
        if (cve != null && cve.toUpperCase().startsWith("CVE")) {
            setField("CVE", "[" + cve + "](" + cveBaseURL + cve + ")");
        }
    }

    public void setCVEs(List<String> cves) {
        Set<String> cveSet = new HashSet<>(cves);
        StringBuilder cveContent = new StringBuilder();
        for (String cve : cveSet) {
            if (cve != null && cve.toUpperCase().startsWith("CVE")) {
                cveContent.append("[").append(cve).append("](").append(cveBaseURL).append(cve).append(")").append(" ");
            }
        }
        if (!cveContent.toString().isEmpty()) {
            setField("CVEs", cveContent.toString().trim());
        }
    }

    public void setField(String label, String content) {
        this.fields.put(label, content);
    }

    Map<String, String> getFields() {
        return fields;
    }

    Set<String> getKeys() {
        return this.keys;
    }

    Set<String> getTags() {
        return this.tags;
    }

    @Override
    public String toString() {
        StringBuilder content = new StringBuilder();
        content.append("Title:\t").append(title)
                .append("\nPriority:\t").append(priority);
        if (tags.size() > 0) {
            content.append("\nTags:");
            for (String tag : tags) {
                content.append(" ").append(tag);
            }
        }
        for (String label : fields.keySet()) {
            content.append("\n").append(label).append(":\t").append(fields.get(label));
        }
        content.append("\n");
        return content.toString();
    }
}
