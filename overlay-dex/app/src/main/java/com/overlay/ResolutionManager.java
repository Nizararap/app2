package com.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ResolutionManager {

    private static int originalDensityDpi = -1;

    public static void applyScale(Context context, float scale) {
        try {
            Context appCtx = context.getApplicationContext();
            Resources res = appCtx.getResources();
            DisplayMetrics dm = res.getDisplayMetrics();

            if (originalDensityDpi == -1) {
                originalDensityDpi = dm.densityDpi;
            }

            int targetDpi = (int)(originalDensityDpi * scale);

            // Apply to app resources
            Configuration config = new Configuration(res.getConfiguration());
            config.densityDpi = targetDpi;
            res.updateConfiguration(config, dm);

            // Also apply to all active Activities via ActivityThread
            try {
                Class<?> atClass = Class.forName("android.app.ActivityThread");
                Method current = atClass.getDeclaredMethod("currentActivityThread");
                current.setAccessible(true);
                Object at = current.invoke(null);

                Field mActivities = atClass.getDeclaredField("mActivities");
                mActivities.setAccessible(true);
                Object actMap = mActivities.get(at);

                // actMap is ArrayMap - get values
                Method values = actMap.getClass().getDeclaredMethod("values");
                values.setAccessible(true);
                Object[] records = ((ArrayList<?>) values.invoke(actMap)).toArray();

                for (Object record : records) {
                    Field activityField = record.getClass().getDeclaredField("activity");
                    activityField.setAccessible(true);
                    Activity activity = (Activity) activityField.get(record);
                    if (activity != null) {
                        Resources actRes = activity.getResources();
                        actRes.updateConfiguration(config, actRes.getDisplayMetrics());
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}
    }

    public static void reset(Context context) {
        if (originalDensityDpi != -1) {
            applyScale(context, 1.0f);
            originalDensityDpi = -1;
        }
    }
}
