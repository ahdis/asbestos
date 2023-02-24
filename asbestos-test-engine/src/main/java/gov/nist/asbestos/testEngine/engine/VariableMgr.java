package gov.nist.asbestos.testEngine.engine;

import gov.nist.asbestos.client.resolver.ResourceWrapper;
import gov.nist.asbestos.http.headers.Headers;
import gov.nist.asbestos.http.operations.HttpBase;
import gov.nist.asbestos.http.operations.HttpPost;
import gov.nist.asbestos.simapi.tk.stubs.UUIDFactory;
import gov.nist.asbestos.simapi.validation.ValE;
import gov.nist.asbestos.testEngine.engine.fixture.FixtureComponent;
import gov.nist.asbestos.testEngine.engine.fixture.FixtureMgr;
import gov.nist.asbestos.testEngine.engine.translator.AsbestosPropertyReference;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TestReport;
import org.hl7.fhir.r4.model.TestScript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VariableMgr {
    private final TestScript testScript;
    private final FixtureMgr fixtureMgr;  // variables reference fixtures so this is needed
    private ValE val;
    //private TestReport.SetupActionOperationComponent opReport;
    private Reporter reporter;
    private Map<String, String> externalVariables = new HashMap<>();  // passed by module call
    private Properties propertiesMap;
    private static final Logger logger = Logger.getLogger(VariableMgr.class.getName());

    VariableMgr(TestScript testScript, FixtureMgr fixtureMgr) {
        Objects.requireNonNull(testScript);
        Objects.requireNonNull(fixtureMgr);
        this.testScript = testScript;
        this.fixtureMgr = fixtureMgr;
    }

    boolean hasVariable(String name) {
        Objects.requireNonNull(name);
        for(TestScript.TestScriptVariableComponent comp : testScript.getVariable()) {
            if (comp.hasName() && name.equals(comp.getName()))
                return true;
        }
        return false;
    }

    public TestScript.TestScriptVariableComponent getVariable(String name) {
        Objects.requireNonNull(name);
        TestScript.TestScriptVariableComponent theVar = null;
        for(TestScript.TestScriptVariableComponent comp : testScript.getVariable()) {
            if (comp.hasName() && name.equals(comp.getName())) {
                if (theVar != null) {
                    String msg = "variable " + name + " is defined multiple times";
                    reporter.reportError(msg);
                }
                theVar = comp;
            }
        }
        return theVar;
    }

    private List<String> getVariableNames() {
        List<String> names = new ArrayList<>();
        for(TestScript.TestScriptVariableComponent comp : testScript.getVariable()) {
            if (comp.hasName()) {
                String theName = comp.getName();
                if (names.contains(theName)) {
                    String msg = "variable " + theName + " is defined multiple times";
                    reporter.reportError(msg);
                }
                names.add(comp.getName());
            }
        }
        return names;
    }

    public Map<String, String> getVariables() {
        return getVariables(true);
    }

    public Map<String, String> getVariables(boolean errorAsValue) {
        Map<String, String> vars = new HashMap<>();
        for (String name : getVariableNames()) {
            String value = eval(name, errorAsValue);
            vars.put(name, value);
        }
        return vars;
    }

    private boolean containsVariable(String reference) {
        if (reference == null)
            return false;
        return reference.contains("${");
    }

    private int variableCount(String reference) {
        if (reference == null)
            return 0;
        int count = 0;
        int pos = 0;
        while (pos != -1) {
            pos = reference.indexOf("${", pos);
            if (pos != -1) {
                count++;
                pos += 2;
            }
        }
        return count;
    }

    public String updateReference(String reference) {
        if (reference == null)
            return null;
        final int variableCount = variableCount(reference);
        for (int i=1; i<51; i++) { // 50 variables limit
            if (!containsVariable(reference))
                return reference;
            reference = updateReference1(reference, false);
            int remainingVariables = variableCount(reference); // remaining variables should be one less than before
            if (remainingVariables != (variableCount - i)) // stuck, probably an unresolved variable
                break;
        }
        Variable unresolvedVariable = getNextVariable(reference);
        if (unresolvedVariable != null) {
            reference = updateReference1(reference, true);
            unresolvedVariable = getNextVariable(reference);
            if (unresolvedVariable == null) {
                return reference;
            } else {
                reporter.reportError("ASBTS variable " + unresolvedVariable.name + " cannot be resolved.");
            }
            //throw new Error("variable " + var.name + " cannot be resolved");
        }
        return null;
    }

    public VariableMgr setPropertiesMap(Properties propertiesMap) {
        this.propertiesMap = propertiesMap;
        return this;
    }

    class Variable {
        String name;
        int from;
        int to;
        String reference;
    }

    private Variable getNextVariable(String reference) {
        if (reference == null)
            return null;
        if (!reference.contains(("${")))
            return null;
        int from = reference.indexOf("${");
        int to = reference.indexOf("}", from);
        if (to == -1) {
            reporter.reportError("reference " + reference + " has no closing }");
            throw new Error("reference " + reference + " has no closing }");
        }
        String varName = reference.substring(from+2, to);
        Variable var = new Variable();
        var.name = varName;
        var.from = from;
        var.to = to;
        var.reference = reference;
        return var;
    }

    /**
     * @param reference
     * @param convertNullToEmptySet See http://hl7.org/fhirpath/#null-and-empty
     * @return
     */
    private String updateReference1(String reference, boolean convertNullToEmptySet) {
        Objects.requireNonNull(reference);
        Objects.requireNonNull(reporter);
        if (!reference.contains(("${"))) // Check if reference follows the Variable naming-convention
            return reference;
        // Can this recurse to handle nested variables?
        Variable var  = getNextVariable(reference); //reference.substring(from+2, to);
        String update = eval(var.name, false);
        if (update == null) {
            if (convertNullToEmptySet) {
                final String emptySet = "{}";
                update = String.format("%s/* %s is an unresolved variable. Here, null is represented as an empty set. This may be expected if an element is missing. Otherwise, check if it is defined in TestScript and confirm if the resource element exists as per the sourceId. */", emptySet, var.name);
            } else
                return reference;
        }
        return reference.substring(0, var.from) + update + reference.substring(var.to+1);
    }

    private class AnonymousVariableEval {
        public static final String ANONYMOUS = "anonymous";

        boolean isAnonymousVariable(String s) {
            return s != null && s.startsWith("'");
        }

        String eval(String s, boolean errorAsValue) {
            /*
                    If the value is a string literal, then this is an anonymous variable (always coded as a string literal) in a TestScript Module component reference call.
                    Anonymous variables are only traceable in the TestReport if the Test is Passes, and shown in the Called Module: report section.
            */
            TestScript.TestScriptVariableComponent anonymousVariable = new TestScript.TestScriptVariableComponent(new StringType("asbestosAnonVar1"));
            anonymousVariable.setDefaultValue(s);
            FixtureComponent anonFixture = new FixtureComponent().setFixtureMgr(fixtureMgr).setResourceSimple(new ResourceWrapper(new Bundle()));
            anonFixture.setId(ANONYMOUS);
            String anonExpValue = null;
            try {
                anonExpValue = doVariableEval(anonFixture, anonymousVariable.getDefaultValue());
                if (anonExpValue != null) {
                    return anonExpValue;
                }
            } catch (Exception ex) {
                String error = String.format("Anonymous variable %s eval exception: %s", anonymousVariable.getName(), ex.toString());
                if (errorAsValue)
                    return error;
                reporter.reportError( error);
            }
        return null;
        }
    }

    /**
     *
     * @param variableName
     * @param errorAsValue If true, then the output of this function contains the error
     * @return
     */
    String eval(String variableName, boolean errorAsValue) {
        Objects.requireNonNull(reporter);
        TestScript.TestScriptVariableComponent testScriptLocalVariable = getVariable(variableName);

        /* If there exists a local variable defined in TestScript, external variable with the same name overrides the local variable value.
         This is useful in the case when a TestScript can be both executed independently and as a module
         */
        if (testScriptLocalVariable != null && externalVariables.containsKey(variableName)) {
            /* external variable overrides local variable of the same name */
            String value = externalVariables.get(variableName);
            return value;
        }

        if (testScriptLocalVariable == null) {
            if (externalVariables.containsKey(variableName)) {
                /* undefined local variable, external variable reference */
                return externalVariables.get(variableName);
            } else {
                /* variable name does not exist */
                AnonymousVariableEval anonymousVariableEval = new AnonymousVariableEval();
                if (anonymousVariableEval.isAnonymousVariable(variableName)) {
                    return anonymousVariableEval.eval(variableName, errorAsValue);
                } else {
                    String error = "Variable " + variableName + " is referenced but not defined. Check server log.";
                    logger.warning(error + " If action (assertion or operation) was successful but variables could not be resolved at the action reporting step, then reporting external variables configuration might be missing. See TestEngine#reportOperation to see how external variables are added to variableMgr.");
                    if (errorAsValue)
                        return error;
                    reporter.reportError(error);
                    return null;
                }
            }
        }

        /* Evaluate local variable */

        // special feature to generate unique UUIDs
        if (testScriptLocalVariable.hasSourceId() && "GENERATEUUID".equals(testScriptLocalVariable.getSourceId())) {
            String newUUID = "urn:uuid:" + UUIDFactory.getInstance().newUUID().toString();
            testScriptLocalVariable.setSourceId(null);
            testScriptLocalVariable.setDefaultValue(newUUID);
        }

        if (testScriptLocalVariable.hasDefaultValue()) {
            String value =  testScriptLocalVariable.getDefaultValue();
             /*
            replace property reference in a local variable
             */
            if (AsbestosPropertyReference.isPropertyReference(value)) {
                if (propertiesMap == null) {
                    logger.log(Level.CONFIG, "variable defaultValue Asbestos external property file referenced without an object instance.");
                } else {
                    value = AsbestosPropertyReference.getValue(propertiesMap, value);
                }
            }
            value = updateReference(value);
            return value;
        }

        String sourceId = null;
        if (testScriptLocalVariable.hasSourceId()) {
            sourceId = testScriptLocalVariable.getSourceId();
        } else if (!testScriptLocalVariable.hasDefaultValue()){
            String error = "Variable " + variableName + " does not have a source and does not define a defaultValue";
            if (errorAsValue)
                return error;
            reporter.reportError(error);
            return null;
        }


        if (!fixtureMgr.containsKey(sourceId)) {
            if (! (testScriptLocalVariable.hasExpression() && AnonymousVariableEval.ANONYMOUS.equals(sourceId))) {
                String error = "Variable " + variableName + " references source " + sourceId + " which does not exist";
                if (errorAsValue)
                    return error;
                reporter.reportError(error);
                return null;
            }
        }
        FixtureComponent fixture = fixtureMgr.get(sourceId);

        if (testScriptLocalVariable.hasHeaderField()) {
            if (!fixture.hasHttpBase()) {
                String error = "Variable " + variableName + " source " + sourceId + " does not have a HTTP header behind it";
                if (errorAsValue)
                    return error;
                reporter.reportError(error);
                return null;
            }
            HttpBase base = fixture.getHttpBase();
            if (testScriptLocalVariable.getHeaderField().equals("Location") && base instanceof HttpPost) {
                HttpPost poster = (HttpPost) base;
                return poster.getLocationHeader().getValue();
            }
            Headers responseHeaders = base.getResponseHeaders();
            if (responseHeaders == null)
                responseHeaders = new Headers();
            return responseHeaders.getValue(testScriptLocalVariable.getHeaderField());
        } else if (testScriptLocalVariable.hasExpression()) {
            String expression = testScriptLocalVariable.getExpression();
            /*
            replace property reference in a local variable
             */
            if (AsbestosPropertyReference.isPropertyReference(expression)) {
                if (propertiesMap == null) {
                    logger.log(Level.CONFIG, "variable expression Asbestos external property file referenced without an object instance.");
                } else {
                    expression = AsbestosPropertyReference.getValue(propertiesMap, expression);
                }
            }

            if (fixture == null && AnonymousVariableEval.ANONYMOUS.equals(sourceId )) {
                AnonymousVariableEval anonymousVariableEval = new AnonymousVariableEval();
                if (anonymousVariableEval.isAnonymousVariable(expression)) {
                    return anonymousVariableEval.eval(expression, errorAsValue);
                }
            }

            // Does this expression yet reference another variable?
            return doVariableEval(fixture, expression);

        } else if (testScriptLocalVariable.hasPath()) {
            String error = "Variable " + variableName + " path not supported";
            if (errorAsValue)
                return error;
            reporter.reportError(error);
            return null;
        } else {
            String error = "Variable " + variableName + " does not define one of headerField, expression, path, defaultValue";
            if (errorAsValue)
                return error;
            reporter.reportError(error);
            return null;
        }
    }

    private String doVariableEval(FixtureComponent fixture, String expression) {
        if (containsVariable(expression)) {
            expression = updateReference(expression);
        }
        return FhirPathEngineBuilder.evalForString(fixture.getResourceResource(), expression);
    }

    public VariableMgr setVal(ValE val) {
        this.val = val;
        return this;
    }

    VariableMgr setOpReport(TestReport.SetupActionOperationComponent opReport) {
        Objects.requireNonNull(opReport);
        Objects.requireNonNull(val);
        //this.opReport = opReport;
        reporter = new Reporter(val, opReport, "", "");
        return this;
    }

    VariableMgr setOpReport(TestReport.SetupActionAssertComponent asReport) {
        Objects.requireNonNull(asReport);
        Objects.requireNonNull(val);
        //this.opReport = opReport;
        reporter = new Reporter(val, asReport, "", "");
        return this;
    }


    public VariableMgr setExternalVariables(Map<String, String> variables) {
        this.externalVariables = variables;
        return this;
    }

    public boolean hasReporter() {
        return reporter != null;
    }

    public VariableMgr setReporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }
}
