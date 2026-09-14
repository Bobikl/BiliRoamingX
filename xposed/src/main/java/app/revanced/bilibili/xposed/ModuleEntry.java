package app.revanced.bilibili.xposed;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Log;

import java.security.MessageDigest;
import java.util.Locale;

import io.github.libxposed.api.XposedModule;

/** Modern API 102 entry; no legacy Xposed API and no host DEX modification. */
public final class ModuleEntry extends XposedModule {
    private boolean mainProcess;
    private boolean initialized;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        mainProcess = ModuleConstants.HOST.equals(param.getProcessName());
        if (mainProcess) log(Log.INFO, ModuleConstants.TAG, "Module entry loaded; API 102; target 8.27.0/8270400");
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!mainProcess || !ModuleConstants.HOST.equals(param.getPackageName())) return;
        try {
            var attach = Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                // Never retry the original method after a host exception.
                Object result = chain.proceed();
                try {
                    initialize((Application) chain.getThisObject(), param.getClassLoader());
                } catch (Throwable error) {
                    failure("Application.attach", "android.app.Application", "attach(Context)", error);
                }
                return result;
            });
        } catch (Throwable error) {
            failure("Application.attach", "android.app.Application", "attach(Context)", error);
        }
    }

    private synchronized void initialize(Application hostApplication, ClassLoader hostLoader) throws Exception {
        if (initialized || !ModuleConstants.HOST.equals(hostApplication.getPackageName())) return;
        initialized = true;
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo info = hostApplication.getPackageManager().getPackageInfo(ModuleConstants.HOST, flags);
        long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        if (!ModuleConstants.HOST_VERSION.equals(info.versionName) || versionCode != ModuleConstants.HOST_CODE) {
            log(Log.ERROR, ModuleConstants.TAG, "BottomBar skipped: unsupported host " + info.versionName + "/" + versionCode
                    + "; supported host is 8.27.0/8270400");
            return;
        }
        var preferences = getRemotePreferences(ModuleConstants.GROUP);
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
                ? info.signingInfo.getApkContentsSigners() : info.signatures;
        if (signatures == null || signatures.length != 1) throw new IllegalStateException("Unexpected host signer count");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
        StringBuilder fingerprint = new StringBuilder();
        for (byte part : digest) fingerprint.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        if (!ModuleConstants.HOST_CERT_SHA256.contentEquals(fingerprint)) {
            log(Log.ERROR, ModuleConstants.TAG, "BottomBar skipped: host signer does not match official 8.27.0; fingerprint=" + fingerprint);
            return;
        }
        HostRuntime runtime = new HostRuntime(hostApplication, hostLoader, preferences, this);
        runtime.registerLifecycle();
        new BottomBarHook(this, runtime).install();
        new SettingsEntranceHook(this, runtime).install();
    }

    void failure(String hook, String target, String method, Throwable error) {
        log(Log.ERROR, ModuleConstants.TAG, "Hook=" + hook + "; host=8.27.0/8270400; class=" + target
                + "; method=" + method + "; cause=" + error, error);
    }
}
