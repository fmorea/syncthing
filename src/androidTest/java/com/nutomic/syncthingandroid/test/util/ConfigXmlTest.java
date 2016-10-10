package com.nutomic.syncthingandroid.test.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.io.File;
import java.io.IOException;

public class ConfigXmlTest extends AndroidTestCase {

    private MockContext mContext;

    private ConfigXml mConfig;

    @Before
    public void setUp() throws Exception {
        mContext = new MockContext(InstrumentationRegistry.getTargetContext());
        Assert.assertFalse(ConfigXml.getConfigFile(mContext).exists());
        mConfig = new ConfigXml(mContext);
        Assert.assertTrue(ConfigXml.getConfigFile(mContext).exists());
    }

    @After
    public void tearDown() throws Exception {
        ConfigXml.getConfigFile(mContext).delete();
    }

    @Test
    public void testGetWebGuiUrl() {
        assertTrue(mConfig.getWebGuiUrl().startsWith("https://127.0.0.1:"));
    }

}
