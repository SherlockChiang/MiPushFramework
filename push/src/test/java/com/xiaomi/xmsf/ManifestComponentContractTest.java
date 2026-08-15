package com.xiaomi.xmsf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

public class ManifestComponentContractTest {
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String XMSF_PACKAGE = "com.xiaomi.xmsf";
    private static final String PUSH_SERVICE = "com.xiaomi.push.service.XMPushService";
    private static final String PUSH_RECEIVER =
            "com.xiaomi.xmsf.push.service.receivers.MiuiPushMessageReceiver";
    private static final String PUSH_MESSAGE_HANDLER =
            "com.xiaomi.mipush.sdk.PushMessageHandler";

    @Test
    public void sourceManifestsDeclareProductionAndQaBoundaries() throws Exception {
        Path pushDirectory = findPushDirectory();
        Document main = parse(pushDirectory.resolve("src/main/AndroidManifest.xml"));
        Document qa = parse(pushDirectory.resolve("src/qa/AndroidManifest.xml"));

        assertAttribute(main, "service", PUSH_SERVICE, "exported", "true");
        assertAttribute(main, "receiver", PUSH_RECEIVER, "exported", "true");
        assertAttribute(main, "service", PUSH_MESSAGE_HANDLER, "enabled", "true");
        assertAttribute(main, "service", PUSH_MESSAGE_HANDLER, "exported", "true");

        assertAttribute(qa, "receiver", PUSH_RECEIVER, "enabled", "false");
        assertAttribute(qa, "receiver", PUSH_RECEIVER, "exported", "false");
        assertAttribute(qa, "service", PUSH_SERVICE, "exported", "false");
        assertAttribute(qa, "service", PUSH_MESSAGE_HANDLER, "enabled", "false");
        assertAttribute(qa, "service", PUSH_MESSAGE_HANDLER, "exported", "false");
    }

    @Test
    public void mergedManifestPreservesCurrentVariantContract() throws Exception {
        Document merged = parse(findMergedManifest());

        if (BuildConfig.QA_BUILD) {
            assertAttribute(merged, "receiver", PUSH_RECEIVER, "enabled", "false");
            assertAttribute(merged, "receiver", PUSH_RECEIVER, "exported", "false");
            assertAttribute(merged, "service", PUSH_SERVICE, "exported", "false");
            assertAttribute(merged, "service", PUSH_MESSAGE_HANDLER, "enabled", "false");
            assertAttribute(merged, "service", PUSH_MESSAGE_HANDLER, "exported", "false");
        } else {
            assertAttribute(merged, "service", PUSH_SERVICE, "exported", "true");
            assertAttribute(merged, "receiver", PUSH_RECEIVER, "exported", "true");
            assertAttribute(merged, "service", PUSH_MESSAGE_HANDLER, "enabled", "true");
            assertAttribute(merged, "service", PUSH_MESSAGE_HANDLER, "exported", "true");
        }
    }

    private static Path findPushDirectory() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 6; depth++, current = current.getParent()) {
            Path direct = current.resolve("src/main/AndroidManifest.xml");
            if (Files.isRegularFile(direct)) {
                return current;
            }
            Path nested = current.resolve("push/src/main/AndroidManifest.xml");
            if (Files.isRegularFile(nested)) {
                return current.resolve("push");
            }
        }
        throw new AssertionError("Unable to locate push module from " + System.getProperty("user.dir"));
    }

    private static Path findMergedManifest() {
        Path pushDirectory = findPushDirectory();
        String buildType = BuildConfig.BUILD_TYPE;
        String variant = BuildConfig.FLAVOR + Character.toUpperCase(buildType.charAt(0))
                + buildType.substring(1);
        String[] outputDirectories = {
                "merged_manifest",
                "merged_manifests",
                "packaged_manifests"
        };
        for (String outputDirectory : outputDirectories) {
            Path candidate = pushDirectory.resolve("build/intermediates")
                    .resolve(outputDirectory)
                    .resolve(variant)
                    .resolve("AndroidManifest.xml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Unable to locate merged manifest for variant " + variant);
    }

    private static Document parse(Path manifest) throws Exception {
        assertTrue("Manifest must exist: " + manifest, Files.isRegularFile(manifest));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(manifest.toFile());
    }

    private static void assertAttribute(
            Document document,
            String componentType,
            String componentName,
            String attribute,
            String expectedValue) {
        Element component = findComponent(document, componentType, componentName);
        assertNotNull(componentType + " must be declared: " + componentName, component);
        assertEquals(
                componentType + " " + componentName + " android:" + attribute,
                expectedValue,
                component.getAttributeNS(ANDROID_NAMESPACE, attribute));
    }

    private static Element findComponent(
            Document document, String componentType, String componentName) {
        NodeList components = document.getElementsByTagName(componentType);
        for (int index = 0; index < components.getLength(); index++) {
            Element component = (Element) components.item(index);
            String declaredName = component.getAttributeNS(ANDROID_NAMESPACE, "name");
            if (normalizeComponentName(declaredName).equals(componentName)) {
                return component;
            }
        }
        return null;
    }

    private static String normalizeComponentName(String componentName) {
        if (componentName.startsWith(".")) {
            return XMSF_PACKAGE + componentName;
        }
        return componentName;
    }
}
