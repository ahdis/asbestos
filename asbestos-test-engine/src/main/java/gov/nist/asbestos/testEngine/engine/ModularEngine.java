package gov.nist.asbestos.testEngine.engine;

import gov.nist.asbestos.client.Base.EC;
import gov.nist.asbestos.client.Base.ParserBase;
import gov.nist.asbestos.client.client.FhirClient;
import gov.nist.asbestos.client.debug.StopDebugTestScriptException;
import gov.nist.asbestos.client.debug.TestScriptDebugState;
import gov.nist.asbestos.client.resolver.ResourceWrapper;
import gov.nist.asbestos.simapi.validation.Val;
import gov.nist.asbestos.testEngine.engine.fixture.FixtureMgr;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.TestReport;
import org.hl7.fhir.r4.model.TestScript;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ModularEngine {
    private static Logger log = Logger.getLogger(ModularEngine.class.getName());

    private List<TestEngine> engines = new ArrayList<>();
    private boolean saveLogs = false;
    private String testName;
    private Map<String, String> reports = new HashMap<>();   // name => TestReport json
    private ModularScripts modularScripts;

    public ModularEngine(File testDefDir) {
        this(testDefDir, (URI) null);
    }

    public ModularEngine(File testDefDir, TestScript testScript) {
        this(testDefDir, (URI) null);
        getMainTestEngine().setTestScript(testScript);
    }

    public ModularEngine(File testDefDir, URI sut) {
        this(testDefDir, sut, null);
    }

    public ModularEngine(File testDefDir, URI sut, TestScriptDebugState state) {
        nameFromTestDefDir(testDefDir);
        Set<String> moduleIds = new LinkedHashSet<>();
        for (TestEngine te : engines) {
           moduleIds.addAll(te.moduleIds);
        }
        TestEngine testEngine = sut == null ? new TestEngine(testDefDir, moduleIds) : new TestEngine(testDefDir, sut, moduleIds);
        testEngine.setTestScriptDebugState(state);
        engines.add(testEngine);
        testEngine.setModularEngine(this);
    }

    public boolean isClientTest() {
        return getMainTestEngine().getSut() == null;
    }

    private void nameFromTestDefDir(File testDefDir) {
        Objects.requireNonNull(testDefDir);
        String[] parts = testDefDir.toString().split(Pattern.quote(File.separator));
        int i = parts.length - 1;
        if (parts[i].equals(""))
            i--;
        testName = parts[i];
    }

    public ModularEngine add(TestEngine engine) {
        engines.add(engine);
        return this;
    }

    public TestEngine getMainTestEngine() {
        TestEngine te = engines.get(0);
        return engines.get(0);
    }

    public String reportsAsJson() {
        return new ModularReports(reports).asJson();
    }

    public ModularEngine setSaveLogs(boolean save) {
        saveLogs = save;
        return this;
    }

    public List<TestEngine> getTestEngines() {
        return engines;
    }

    private void installModuleNames() {
        boolean first = true;
        for (TestEngine engine : engines) {
            TestReport report = engine.getTestReport();
            String scriptReportName = stripExtension(engine.getTestScriptName());
            if (report.hasExtension()) {
                for (Extension e : report.getExtension()) {
                    if ("urn:moduleId".equals(e.getUrl())) {
                        scriptReportName = e.getValue().toString();
                    }
                }
            }
            String moduleName = first ? null : scriptReportName;

            report.setName(this.testName + (moduleName == null ? "" : '/' + moduleName));
            first = false;
        }
    }

    void saveLogs() {
        boolean first = true;
        String channelId = getMainTestEngine().getChannelId();
        String testCollection = getMainTestEngine().getTestCollection();
        for (TestEngine engine : engines) {
            TestReport report = engine.getTestReport();
            String scriptName = stripExtension(engine.getTestScriptName());

            // scriptReportName can be different from scriptName if same script (module) used more than once
            String scriptReportName = scriptName;
            if (report.hasExtension()) {
                for (Extension e : report.getExtension()) {
                    if ("urn:moduleId".equals(e.getUrl())) {
                        scriptReportName = e.getValue().toString();
                    }
                }
            }
            String moduleName = first ? null : scriptReportName;

            report.setName(this.testName + (moduleName == null ? "" : '/' + moduleName));
            String json = ParserBase.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(report);
            reports.put(report.getName(), json);

            if (saveLogs) {
                Path path = new EC(engine.getExternalCache()).getTestLog(
                        channelId,
                        testCollection,
                        this.testName,
                        moduleName
                ).toPath();
                try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                    writer.write(json);
                    writer.flush();
                } catch (IOException e) {
                    log.log(Level.SEVERE, e.toString(), e);
                    throw new RuntimeException(e);
                }
            }

            first = false;
        }
    }

    String stripExtension(String name) {
        if (name == null) return null;
        int i = name.lastIndexOf(".");
        if (i == -1) return name;
        return name.substring(0, i);
    }


    //
    // Delegation to TestEngine
    //

    public ModularEngine setTestSession(String testSession) {
        getMainTestEngine().setTestSession(testSession);
        return this;
    }

    public ModularEngine setChannelId(String channelId) {
        getMainTestEngine().setChannelId(channelId);
        return this;
    }

    public ModularEngine setExternalCache(File externalCache) {
        getMainTestEngine().setExternalCache(externalCache);
        return this;
    }

    public ModularEngine setVal(Val val) {
        getMainTestEngine().setVal(val);
        return this;
    }

    public ModularEngine setFhirClient(FhirClient fhirClient) {
        getMainTestEngine().setFhirClient(fhirClient);
        return this;
    }

    public ModularEngine setTestCollection(String testCollection) {
        getMainTestEngine().setTestCollection(testCollection);
        return this;
    }

    public ModularEngine addCache(File cacheDir) {
        getMainTestEngine().addCache(cacheDir);
        return this;
    }

    public ModularEngine runTest() {
        try {
            getMainTestEngine().runTest();
        } catch (StopDebugTestScriptException sdex) {
            throw sdex;
        } finally {
            saveLogs();
        }
        return this;
    }

    public TestReport getTestReport() {
        return getMainTestEngine().getTestReport();
    }

    public List<TestReport> getTestReports() {
        List<TestReport> reports = new ArrayList<>();
        for (TestEngine engine : getTestEngines()) {
            reports.add(engine.getTestReport());
        }
        return reports;
    }

    public FixtureMgr getFixtureMgr() {
        return getMainTestEngine().getFixtureMgr();
    }

    public ModularEngine setTestId(String theTestId) {
        getMainTestEngine().setTestId(theTestId);
        return this;
    }

    public ModularEngine runEval(ResourceWrapper request, ResourceWrapper response, boolean skipAll) {
        getMainTestEngine().runEval(request, response, skipAll);
        installModuleNames();
        return this;
    }

    public ModularEngine setModularScripts() {
        try {
            if (modularScripts == null)
                modularScripts = new ModularScripts(new EC(getMainTestEngine().getExternalCache()), getMainTestEngine().getTestCollectionId(), getMainTestEngine().getTestDef(), ModularScriptRunMode.BACK_END_TEST_RUNNER);
        } catch (Exception ex) {
            log.log(Level.SEVERE, "setModularScripts Exception: " + ex.toString(), ex);
        }
        return this;
    }


    ModularScripts getModularScripts() {
        return modularScripts;
    }
}
