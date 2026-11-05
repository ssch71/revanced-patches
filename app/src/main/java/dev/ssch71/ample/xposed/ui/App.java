/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed.ui;

import android.app.Application;

import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Xposed 서비스(LSPosed)와의 연결을 유지하고 설정 화면에 알려 준다. */
public class App extends Application implements XposedServiceHelper.OnServiceListener {
    public interface ServiceStateListener {
        void onServiceStateChanged(XposedService service);
    }

    private static volatile XposedService service;
    private static final CopyOnWriteArraySet<ServiceStateListener> LISTENERS = new CopyOnWriteArraySet<>();

    public static XposedService getService() {
        return service;
    }

    public static void addListener(ServiceStateListener listener) {
        LISTENERS.add(listener);
        listener.onServiceStateChanged(service);
    }

    public static void removeListener(ServiceStateListener listener) {
        LISTENERS.remove(listener);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService s) {
        service = s;
        dispatch(s);
    }

    @Override
    public void onServiceDied(XposedService s) {
        service = null;
        dispatch(null);
    }

    private static void dispatch(XposedService s) {
        for (ServiceStateListener listener : LISTENERS) {
            listener.onServiceStateChanged(s);
        }
    }
}
