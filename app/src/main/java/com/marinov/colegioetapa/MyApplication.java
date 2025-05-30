package com.marinov.colegioetapa;

import android.app.Application;
import android.os.Build;
import com.google.android.material.color.DynamicColors;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // só aplica em Android 12+; em versões antigas fica no seu tema estático
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
    }
}
