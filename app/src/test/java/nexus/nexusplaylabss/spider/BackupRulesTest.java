package com.nexusplaylabss.spider;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Backup keeps only the game's own state. A wider rule would also restore the ads consent,
 * the cached Remove Ads purchase, reminder scheduling or Firebase IDs on another phone.
 */
public class BackupRulesTest {
    private static final List<String> ONLY_GAME_STATE = List.of("include sharedpref freecell_web_state.xml");

    private static List<String> rules(String file, String section) throws Exception {
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/xml/" + file)).getDocumentElement();
        Element parent = section == null ? root : (Element) root.getElementsByTagName(section).item(0);
        List<String> found = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element)) continue;
            Element rule = (Element) children.item(i);
            found.add(rule.getTagName() + " " + rule.getAttribute("domain") + " " + rule.getAttribute("path"));
        }
        return found;
    }

    @Test public void androidElevenAndOlderBackUpOnlyTheGameState() throws Exception {
        assertEquals(ONLY_GAME_STATE, rules("backup_rules.xml", null));
    }

    @Test public void cloudBackupAndDeviceTransferKeepOnlyTheGameState() throws Exception {
        assertEquals(ONLY_GAME_STATE, rules("data_extraction_rules.xml", "cloud-backup"));
        assertEquals(ONLY_GAME_STATE, rules("data_extraction_rules.xml", "device-transfer"));
    }
}
