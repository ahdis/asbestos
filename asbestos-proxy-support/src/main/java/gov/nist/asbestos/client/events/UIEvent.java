package gov.nist.asbestos.client.events;

import gov.nist.asbestos.client.Base.EC;
import gov.nist.asbestos.client.Base.ParserBase;
import gov.nist.asbestos.client.client.Format;
import gov.nist.asbestos.client.resolver.ResourceWrapper;
import gov.nist.asbestos.http.headers.Header;
import gov.nist.asbestos.http.headers.Headers;
import gov.nist.asbestos.serviceproperties.ServiceProperties;
import gov.nist.asbestos.serviceproperties.ServicePropertiesEnum;
import org.hl7.fhir.r4.model.BaseResource;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class UIEvent {
    private String hostPort;
    private String testSession;
    private String channelId;
    private String eventName;
    private String resourceType;
    private List<UITask> tasks = new ArrayList<>();
    private EC ec;
    private static final Logger logger = Logger.getLogger(UIEvent.class.getName());

    public UIEvent(EC ec) {
        this.ec = ec;
        hostPort = defaultHostPort();
        int i = 1;
    }

    private String defaultHostPort() {
        String value = ServiceProperties.getInstance().getPropertyOrThrow(ServicePropertiesEnum.FHIR_TOOLKIT_BASE);
        int index = value.indexOf("/asbestos");
        value = value.substring(0, index);
        String schemeAuthSeparator = "://";
        value = value.substring(value.indexOf(schemeAuthSeparator)+schemeAuthSeparator.length());
        return value;
    }

    public UIEvent fromEventDir(File eventDir) {
        List<String> parts = Reader.dirListingAsStringList(eventDir);
        int i = 0;
        for (String part : parts) {
            UITask uiTask = new UITask(eventDir, part);
            uiTask.setLabel(part);
            uiTask.setIndex(i++);
            tasks.add(uiTask);
        }
        return this;
    }

    public UIEvent fromResource(ResourceWrapper wrapper) {
        Objects.requireNonNull(wrapper);

        UIEvent eventFromWrapper = fromURI(wrapper.getRef().getUri());
        hostPort = "";
        testSession = eventFromWrapper.getTestSession();
        channelId = eventFromWrapper.getChannelId();
        eventName = "";
        resourceType = wrapper.getResourceType();
        UITask task = new UITask(wrapper);
        String requestHeader = task.getRequestHeader();
        Headers headers = new Headers(requestHeader);
        Header header = headers.get("x-proxy-event");
        if (header != null) {
            String value = header.getValue();
            if (value != null) {
                String[] parts = value.split("/");
                if (parts.length > 2) {
                    String id = parts[parts.length - 1];
                    eventName = id;
                }
            }
        }
        tasks.add(task);

        return this;
    }

    public UIEvent fromParms(String testSession, String channelId, String resourceType, String eventName) {
        File fhir = ec.fhirDir(testSession, channelId);
        if (resourceType.equals("null")) {
            resourceType = ec.resourceTypeForEvent(fhir, eventName);
            if (resourceType == null) {
                return null;
            }
        }
        File resourceTypeFile = new File(fhir, resourceType);
        File eventDir = new File(resourceTypeFile, eventName);

        UIEvent uiEvent = fromEventDir(eventDir);
        uiEvent.eventName = eventName;
        uiEvent.resourceType = resourceType;
        uiEvent.testSession = testSession;
        uiEvent.channelId = channelId;
        return uiEvent;
    }

    public UIEvent fromURI(URI uri) {
        try {
            List<String> parts = Arrays.asList(uri.toString().split("/"));
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                if ("asbestos".equals(part) && "log".equals(parts.get(i + 1))) {
                    String testSession = parts.get(i + 2);
                    String channelId = parts.get(i + 3);
                    String resourceType = parts.get(i + 4);
                    String eventName = parts.get(i + 5);
                    return fromParms(testSession, channelId, resourceType, eventName);
                }
                if ("asbestos".equals(part) && "proxy".equals(parts.get(i + 1))) {
                    String testSessionChannelId = parts.get(i + 2);
                    String[] tsParts = testSessionChannelId.split("__");
                    String testSession = tsParts[0];
                    String channelId = tsParts[1];
                    String resourceType = parts.get(i + 3);
                    String eventName = parts.get(i + 4);
                    return fromParms(testSession, channelId, resourceType, eventName);
                }
            }
        } catch (Exception ex) {
            logger.fine("UIEvent.fromURI" + ex.toString());
        }
        return null;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public URI getURI() {
        try {
            // Use the fhirToolkitBase because the hostPort property can have different https port supplied through the HTTPS channel POST header host value,
            // which is not necessarily what is needed for server-to-server internal API calls
            return new URI(
            ServiceProperties.getInstance().getPropertyOrThrow(ServicePropertiesEnum.FHIR_TOOLKIT_BASE) +
            "/log/" +
            testSession + "/" +
            channelId + "/" +
            resourceType + "/" +
            eventName);
        } catch (URISyntaxException e) {
            throw new Error(e);
        }

    }

    public BaseResource getResponseResource() {
        String responseBody = getResponseBody();
        return ParserBase.parse(responseBody, Format.fromContent(responseBody));
    }

    public void setHostPort(String hostPort) {
        this.hostPort = hostPort;
    }

    public void setTestSession(String testSession) {
        this.testSession = testSession;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public UITask getClientTask() {
        return tasks.get(0);
    }

    public UITask getTask(int i) {
        return tasks.get(i);
    }

    public int getTaskCount() { return tasks.size(); }

    public Headers getRequestHeader() {
        return new Headers(getClientTask().getRequestHeader());
    }

    public Headers getResponseHeader() {
        return new Headers(getClientTask().getResponseHeader());
    }

    public String getRequestBody() {
        return getClientTask().getRequestBody();
    }

    public String getResponseBody() {
        return getClientTask().getResponseBody();
    }

    public String getTestSession() {
        return testSession;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getEventName() {
        return eventName;
    }

    public String getResourceType() {
        return resourceType;
    }
}
