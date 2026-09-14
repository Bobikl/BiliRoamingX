package app.revanced.bilibili.xposed;

import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;

/** Read only the local VIP state needed to avoid changing paid users' playback. */
final class PlayerAccount {
    static boolean knownNonVip(HostRuntime runtime) {
        try {
            Class<?> type = Class.forName("com.bilibili.lib.accounts.BiliAccounts", false, runtime.hostLoader);
            Object account = type.getDeclaredMethod("get", Context.class).invoke(null, runtime.hostContext);
            long mid = (long) PlayerReflection.call(account, "mid");
            if (mid == 0) return true;
            String key = "info" + mid, info = null;
            try (var cursor = runtime.hostContext.getContentResolver().query(
                    Uri.parse("content://" + ModuleConstants.HOST + ".provider.auth"), null, null, new String[]{key}, null)) {
                if (cursor != null && cursor.moveToFirst()) info = cursor.getString(0);
            } catch (RuntimeException ignored) { }
            if (info == null || info.isEmpty()) info = runtime.hostContext.getSharedPreferences("bili.passport.auth", Context.MODE_PRIVATE).getString(key, "");
            if (info == null || info.isEmpty()) return false;
            JSONObject vip = new JSONObject(info).optJSONObject("vip");
            if (vip == null) return false;
            int vipType = vip.optInt("type");
            return !((vipType == 1 || vipType == 2) && vip.optInt("status") == 1);
        } catch (Exception ignored) { return false; }
    }
    private PlayerAccount() { }
}
