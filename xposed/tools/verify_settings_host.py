"""Verify reflection targets in the untouched host DEX; does not execute the APK."""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('apk', type=Path)
p.add_argument('--sdk', type=Path, required=True)
p.add_argument('--report', type=Path, required=True)
a = p.parse_args()
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')
targets = {
    'com.bilibili.app.preferences.BiliPreferencesActivity$BiliPreferencesFragment': [
        'onCreatePreferences(Landroid/os/Bundle;Ljava/lang/String;)V'],
    'androidx.preference.Preference': [
        '<init>(Landroid/content/Context;)V', 'setKey(Ljava/lang/String;)V',
        'setTitle(Ljava/lang/CharSequence;)V', 'setSummary(Ljava/lang/CharSequence;)V',
        'setOrder(I)V', 'setPersistent(Z)V', 'setIconSpaceReserved(Z)V',
        'setOnPreferenceClickListener(Landroidx/preference/Preference$d;)V'],
    'androidx.preference.Preference$d': ['(Landroidx/preference/Preference;)Z'],
    'androidx.preference.PreferenceGroup': ['findPreference(Ljava/lang/CharSequence;)Landroidx/preference/Preference;',
                                           'addPreference(Landroidx/preference/Preference;)Z'],
    'androidx.preference.PreferenceFragmentCompat': ['getPreferenceScreen()Landroidx/preference/PreferenceScreen;'],
    'androidx.fragment.app.Fragment': ['getActivity()Landroidx/fragment/app/FragmentActivity;', 'getContext()Landroid/content/Context;'],
}
results = {}
for cls, methods in targets.items():
    command = ['java', '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8',
               '-Dcom.android.sdklib.toolsdir=' + str(a.sdk / 'cmdline-tools/latest'),
               '-classpath', str(a.sdk / 'cmdline-tools/latest/lib/apkanalyzer-classpath.jar'),
               'com.android.tools.apk.analyzer.ApkAnalyzerCli', 'dex', 'code', '--class', cls, str(a.apk)]
    run = subprocess.run(command, check=True, capture_output=True, encoding='utf-8')
    declarations = [line for line in run.stdout.splitlines() if line.startswith('.method public ')]
    for method in methods:
        assert any(line.endswith(method) for line in declarations), (cls, method)
    results[cls] = methods
report = {'host_sha256': hashlib.sha256(a.apk.read_bytes()).hexdigest(), 'checks': 'passed',
          'public_dex_methods': results, 'device_execution': False}
text = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
a.report.write_text(text, encoding='utf-8')
assert a.report.read_text(encoding='utf-8') == text
print('Verified', sum(map(len, targets.values())), 'public DEX methods in', len(targets), 'classes')
