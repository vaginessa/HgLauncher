package mono.hg.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import mono.hg.R;
import mono.hg.helpers.LauncherIconHelper;
import mono.hg.helpers.PreferenceHelper;
import mono.hg.models.AppDetail;
import mono.hg.models.PinnedAppDetail;

public class AppUtils {
    /**
     * Checks if a certain application is installed, regardless of their launch intent.
     *
     * @param packageManager PackageManager object to use for checking the requested
     *                       package's existence.
     * @param packageName    Application package name to check.
     *
     * @return boolean True or false depending on the existence of the package.
     */
    public static boolean isAppInstalled(PackageManager packageManager, String packageName) {
        try {
            // Get application info while handling exception spawning from it.
            packageManager.getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            // No, it's not installed.
            return false;
        }
    }

    /**
     * Checks with its package name, if an application is a system app, or is the app
     * is installed as a system app.
     *
     * @param packageManager PackageManager object used to receive application info.
     * @param componentName  Application package name to check against.
     *
     * @return boolean True if the application is a system app, false if otherwise.
     */
    public static boolean isSystemApp(PackageManager packageManager, String componentName) {
        try {
            ApplicationInfo appFlags = packageManager.getApplicationInfo(
                    getPackageName(componentName), 0);
            if ((appFlags.flags & ApplicationInfo.FLAG_SYSTEM) == 1) {
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Utils.sendLog(3, e.toString());
            return false;
        }
        return false;
    }

    /**
     * Pins an app to the favourites panel.
     *
     * @param packageManager PackageManager object used to fetch information regarding
     *                       the app that is being loaded.
     * @param componentName  The package name to load and fetch.
     * @param adapter        Which adapter should we notify update to?
     * @param list           Which List object should be updated?
     */
    public static void pinApp(PackageManager packageManager, String componentName,
            FlexibleAdapter<PinnedAppDetail> adapter, List<PinnedAppDetail> list) {
        if (!adapter.contains(new PinnedAppDetail(null, componentName))) {
            ComponentName iconComponent = ComponentName.unflattenFromString(componentName);

            try {
                Drawable icon;
                Drawable getIcon = null;

                if (!PreferenceHelper.getIconPackName().equals("default")) {
                    getIcon = LauncherIconHelper.getIconDrawable(packageManager,
                            componentName);
                }
                if (getIcon == null) {
                    icon = packageManager.getActivityIcon(iconComponent);
                } else {
                    icon = getIcon;
                }

                PinnedAppDetail app = new PinnedAppDetail(icon, componentName);
                list.add(app);
                adapter.updateDataSet(list, false);
            } catch (PackageManager.NameNotFoundException e) {
                Utils.sendLog(3, e.toString());
            }
        }
    }

    /**
     * Launches an app as a new task.
     *
     * @param componentName The package name of the app.
     */
    public static void launchApp(Activity activity, String componentName) {
        ComponentName component = ComponentName.unflattenFromString(componentName);
        Intent intent = Intent.makeMainActivity(component);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Attempt to catch exceptions instead of crash landing directly to the floor.
        try {
            activity.startActivity(intent);

            // Override app launch animation when needed.
            switch (PreferenceHelper.getLaunchAnim()) {
                case "pull_up":
                    activity.overridePendingTransition(R.anim.pull_up, 0);
                    break;
                case "slide_in":
                    activity.overridePendingTransition(R.anim.slide_in, 0);
                    break;
                default:
                case "default":
                    // Don't override when we have the default value.
                    break;
            }
        } catch (ActivityNotFoundException | NullPointerException e) {
            Toast.makeText(activity, R.string.err_activity_null, Toast.LENGTH_LONG).show();
            Utils.sendLog(3, "Cannot start " + componentName + "; missing package?");
        }
    }

    /**
     * Get simple package name from a flattened ComponentName.
     *
     * @param componentName The flattened ComponentName whose package name is to be returned.
     *
     * @return String The package name if not null.
     */
    public static String getPackageName(String componentName) {
        ComponentName unflattened = ComponentName.unflattenFromString(componentName);
        if (unflattened != null) {
            return unflattened.getPackageName();
        } else {
            return "";
        }
    }

    /**
     * Counts the number of installed package in the system.
     *
     * @param packageManager PackageManager to use for counting the list of installed packages.
     *
     * @return The number of installed packages. Zero if any exception occurs.
     */
    public static int countInstalledPackage(PackageManager packageManager) {
        return packageManager.getInstalledApplications(0).size();
    }

    /**
     * Checks if there has been a new package installed into the device.
     *
     * @param packageManager PackageManager used to count the installed packages, which are then
     *                       compared to the internal count saved by the launcher during its onCreate.
     *
     * @return boolean True if there is a change in number.
     */
    public static boolean hasNewPackage(PackageManager packageManager) {
        if (PreferenceHelper.getPreference().getInt("package_count", 0) != countInstalledPackage(
                packageManager)) {
            PreferenceHelper.getEditor()
                            .putInt("package_count", countInstalledPackage(packageManager)).apply();
            return true;
        }
        return false;
    }

    /**
     * Populates the internal app list. This method must be loaded asynchronously to avoid
     * performance degradation.
     *
     * @param activity The activity where the app list resides and is needed.
     *
     * @return List an AppDetail List containing the app list itself
     */
    public static List<AppDetail> loadApps(Activity activity) {
        PackageManager manager = activity.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        List<AppDetail> appsList = new ArrayList<>();

        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> availableActivities = manager.queryIntentActivities(intent, 0);

        if (PreferenceHelper.isListInverted()) {
            Collections.sort(availableActivities, Collections
                    .reverseOrder(new ResolveInfo.DisplayNameComparator(manager)));
        } else {
            Collections.sort(availableActivities, new ResolveInfo.DisplayNameComparator(manager));
        }

        // Fetch and add every app into our list, but ignore those that are in the exclusion list.
        for (ResolveInfo ri : availableActivities) {
            String packageName = ri.activityInfo.packageName + "/" + ri.activityInfo.name;
            if (!PreferenceHelper.getExclusionList().contains(packageName) && !packageName.contains(
                    activity.getPackageName())) {
                String appName = ri.loadLabel(manager).toString();
                Drawable icon = null;
                Drawable getIcon = null;
                // Only show icons if user chooses so.
                if (!PreferenceHelper.shouldHideIcon()) {
                    if (!PreferenceHelper.getIconPackName().equals("default")) {
                        getIcon = LauncherIconHelper.getIconDrawable(manager, packageName);
                    }
                    if (getIcon == null) {
                        icon = ri.activityInfo.loadIcon(manager);
                        if (PreferenceHelper.appTheme().equals("light")
                                && PreferenceHelper.shadeAdaptiveIcon()
                                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                && icon instanceof AdaptiveIconDrawable) {
                            icon = LauncherIconHelper.drawAdaptiveShadow(icon);
                        }
                    } else {
                        icon = getIcon;
                    }
                }
                AppDetail app = new AppDetail(icon, appName, packageName, false);
                appsList.add(app);
            }
        }
        return appsList;
    }
}
