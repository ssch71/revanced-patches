/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed;


import dev.ssch71.ample.xposed.core.AppHooks;
import dev.ssch71.ample.xposed.core.HookContext;
import dev.ssch71.ample.xposed.dcinside.DcInsideHooks;
import dev.ssch71.ample.xposed.kakaotalk.KakaoTalkHooks;
import io.github.libxposed.api.XposedModule;

public class ModuleMain extends XposedModule {
    private static final AppHooks[] APPS = {
            new KakaoTalkHooks(),
            new DcInsideHooks(),
    };

    @Override
    public void onPackageReady(PackageReadyParam param) {
        // 프로세스당 한 번만 (보조 패키지 로드 시에는 건너뜀)
        if (!param.isFirstPackage()) return;

        for (AppHooks app : APPS) {
            if (!app.packageName().equals(param.getPackageName())) continue;

            HookContext ctx = new HookContext(this, param.getClassLoader(), param.getPackageName());
            long start = System.currentTimeMillis();
            try {
                app.install(ctx);
                ctx.info(app.packageName() + ": 후크 설치 완료 (" + (System.currentTimeMillis() - start) + "ms)");
            } catch (Throwable t) {
                ctx.error(app.packageName() + ": 후크 설치 중 오류", t);
            } finally {
                ctx.finish();
            }
            return;
        }
    }
}
