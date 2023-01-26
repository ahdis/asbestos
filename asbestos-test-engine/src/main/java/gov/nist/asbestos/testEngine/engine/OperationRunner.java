package gov.nist.asbestos.testEngine.engine;

import gov.nist.asbestos.client.client.FhirClient;
import gov.nist.asbestos.client.client.Format;
import gov.nist.asbestos.client.resolver.Ref;
import gov.nist.asbestos.client.resolver.ResourceWrapper;
import gov.nist.asbestos.serviceproperties.ServiceProperties;
import gov.nist.asbestos.serviceproperties.ServicePropertiesEnum;
import gov.nist.asbestos.simapi.validation.ValE;
import gov.nist.asbestos.testEngine.engine.fixture.FixtureComponent;
import gov.nist.asbestos.testEngine.engine.fixture.FixtureMgr;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.TestReport;
import org.hl7.fhir.r4.model.TestScript;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public class OperationRunner {
    private String label;
    private String type;
    private String typePrefix;
    private ValE val;
    private FhirClient fhirClient = null;
    private TestScript testScript = null;
    private FixtureMgr fixtureMgr;
    private TestReport testReport = null;
    private URI sut = null;
    private Reporter reporter;
    private Map<String, String> externalVariables;
    private String testCollectionId = null;
    private String testId = null;
    private TestEngine testEngine = null;
    private ActionReference actionReference = null;
    private static Logger logger = Logger.getLogger(OperationRunner.class.getName());

    OperationRunner(ActionReference actionReference, FixtureMgr fixtureMgr, Map<String, String> externalVariables) {
        Objects.requireNonNull(fixtureMgr);
        this.fixtureMgr = fixtureMgr;
        this.externalVariables = externalVariables;
        this.actionReference = actionReference;
    }

    void run(TestScript.SetupActionOperationComponent op, TestReport.SetupActionOperationComponent operationReport, boolean isFollowedByAssert) {
        Objects.requireNonNull(typePrefix);
        Objects.requireNonNull(val);
        Objects.requireNonNull(fhirClient);
        Objects.requireNonNull(testScript);
        Objects.requireNonNull(operationReport);
        Objects.requireNonNull(testCollectionId);
        Objects.requireNonNull(testId);
        Objects.requireNonNull(actionReference);

        reporter = new Reporter(val, operationReport, "", "");

        operationReport.setResult(TestReport.TestReportActionResult.PASS);  // may be overwritten

        this.label = op.getLabel();
        type = typePrefix + ".operation";

        int elementCount = 0;
        if (op.hasSourceId()) elementCount++;
        if (op.hasTargetId()) elementCount++;
        if (op.hasParams()) elementCount++;
        if (op.hasUrl()) elementCount++;
        if (elementCount == 0 && sut == null) {
            reporter.reportError("has none of sourceId, targetId, params, url - one is required");
            return;
        }
        if (elementCount > 1) {
            boolean itsOk = false;
            if (op.hasSourceId()) {
                FixtureComponent fixtureComponent = fixtureMgr.get(op.getSourceId());
                if (fixtureComponent == null) {
                    reporter.reportError("fixture " + op.getSourceId() + " is not defined");
                    return;
                }
                if (!fixtureComponent.getResourceWrapper().hasHttpBase()) {
                    // it's ok - loaded as static fixture
                    itsOk = true;
                }
            }
            if (!itsOk) {
                // this one breaks the rules
                if (!(op.hasType() && op.getType().getCode().equals("save-to-cache"))) {
                    reporter.reportError("has multiple of sourceId, targetId, params, url - only one is allowed");
                    return;
                }
            }
        }
        if (!op.hasType()) {
            reporter.reportError("has no type");
            return;
        }

        if (op.hasDestination()) {
            reporter.reportError("destination not supported");
            return;
        }

        Coding typeCoding = op.getType(); // TODO: check if System is whether NIST FHIR Toolkit or FHIR TestScript
        String code = typeCoding.getCode();

        fhirClient.setFormat(op.hasContentType() ? Format.fromContentType(op.getContentType()) : fhirClient.getFormat());
        if (fhirClient.getFormat() == null)
            fhirClient.setFormat(Format.JSON);

        if ("read".equals(code)) {
            SetupActionRead setupActionRead = new SetupActionRead(actionReference, fixtureMgr, isFollowedByAssert)
                    .setVal(val)
                    .setFhirClient(fhirClient)
                    .setSut(sut)
                    .setType(type + ".read")
                    .setTestReport(testReport);
            setupActionRead.setVariableMgr(new VariableMgr(testScript, fixtureMgr)
                    .setExternalVariables(externalVariables)
                    .setVal(val)
                    .setOpReport(operationReport)
            );
            setupActionRead
                    .setTestCollectionId(testCollectionId)
                    .setTestId(testId)
                    .setTestEngine(testEngine);
            ResourceWrapper responseWrapper = setupActionRead.run(op, operationReport);
            if (responseWrapper != null) {
                FixtureLabels labels = new FixtureLabels(new ActionReporter(), op, null)
                        .referenceWrapper(responseWrapper);
                Reporter.operationDescription(operationReport, "**Request/Response** " + labels.getReference());
            }
        } else if ("search".equals(code)) {
            SetupActionSearch setupActionSearch = new SetupActionSearch(actionReference, fixtureMgr, isFollowedByAssert)
                    .setVal(val)
                    .setFhirClient(fhirClient)
                    .setSut(sut)
                    .setType(type + ".search")
                    .setTestReport(testReport);
            setupActionSearch
                    .setVal(val)
                    .setVariableMgr(
                            new VariableMgr(testScript, fixtureMgr)
                                    .setExternalVariables(externalVariables)
                                    .setVal(val)
                                    .setOpReport(operationReport));
            setupActionSearch.setTestEngine(testEngine);
            setupActionSearch.setTestCollectionId(testCollectionId);
            setupActionSearch.setTestId(testId);
            ResourceWrapper responseWrapper = setupActionSearch.run(op, operationReport);
            if (responseWrapper != null) {
                FixtureLabels labels = new FixtureLabels(new ActionReporter(), op, null)
                        .referenceWrapper(responseWrapper);
                Reporter.operationDescription(operationReport, "**Request/Response** " + labels.getReference());
            }
        } else if ("create".equals(code)) {
            SetupActionCreate setupActionCreate =
                    new SetupActionCreate(actionReference, fixtureMgr, isFollowedByAssert)
                            .setFhirClient(fhirClient)
                            .setType(type + ".create")
                            .setSut(sut)
                            .setVal(val);
            setupActionCreate.setTestEngine(testEngine);
            setupActionCreate.setVariableMgr(
                    new VariableMgr(testScript, fixtureMgr)
                            .setExternalVariables(externalVariables)
                            .setVal(val)
                            .setOpReport(operationReport));
            setupActionCreate
                    .setTestCollectionId(testCollectionId)
                    .setTestId(testId);
            ResourceWrapper responseWrapper = setupActionCreate.run(op, operationReport);
            if (responseWrapper != null) {
                FixtureLabels labels = new FixtureLabels(new ActionReporter(), op, null)
                        .referenceWrapper(responseWrapper);
                Reporter.operationDescription(operationReport, "**Request/Response** " + labels.getReference());
            }
        } else if ("delete".equals(code)) {
            SetupActionDelete setupActionDelete =
                    new SetupActionDelete(actionReference, fixtureMgr, isFollowedByAssert)
                            .setSut(sut)
                            .setFhirClient(fhirClient)
                            .setType(type + ".delete")
                            .setVal(val);
            setupActionDelete
                    .setTestEngine(testEngine);
            setupActionDelete.setVariableMgr(
                    new VariableMgr(testScript, fixtureMgr)
                            .setExternalVariables(externalVariables)
                            .setVal(val)
                            .setOpReport(operationReport));
            ResourceWrapper responseWrapper = setupActionDelete.run(op, operationReport);
            if (responseWrapper != null) {
                FixtureLabels labels = new FixtureLabels(new ActionReporter(), op, null)
                        .referenceWrapper(responseWrapper);
                Reporter.operationDescription(operationReport, "**Request/Response** " + labels.getReference());
            }
        } else if ("transaction".equals(code)) {
            SetupActionTransaction setupActionTransaction =
                    new SetupActionTransaction(actionReference, fixtureMgr,isFollowedByAssert)
                            .setSut(sut)
                            .setFhirClient(fhirClient)
                            .setType(type + ".transaction")
                            .setVal(val);
            setupActionTransaction
                    .setTestEngine(testEngine);
            setupActionTransaction.setVariableMgr(
                    new VariableMgr(testScript, fixtureMgr)
                            .setExternalVariables(externalVariables)
                            .setVal(val)
                            .setOpReport(operationReport));
            ResourceWrapper responseWrapper = setupActionTransaction.run(op, operationReport);
            if (responseWrapper != null) {
                FixtureLabels labels = new FixtureLabels(new ActionReporter(), op, null)
                        .referenceWrapper(responseWrapper);
                Reporter.operationDescription(operationReport, "**Request/Response** " + labels.getReference());
            }
        } else if ("mhd-pdb-transaction".equals(code)) {
            SetupActionMhdPdbTransaction setupActionTransaction =
                    new SetupActionMhdPdbTransaction(actionReference, fixtureMgr, isFollowedByAssert);
            setupActionTransaction
                    .setSut(sut)
                    .setFhirClient(fhirClient)
                    .setType(type + ".mhd-pdb-transaction")
                    .setVal(val)
                    .setTestCollectionId(testCollectionId)
                    .setTestId(testId);
            setupActionTransaction
                    .setTestEngine(testEngine);
            setupActionTransaction.setVariableMgr(
                    new VariableMgr(testScript, fixtureMgr)
                            .setExternalVariables(externalVariables)
                            .setVal(val)
                            .setOpReport(operationReport));
            ResourceWrapper responseWrapper = setupActionTransaction.run(op, operationReport);
            if (responseWrapper != null) {
                FixtureLabels labels = new FixtureLabels(new ActionReporter(), op, null)
                        .referenceWrapper(responseWrapper);
                Reporter.operationDescription(operationReport, "**Request/Response** " + labels.getReference());
            }
        } else if ("save-to-cache".equals(code)) {
            SaveToCache saveToCache = new SaveToCache(actionReference, fixtureMgr, isFollowedByAssert);
            saveToCache
                    .setVal(val)
                    .setTestEngine(testEngine);
            saveToCache.setVariableMgr(
                    new VariableMgr(testScript, fixtureMgr)
                            .setExternalVariables(externalVariables)
                            .setVal(val)
                            .setOpReport(operationReport))
                    .setTestCollectionId(testCollectionId)
                    .setTestId(testId);
            saveToCache.run(op, operationReport);
        } else if ("eventPart".equals(code)) {
            handleEventPartRequest(op, operationReport, isFollowedByAssert, code);
        } else if ("validate".equals(code)) {
            if (! hasRequestHeader(op, code)) {
                return;
            }

            VariableMgr variableMgr = new VariableMgr(testScript, fixtureMgr)
                    .setExternalVariables(externalVariables)
                    .setVal(val)
                    .setOpReport(operationReport);
            final String xUrlRequestHeader = "x-Url";
            String xUrlRequestHeaderValue = getHeaderComponentValue(xUrlRequestHeader, op, variableMgr);
            if (xUrlRequestHeaderValue == null) {
                String error = String.format("%s headerComponent value not found.", xUrlRequestHeader);
                logger.warning(error);
                reporter.reportError(error);
                return;
            }
            try {
                new URI(xUrlRequestHeaderValue);
            } catch (URISyntaxException e) {
                String error = String.format("%s is not a valid URI.", xUrlRequestHeader);
                logger.warning(error);
                reporter.reportError(error);
                return;
            }

            try {
                SetupActionTransaction validateAction = new SetupActionTransaction(actionReference, fixtureMgr, isFollowedByAssert) {
                    @Override
                    Ref buildTargetUrl() {
                        return new Ref(xUrlRequestHeaderValue);
                    }
                };
                validateAction.run(op, operationReport);
            } catch (Exception ex) {
                logger.warning(ex.toString());
                reporter.reportError(ex.toString());
            }

        } else if ("internalFtkRequest".equals(code)) {
            if (! hasRequestHeader(op, code)) {
                return;
            }

            VariableMgr variableMgr = new VariableMgr(testScript, fixtureMgr)
                    .setExternalVariables(externalVariables)
                    .setVal(val)
                    .setOpReport(operationReport);
            final String ftkInternalCode = "x-internalFtkRequestCode";
            String ftkInternalRequestCodeValue = getHeaderComponentValue(ftkInternalCode, op, variableMgr);
            if (ftkInternalRequestCodeValue == null) {
                reporter.reportError(String.format("%s headerComponent value not found.", ftkInternalCode));
                return;
            }
            FtkInternalRequestCode requestCode = FtkInternalRequestCode.find(ftkInternalRequestCodeValue);
            if (requestCode == null) {
                String error = "FtkInternalRequestCode cannot be null.";
                logger.severe(error);
                reporter.reportError(error);
                return;
            }
            switch (requestCode) {
                case FTK_LOAD_FIXTURE:
                case FTK_FUNCTION_CODE:
                    ftkOperation(op, operationReport, isFollowedByAssert, code, variableMgr, ftkInternalRequestCodeValue);
                    break;
                case FTK_GET_EVENT_PART:
                    handleEventPartRequest(op, operationReport, isFollowedByAssert, code);
                    break;
                default:
                    reporter.reportError("do not understand request code of " + ftkInternalCode + ":" + ftkInternalRequestCodeValue);
            }
        } else {
            reporter.reportError("do not understand code.code of " + code);
        }
    }

    private boolean hasRequestHeader(TestScript.SetupActionOperationComponent op, String code) {
        if (! op.hasRequestHeader()) {
            String error = String.format("%s is missing a requestHeader code.", code);
            logger.warning(error);
            reporter.reportError(error);
            return false;
        }
        return true;
    }

    private void handleEventPartRequest(TestScript.SetupActionOperationComponent op, TestReport.SetupActionOperationComponent operationReport, boolean isFollowedByAssert, String code) {
        SetupActionSearch setupActionSearch = new SetupActionSearch(actionReference, fixtureMgr, isFollowedByAssert)
                .setVal(val)
                .setFhirClient(fhirClient)
                .setSut(sut)
                .setType(String.format("%s.%s.search", type , code ))
                .setTestReport(testReport);
        setupActionSearch
                .setVal(val)
                .setVariableMgr(
                        new VariableMgr(testScript, fixtureMgr)
                                .setExternalVariables(externalVariables)
                                .setVal(val)
                                .setOpReport(operationReport));
        setupActionSearch.setTestEngine(testEngine);
        setupActionSearch.setTestCollectionId(testCollectionId);
        setupActionSearch.setTestId(testId);
        String internalBasePath = ServiceProperties.getInstance().getPropertyOrThrow(ServicePropertiesEnum.FHIR_TOOLKIT_BASE)
                .concat("/engine/")
                .concat(code)
                .concat("/")
                .concat(testEngine.getChannelId());
        setupActionSearch.setInternalBasePath(internalBasePath);
                /*
                TODO
               Remove the TestScript variable and use the test's Test.properties DependsOn property instead
               TestScript.xml variable:
                 <variable>
                <name value="dependsOnTestId"/>
                <defaultValue value="MHD_DocumentRecipient_minimal/1_Prerequisite_Single_Document_with_Binary"/>
                </variable>
                Test.properties:
                DependsOn=1_Prerequisite_Single_Document_with_Binary
                 */
        ResourceWrapper responseWrapper = setupActionSearch.run(op, operationReport);
        if (responseWrapper != null) {
            FixtureLabels labels = new FixtureLabels(new ActionReporter(), op, null)
                    .referenceWrapper(responseWrapper);
            /*
            Because there is no event registered for this code,
            the call to Reporter.operationDescription(operationReport, "**Request/Response** " + labels.getReference());
            results in
            "Request/Response null"
            where 'null' is a link to something like: https://fhirtoolkit.test:8082/session/default/channel/limited/collection/MHDv4_DocumentRecipient_minimal/test/null
             */
        }
    }

    private String getHeaderComponentValue(String ftkInternalCode, TestScript.SetupActionOperationComponent op, VariableMgr variableMgr) {
        String ftkInternalRequestCodeValue = null;
        Optional<TestScript.SetupActionOperationRequestHeaderComponent> headerComponent =  op.getRequestHeader().stream()
                .filter(s -> s.hasField() && s.getField().equals(ftkInternalCode)).findFirst();
        if (headerComponent.isPresent()) {
            Map<String, String> requestHeader = new HashMap<>();
            GenericSetupAction.updateRequestHeader(requestHeader, op, variableMgr);
            ftkInternalRequestCodeValue = requestHeader.get(ftkInternalCode);
        }
        return ftkInternalRequestCodeValue;
    }

    private void ftkOperation(TestScript.SetupActionOperationComponent op, TestReport.SetupActionOperationComponent operationReport, boolean isFollowedByAssert, String code, VariableMgr variableMgr, String ftkInternalRequestCodeValue) {
        SetupActionSearch setupActionSearch = new SetupActionSearch(actionReference, fixtureMgr, isFollowedByAssert)
                .setVal(val)
                .setFhirClient(fhirClient)
                .setSut(sut)
                .setType(String.format("%s.%s.%s.search", type, code, ftkInternalRequestCodeValue))
                .setTestReport(testReport);
        setupActionSearch
                .setVal(val)
                .setVariableMgr(
                        variableMgr);
        setupActionSearch.setTestEngine(testEngine);
        setupActionSearch.setTestCollectionId(testCollectionId);
        setupActionSearch.setTestId(testId);
        String internalBasePath = ServiceProperties.getInstance().getPropertyOrThrow(ServicePropertiesEnum.FHIR_TOOLKIT_BASE)
                .concat("/engine/")
                .concat(ftkInternalRequestCodeValue)
                .concat("/")
                .concat(testEngine.getChannelId())
                .concat("/")
                .concat(testCollectionId)
                .concat("/")
                .concat(testId);
        setupActionSearch.setInternalBasePath(internalBasePath);
        ResourceWrapper responseWrapper = setupActionSearch.run(op, operationReport);
        if (responseWrapper != null) {
            FixtureLabels labels = new FixtureLabels(new ActionReporter(), op, null)
                    .referenceWrapper(responseWrapper);
             /*
                Because there is no event registered for this code,
                the call to Reporter.operationDescription(operationReport, "**Request/Response** " + labels.getReference());
                results in
                "Request/Response null"
                where 'null' is a link to something like: https://fhirtoolkit.test:8082/session/default/channel/limited/collection/MHDv4_DocumentRecipient_minimal/test/null
             */
        }
    }

    public OperationRunner setTypePrefix(String typePrefix) {
        this.typePrefix = typePrefix;
        return this;
    }

    public OperationRunner setVal(ValE val) {
        this.val = val;
        return this;
    }

    public OperationRunner setFhirClient(FhirClient fhirClient) {
        this.fhirClient = fhirClient;
        return this;
    }

    public OperationRunner setTestScript(TestScript testScript) {
        this.testScript = testScript;
        return this;
    }

    public OperationRunner setTestReport(TestReport testReport) {
        this.testReport = testReport;
        return this;
    }

    public OperationRunner setSut(URI sut) {
        this.sut = sut;
        return this;
    }

    public OperationRunner setTestCollectionId(String testCollectionId) {
        this.testCollectionId = testCollectionId;
        return this;
    }

    public OperationRunner setTestId(String testId) {
        this.testId = testId;
        return this;
    }

    public OperationRunner setTestEngine(TestEngine testEngine) {
        this.testEngine = testEngine;
        return this;
    }
}
