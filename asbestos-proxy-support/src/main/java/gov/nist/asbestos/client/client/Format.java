package gov.nist.asbestos.client.client;

import gov.nist.asbestos.http.headers.Header;
import gov.nist.asbestos.http.headers.Headers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public enum Format {
    JSON,
    XML,
    NONE;

    private static String fhirVersionHeader = "fhirVersion=4.0";

    public String getContentType() {
        return (this.name().equals("JSON") ? "application/fhir+json" : "application/fhir+xml") + "; " + fhirVersionHeader;
    }

    private static List<String> formats = new ArrayList<>();

    static {
        formats.add("application/fhir+json");
        formats.add("application/fhir+xml");
    }

    public static boolean isFormat(String format) {
        return format != null && formats.contains(format);
    }

    public static Format fromContentType(String contentType) {
        if (contentType != null && contentType.contains("json"))
            return Format.JSON;
        if (contentType != null && contentType.contains("xml"))
            return Format.XML;
        return null;
    }

    public static Format resultContentType(Headers inHeaders) {
        Header acceptHeader = inHeaders.getAccept();
        Format format = Format.XML;
        if (acceptHeader != null && acceptHeader.getValue().contains("json"))
            format = Format.JSON;
        return format;
    }

    public static String fileExtensionFromContent(String content) {
        return content.trim().startsWith("<") ? "xml" : "json";
    }

    public static Format fromContent(String content) {
        return content.trim().startsWith("<") ? XML : JSON;
    }

    public static List<String> getFormats() {
        return formats;
    }

    public static String quickEscapeXml(String in) {
        Objects.requireNonNull(in);
        return in.replaceAll("<","&lt;");
    }
}
