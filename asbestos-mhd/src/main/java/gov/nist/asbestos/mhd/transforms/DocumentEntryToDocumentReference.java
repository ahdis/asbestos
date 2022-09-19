package gov.nist.asbestos.mhd.transforms;

import gov.nist.asbestos.client.Base.IVal;
import gov.nist.asbestos.client.client.FhirClient;
import gov.nist.asbestos.client.resolver.ResourceCacheMgr;
import gov.nist.asbestos.mhd.exceptions.MetadataAttributeTranslationException;
import gov.nist.asbestos.mhd.transactionSupport.CodeTranslator;
import gov.nist.asbestos.mhd.translation.ContainedIdAllocator;
import gov.nist.asbestos.mhd.translation.attribute.*;
import gov.nist.asbestos.mhd.translation.attribute.Slot;
import gov.nist.asbestos.client.channel.ChannelConfig;
import gov.nist.asbestos.mhd.util.Utils;
import gov.nist.asbestos.simapi.validation.Val;
import gov.nist.asbestos.simapi.validation.ValE;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.*;
import org.hl7.fhir.r4.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

// TODO author, related, binary (content.attachment), logical id, identifier (entryUUID), legalAuthenticator, sourcePatientInfo, sourcePatientId
public class DocumentEntryToDocumentReference implements IVal {
    private Val val;
    private CodeTranslator codeTranslator;
    private ResourceCacheMgr resourceCacheMgr = null;
    private ContainedIdAllocator containedIdAllocator = null;
    FhirClient fhirClient = null;
    private static Logger log = Logger.getLogger(DocumentEntryToDocumentReference.class.getName());

