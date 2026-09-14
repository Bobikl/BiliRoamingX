package app.revanced.bilibili.xposed;

import android.view.View;
import android.widget.TextView;

final class PlayerTrialHook extends PlayerHookSupport {
    PlayerTrialHook(ModuleEntry entry, HostRuntime runtime) { super(entry, runtime); }
    void install() {
        for (String name : new String[]{"com.bilibili.playerbizcommonv2.widget.quality.m", "com.mall.videodetail.vd.united.page.videoquality.w"}) {
            installPart("trialBadge." + name, () -> {
                int count = 0;
                for (var method : host(name).getDeclaredMethods()) {
                    Class<?>[] types = method.getParameterTypes();
                    if ((types.length != 5 && types.length != 6) || types[1] != boolean.class || types[3] != TextView.class || types[4] != TextView.class) continue;
                    count++;
                    after(method, "trialBadge", (chain, result) -> {
                        if (!enabled("trial_vip_quality") || !PlayerAccount.knownNonVip(runtime)) return result;
                        TextView stroke = (TextView) chain.getArg(3), solid = (TextView) chain.getArg(4);
                        if (stroke == null || solid == null || !solid.getText().toString().equals("限免中")) return result;
                        solid.setVisibility(View.GONE); stroke.setText((boolean) chain.getArg(1) ? "试看中" : "可试看"); stroke.setVisibility(View.VISIBLE);
                        float density = stroke.getResources().getDisplayMetrics().density;
                        stroke.setPadding(Math.round(4 * density), Math.round(density), Math.round(4 * density), Math.round(2 * density));
                        return result;
                    });
                }
                if (count == 0) throw new NoSuchMethodException("Trial badge binding method");
            });
        }
    }
}
