package gov.nist.asbestos.http.operations;

import gov.nist.asbestos.http.headers.Header;
import gov.nist.asbestos.http.util.Gzip;
import org.apache.commons.io.IOUtils;
import java.util.logging.Level;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class HttpMethod extends HttpBase {
    private static Logger logger = Logger.getLogger(HttpMethod.class.getName());
    private Header locationHeader = null;
    protected String theHttpVerb;

    public HttpMethod(String theHttpVerb) {
        this.theHttpVerb = theHttpVerb;
    }

    // content is unzipped
    protected void doVerb(URI uri, Map<String, String> headers, byte[] content) throws IOException {
        //String stringContent = new String(content);
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            if (_requestHeaders != null)
                addHeaders(connection, _requestHeaders);
            if (headers != null)
                addHeaders(connection, headers);
            requestHeadersList = connection.getRequestProperties();
            connection.setRequestMethod(theHttpVerb);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            // TODO use proper charset (from input)
            if (content != null) {
                if (isRequestGzipEncoded())
                    connection.getOutputStream().write(Gzip.compressGZIP(content));
                else
                    connection.getOutputStream().write(content);
            }
            status = connection.getResponseCode();
            try {
                setResponseHeadersList(connection.getHeaderFields());
                InputStream is;
                if (status >= 400)
                    is = connection.getErrorStream();
                else
                    is = connection.getInputStream();
                byte[] bytes = IOUtils.toByteArray(is);
                if (isResponseGzipEncoded())
                    setResponse(Gzip.decompressGZIP(bytes));
                else
                    setResponse(bytes);
            } catch (Throwable t) {
                // ok - won't always be available
            }
        } finally {
            if (connection != null)
                connection.disconnect();
        }
        if (getResponseHeaders() != null)
            locationHeader = getResponseHeaders().get("Location");
    }

    protected void submit() {
        try {
            doVerb(uri, getRequestHeaders().getAll(), getRequest());
        } catch (IOException e) {
            status = 400;
            String msg = uri + "\n" + e.getMessage() + "\n" + "HttpMethod#submit Error: Check log for details.";
            logger.log(Level.SEVERE, msg, e);
            setResponseText(msg);
            setResponse(msg.getBytes());
        }
    }

    protected void submit(URI uri, String json) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        doVerb(uri, headers, json == null ? null : json.getBytes());
    }

    public HttpMethod run() throws IOException {
        Objects.requireNonNull(uri);
        doVerb(uri, getRequestHeaders().getAll(),  getRequest());
        return this;
    }

    public String getVerb() {
        return theHttpVerb;
    }

    public Header getLocationHeader() {
        return locationHeader;
    }

    public HttpMethod setLocation(String location) {
        this.locationHeader = new Header("Location", location);
        return this;
    }
}