    public DocumentReference getDocumentReference(ExtrinsicObjectType eo, ChannelConfig channelConfig) {
        Objects.requireNonNull(eo);
        Objects.requireNonNull(fhirClient);
        Objects.requireNonNull(containedIdAllocator);
        DocumentReference dr = new DocumentReference();

        ValE vale = new ValE(val).asTranslation();
        vale.setMsg("DocumentEntry to DocumentReference");

        String objectType = eo.getObjectType();
        if (!"urn:uuid:7edca82f-054d-47f2-a032-9b2a5b5186c1".equals(objectType)) {
            val.add(new ValE("DocumentEntryToDocumentReference: this transform only handles stable DocumentEntries - objectType " + objectType + " received").asError());
            return dr;
        }

        DocumentReference.DocumentReferenceContentComponent content = new DocumentReference.DocumentReferenceContentComponent();
        Attachment attachment = new Attachment();
        dr.addContent(content);
        attachment.setContentType(eo.getMimeType());

        String uid = null;
        for (ExternalIdentifierType ei : eo.getExternalIdentifier()) {
            if (ei.getIdentificationScheme().equals("urn:uuid:2e82c1f6-a085-4c72-9da3-8640a32e42ab"))
                uid = ei.getValue();
        }

        if (uid != null) {
            attachment.setUrl(channelConfig.getFhirBase() + "/Binary/" + uid);
        }
        dr.getContent().get(0).setAttachment(attachment);
        DocumentReference.DocumentReferenceContextComponent context = new DocumentReference.DocumentReferenceContextComponent();
        dr.setContext(context);

        if (eo.getId() != null) {
            dr.setId(Utils.stripUrnPrefixes(eo.getId())); // Used by fullURL
//            String id = eo.getId();
            Identifier idr = new ExtrinsicId().setVal(vale).getIdentifier(eo.getId());
//            Identifier idr = new Identifier();
//            idr.setSystem("urn:ietf:rfc:3986");
//            idr.setValue(stripUrnPrefix(id));
//            if (ResourceMgr.isUUID(id))
//                idr.setUse(Identifier.IdentifierUse.OFFICIAL);
            dr.getIdentifier().add(idr);
        }
        for (ExternalIdentifierType ei : eo.getExternalIdentifier()) {
            String scheme = ei.getIdentificationScheme();
            if ("urn:uuid:58a6f841-87b3-4a3e-92fd-a8ffeff98427".equals(scheme)) {
                // PatientID
                PatientId patientId = new PatientId()
                        .setPatientid(ei.getValue())
                        .setFhirClient(fhirClient)
                        .setResourceCacheMgr(resourceCacheMgr);
                // TEST-1000-XXX is No_Patient - fake patient used when Minimal Metadata has not Patient reference
                if (!"TEST-1000-XXX".equals(patientId.getId())) {
                    patientId.setVal(val);
                    Optional<Reference> reference = patientId.getFhirReference();
                    if (reference.isPresent()) {
                        dr.setSubject(reference.get());
                    } else {
                        val.add(new ValE("DocumentEntryToDocumentReference: Cannot find Patient reference for pid " + ei.getValue()).asError());
                    }
                    reference.ifPresent(dr::setSubject);
                }
            } else if ("urn:uuid:2e82c1f6-a085-4c72-9da3-8640a32e42ab".equals(scheme)) {
                // Unique ID
                Identifier idr = new Identifier();
                idr.setSystem("urn:ietf:rfc:3986");
                String value = ei.getValue();
                idr.setValue(Utils.addUrnOidPrefix(value));
                dr.setMasterIdentifier(idr);
            } else {
                val.add(new ValE("DocumentEntryToDocumentReference: Do not understand ExternalIdentifier identification scheme " + scheme).asError());
            }
        }
        for (ClassificationType c : eo.getClassification()) {
            String scheme = c.getClassificationScheme();
            if ("urn:uuid:93606bcf-9494-43ec-9b4e-a7748d1a838d".equals(scheme)) {
                Author author = new Author();
                author.setContainedIdAllocator(containedIdAllocator);
                author.setVal(val);
                List<Resource> contained = author.authorClassificationToContained(c);
                // Either Practitioner or PractitionerRole or Organization
                for (Resource r : contained) {
                    dr.addContained(r);
                }
                // if Practitioner AND PractitionerRole, only add PractitionerRole as author
                Practitioner practitioner = null;
                PractitionerRole practitionerRole = null;
                Organization organization = null;
                for (Resource r : contained) {
                    if (r instanceof PractitionerRole)
                        practitionerRole = (PractitionerRole) r;
                    if (r instanceof Practitioner)
                        practitioner = (Practitioner) r;
                    if (r instanceof Organization)
                        organization = (Organization) r;
                }
                Resource resource = null;
                // practitioner and organization should not come together
                if (practitionerRole != null)
                    resource = practitionerRole;
                else if (practitioner != null)
                    resource = practitioner;
                else if (organization != null)
                    resource = organization;
                if (resource != null)
                    dr.addAuthor(new Reference().setReference(resource.getId()));
            } else {
                XdsCode xdsCode = new XdsCode()
                        .setCodeTranslator(codeTranslator)
                        .setClassificationType(c);
                xdsCode.setVal(val);
                xdsCode.setCodeTranslator(codeTranslator);

                if (CodeTranslator.FORMATCODE.equals(scheme)) {
                    content.setFormat(xdsCode.asCoding());
                } else if (CodeTranslator.CLASSCODE.equals(scheme)) {
                    dr.setCategory(Collections.singletonList(xdsCode.asCodeableConcept()));
                } else if (CodeTranslator.PRACCODE.equals(scheme)) {
                    context.setPracticeSetting(xdsCode.asCodeableConcept());
                } else if (CodeTranslator.HCFTCODE.equals(scheme)) {
                    context.setFacilityType(xdsCode.asCodeableConcept());
                } else if (CodeTranslator.EVENTCODE.equals(scheme)) {
                    context.setEvent(Collections.singletonList(xdsCode.asCodeableConcept()));
                } else if (CodeTranslator.CONFCODE.equals(scheme)) {
                    dr.setSecurityLabel(Collections.singletonList(xdsCode.asCodeableConcept()));
                } else if (CodeTranslator.TYPECODE.equals(scheme)) {
                    dr.setType(xdsCode.asCodeableConcept());
                } else {
                    val.add(new ValE("DocumentEntryToDocumentReference: Do not understand Classification scheme " + scheme).asError());
                }
            }
        }
        for (SlotType1 slot : eo.getSlot()) {
            String name = slot.getName();
            List<String> values = slot.getValueList().getValue();
            if (!values.isEmpty()) {
                String value1 = values.get(0);
                if ("hash".equals(name)) {
                    attachment.setHash(HashTranslator.toByteArray(value1));
                } else if ("size".equals(name)) {
                    attachment.setSize(Integer.parseInt(value1));
                } else if ("repositoryUniqueId".equals(name)) {

                } else if ("languageCode".equals(name)) {
                    attachment.setLanguage(value1);
                } else if ("serviceStartTime".equals(name)) {
                    if (!context.hasPeriod())
                        context.setPeriod(new Period());
                    Period period = context.getPeriod();
                    try {
                        period.setStart(DateTransform.dtmToDate(value1));
                    } catch (MetadataAttributeTranslationException e) {
                        val.add(new ValE(e.getMessage()).asError());
                    }
                } else if ("serviceStopTime".equals(name)) {
                    if (!context.hasPeriod())
                        context.setPeriod(new Period());
                    Period period = context.getPeriod();
                    try {
                        period.setEnd(DateTransform.dtmToDate(value1));
                    } catch (MetadataAttributeTranslationException e) {
                        val.add(new ValE(e.getMessage()).asError());
                    }
                } else if ("urn:ftk:DocumentReference.date".equals(name)) {
                    try {
                        dr.setDate(DateTransform.dtmToDate(value1));
                    } catch (Exception ex) {
                        log.severe("De2Dr setDate Exception: " + ex.toString());
                    }
                } else if ("creationTime".equals(name)) {
                    try {
                        /*
                        dr.setDate(DateTransform.dtmToDate(value1));
                         */
                        attachment.setCreation(DateTransform.dtmToDate(value1));
                    } catch (MetadataAttributeTranslationException e) {
                        val.add(new ValE(e.getMessage()).asError());
                    }
                } else if ("sourcePatientId".equals(name)) {
                    String[] parts = value1.split("\\^");
                    if (parts.length == 4) {
                        String id = parts[0];
                        String aa = parts[3];
                        if (aa != null || !aa.equals("")) {
                            String[] aaParts = aa.split("&");
                            if (aaParts.length == 3) {
                                String aaOid = aaParts[1];
                                Patient patient = new Patient();
                                patient.addIdentifier().setValue(id).setSystem("urn:oid:" + aaOid).setUse(Identifier.IdentifierUse.USUAL);
                                patient.setId("sourcePatientId");
                                dr.addContained(patient);
                                dr.getContext().setSourcePatientInfo(new Reference("#sourcePatientId"));
                            }
                        }
                    }
                } else if ("sourcePatientInfo".equals(name)) {

                } else if ("legalAuthenticator".equals(name)) {
                    String auth = value1;
                    AuthorPerson authorPerson = new AuthorPerson();
                    authorPerson.setValue(value1, val);
                    authorPerson.parse();
                    String lastName = authorPerson.get(2);
                    if (lastName != null && !lastName.equals("")) {
                        String firstName = authorPerson.get(3);
                        HumanName humanName = new HumanName();
                        humanName.setFamily(lastName);
                        if (firstName != null && !firstName.equals("")) {
                            humanName.addGiven(firstName);
                        }
                        Practitioner practitioner = new Practitioner();
                        practitioner.addName(humanName);
                        String id = containedIdAllocator.newId(Practitioner.class);
                        practitioner.setId(id);
                        dr.addContained(practitioner);
                        dr.setAuthenticator(new Reference().setReference(id));
                    }

                } else if ("referenceIdList".equals(name)) {

                }
            }
        }

        if (eo.getStatus() != null) {
            if (eo.getStatus().endsWith("Approved")) {
                dr.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);
            } else if (eo.getStatus().endsWith("Deprecated")) {
                dr.setStatus(Enumerations.DocumentReferenceStatus.SUPERSEDED);
            }
        }

