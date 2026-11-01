import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN = ROOT / 'app_pojavlauncher/src/main'


class AnalyticsConfigurationTest(unittest.TestCase):
    def test_no_collection_switch(self):
        tree = ET.parse(MAIN / 'res/xml/pref_main.xml')
        key = '{http://schemas.android.com/apk/res/android}key'
        self.assertFalse(any(e.get(key) == 'battly_analytics_enabled' for e in tree.iter()))

    def test_collection_does_not_read_legacy_opt_out(self):
        source = (MAIN / 'java/net/kdt/pojavlaunch/analytics/Telemetry.java').read_text()
        self.assertNotIn('getBoolean(PREF_ANALYTICS', source)
        self.assertIn('sEnabled = !BattlyOfflineMode.isOffline(context);', source)
        self.assertIn('FirebaseProcessGuard.ensureInitialized(appContext)', source)
        self.assertIn('setAnalyticsCollectionEnabled(sEnabled)', source)

    def test_firebase_stays_disabled_until_launcher_initialization(self):
        tree = ET.parse(MAIN / 'AndroidManifest.xml')
        prefix = '{http://schemas.android.com/apk/res/android}'
        item = next(e for e in tree.iter('meta-data') if e.get(prefix + 'name') == 'firebase_analytics_collection_enabled')
        self.assertEqual('false', item.get(prefix + 'value'))


if __name__ == '__main__':
    unittest.main()
