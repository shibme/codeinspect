package me.shib.security.codeinspect;

import me.shib.steward.Steward;
import me.shib.steward.StewardConfig;
import me.shib.steward.StewardData;

import java.util.ArrayList;
import java.util.List;

final class CodeInspectLauncher {

    private static void processResults(CodeInspectConfig config, List<CodeInspect> inspectors) {
        try {
            List<CodeInspectFinding> findings = new ArrayList<>();
            for (CodeInspect codeinspect : inspectors) {
                findings.addAll(codeinspect.getFindings());
            }
            StewardData data = StewardAdapter.toStewardData(config, findings);
            Steward.process(data, StewardConfig.getConfig());
        } catch (Exception e) {
            throw new CodeInspectException(e);
        }
    }

    public static void main(String[] args) {
        CodeInspectConfig config = CodeInspectConfig.getInstance();
        List<CodeInspect> scanners = CodeInspect.getScanners(config);
        for (CodeInspect codeinspect : scanners) {
            System.out.println("Now running scanner: " + codeinspect.getTool());
            try {
                codeinspect.scan();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        processResults(config, scanners);
    }
}