        if (eo.getName() != null) {
            InternationalStringType ist = eo.getName();
            List<LocalizedStringType> local = ist.getLocalizedString();
            if (!local.isEmpty()) {
                LocalizedStringType lst = local.get(0);
                String value = lst.getValue();
                dr.setDescription(value);
            }
        }

        if (eo.getDescription() != null) {
            String desc = Slot.getValue(eo.getDescription());
            if (desc != null)
                attachment.setTitle(desc);
        }

        return dr;
    }

    @Override
    public void setVal(Val val) {
        this.val = val;
    }

    public DocumentEntryToDocumentReference setResourceCacheMgr(ResourceCacheMgr resourceCacheMgr) {
        this.resourceCacheMgr = resourceCacheMgr;
        return this;
    }

    public DocumentEntryToDocumentReference setCodeTranslator(CodeTranslator codeTranslator) {
        this.codeTranslator = codeTranslator;
        return this;
    }

    public DocumentEntryToDocumentReference setContainedIdAllocator(ContainedIdAllocator containedIdAllocator) {
        this.containedIdAllocator = containedIdAllocator;
        return this;
    }

    public DocumentEntryToDocumentReference setFhirClient(FhirClient fhirClient) {
        this.fhirClient = fhirClient;
        return  this;
    }
}
