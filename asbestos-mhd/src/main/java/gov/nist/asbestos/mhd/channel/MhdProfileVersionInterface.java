package gov.nist.asbestos.mhd.channel;

import gov.nist.asbestos.client.channel.ChannelConfig;
import gov.nist.asbestos.client.resolver.IdBuilder;
import gov.nist.asbestos.client.resolver.ResourceMgr;
import gov.nist.asbestos.client.resolver.ResourceWrapper;
import gov.nist.asbestos.mhd.transactionSupport.AssigningAuthorities;
import gov.nist.asbestos.mhd.transactionSupport.CodeTranslator;
import gov.nist.asbestos.mhd.transforms.MhdTransforms;
import gov.nist.asbestos.simapi.validation.ValE;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;

import java.util.List;

public interface MhdProfileVersionInterface {
    MhdVersionEnum getMhdVersionEnum();
    String getComprehensiveMetadataCanonicalUri();
    String getMinimalMetadataCanonicalUri();
    Boolean isMinimalMetadata() throws Exception;
    boolean isBundleProfileDetected(Bundle bundle);
    void evalBundleProfile(Bundle bundle);
    List<Class<?>> getAcceptableResourceTypes();
    RegistryPackageType buildSubmissionSet(ResourceWrapper wrapper, ValE vale, IdBuilder idBuilder, ChannelConfig channelConfig, CodeTranslator codeTranslator, AssigningAuthorities assigningAuthorities);
    MhdTransforms getMhdTransforms();
    String getExtrinsicId(ValE valE, ResourceMgr rMgr, List<Identifier> identifiers);
}
