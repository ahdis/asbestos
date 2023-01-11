package gov.nist.asbestos.proxyWar;

import gov.nist.asbestos.client.channel.FtkChannelTypeEnum;
import gov.nist.asbestos.client.resolver.Ref;
import gov.nist.asbestos.http.operations.HttpDelete;
import gov.nist.asbestos.http.operations.HttpPost;
import gov.nist.asbestos.client.channel.ChannelConfig;
import gov.nist.asbestos.client.channel.ChannelConfigFactory;


import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.fail;


public class ChannelsForTests {
    public static ChannelConfig create(String testSession, String channelId, URI fhirBase) throws URISyntaxException, IOException {
        ChannelConfig channelConfig = new ChannelConfig()
                .setTestSession(testSession)
                .setChannelName(channelId)
                .setEnvironment("default")
                .setActorType("fhir")
                .setChannelType(FtkChannelTypeEnum.fhir)
                .setFhirBase(fhirBase.toString());
        String json = ChannelConfigFactory.convert(channelConfig);
        HttpPost poster = new HttpPost();
        poster.postJson(new URI("http://localhost:" + ITConfig.getProxyPort() + "/asbestos/rw/channel/create"), json);
        int status = poster.getStatus();
        if (!(status == 200 || status == 201))
            fail("200 or 201 required from CreateChannel - returned " + status);
        return channelConfig;
    }

    public static Ref gen500() throws IOException, URISyntaxException {
        String base = "http://localhost:" + ITConfig.getProxyPort()  + "/asbestos/gen500";
        new HttpDelete().run(String.format("http://localhost:%s/asbestos/rw/channel/%s__%s", ITConfig.getProxyPort(), "default", "g500"));
        URI fhirBase500 = new URI(base);
        ChannelConfig channelConfig = ChannelsForTests.create("default", "g500", fhirBase500);
        URI channelBase = getChannelBase(channelConfig);
        return new Ref(channelBase);
    }

    private static URI getChannelBase(ChannelConfig channelConfig) {
        String fhirToolkitBase = "http://localhost:" +  ITConfig.getProxyPort() + "/asbestos";
        //ServiceProperties.getInstance().getPropertyOrStop(ServicePropertiesEnum.FHIR_TOOLKIT_BASE);
        try {
            return new URI(fhirToolkitBase + "/proxy/" + channelConfig.asFullId());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
