package com.offsec.nethunter.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.util.Log;
import com.offsec.nethunter.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class CopyBootFiles extends AsyncTask<String, String, String> {

    private String COPY_ASSETS_TAG = "COPY_ASSETS_TAG";
    private Context ctx;
    private NhPaths nh;
    private File sdCardDir;
    private File scriptsDir;
    private File etcDir;
    private SharedPreferences prefs;

    private String buildTime;
    private Boolean shouldRun;
    private ProgressDialog pd;
    public CopyBootFiles(Context _ctx){
        this.ctx = _ctx;
        this.nh = new NhPaths();


        this.sdCardDir = new File(nh.APP_SD_FILES_PATH);
        this.scriptsDir = new File(nh.APP_SCRIPTS_PATH);
        this.etcDir = new File(nh.APP_INITD_PATH);
        this.prefs = ctx.getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd KK:mm:ss a zzz",
                Locale.US);
        this.buildTime = sdf.format(BuildConfig.BUILD_TIME);
        this.shouldRun = true;
    }

    @Override
    protected void onPreExecute() {
        // setup
        if (!prefs.getString(COPY_ASSETS_TAG, buildTime).equals(buildTime) || !sdCardDir.isDirectory() || !scriptsDir.isDirectory() || !etcDir.isDirectory()) {
            pd = new ProgressDialog(ctx);
            pd.setTitle("New app build detected:");
            pd.setMessage("Coping new files...");
            pd.setCancelable(false);
            pd.show();
        } else {
            Log.d(COPY_ASSETS_TAG, "FILES NOT COPIED");
            shouldRun = false;
        }
    }
    @Override
    protected String doInBackground(String... data) {
          if(shouldRun){

              Log.d(COPY_ASSETS_TAG, "COPING FILES....");
              // 1:1 copy (recursive) of the assets/{scripts, etc, wallpapers} folders to /data/data/...
              publishProgress("Doing app files update. (init.d and filesDir).");
              assetsToFiles(nh.APP_PATH, "", "data");
              // 1:1 copy (recursive) of the configs to  /sdcard...
              publishProgress("Doing sdcard files update. (nh_files).");
              assetsToFiles(nh.SD_PATH, "", "sdcard");
              ShellExecuter exe = new ShellExecuter();
              publishProgress("Fixing permissions for new files");
              exe.RunAsRoot(new String[]{"chmod 700 " + nh.APP_SCRIPTS_PATH + "/*", "chmod 700 " + nh.APP_INITD_PATH + "/*"});
              SharedPreferences.Editor ed = prefs.edit();
              ed.putString(COPY_ASSETS_TAG, buildTime);
              ed.commit();

              return "All files copied.";
          } else {
              cancel(true);
          }
          return "No file copy needed.";
    }
    @Override
    protected void onProgressUpdate(String... progress) {
        pd.setMessage(progress[0]);
    }
    @Override
    protected void onPostExecute(final String result) {
        Log.d("Res_copyAssets:", result);
        if (pd != null) {
            new android.os.Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            pd.dismiss();
                        }
                    }, 1000);
        }
    }
    private Boolean pathIsAllowed(String path, String copyType) {
        // never copy images, sounds or webkit
        if (!path.startsWith("images") && !path.startsWith("sounds") && !path.startsWith("webkit")) {
            if (copyType.equals("sdcard")) {
                if (path.equals("")) {
                    return true;
                } else if (path.startsWith(nh.NH_SD_FOLDER_NAME)) {
                    return true;
                }
                return false;
            }
            if (copyType.equals("data")) {
                if (path.equals("")) {
                    return true;
                } else if (path.startsWith("scripts")) {
                    return true;
                } else if (path.startsWith("wallpapers")) {
                    return true;
                } else if (path.startsWith("etc")) {
                    return true;
                }
                return false;
            }
            return false;
        }
        return false;
    }

    // now this only copies the folders: scripts, etc , wallpapers to /data/data...
    private void assetsToFiles(String TARGET_BASE_PATH, String path, String copyType) {
        AssetManager assetManager = ctx.getAssets();
        String assets[];
        try {
            // Log.i("tag", "assetsTo" + copyType +"() "+path);
            assets = assetManager.list(path);
            if (assets.length == 0) {
                copyFile(TARGET_BASE_PATH, path);
            } else {
                String fullPath = TARGET_BASE_PATH + "/" + path;
                // Log.i("tag", "path="+fullPath);
                File dir = new File(fullPath);
                if (!dir.exists() && pathIsAllowed(path, copyType)) { // copy thouse dirs
                    if (!dir.mkdirs()) {
                        Log.i("tag", "could not create dir " + fullPath);
                    }
                }
                for (String asset : assets) {
                    String p;
                    if (path.equals("")) {
                        p = "";
                    } else {
                        p = path + "/";
                    }
                    if (pathIsAllowed(path, copyType)) {
                        assetsToFiles(TARGET_BASE_PATH, p + asset, copyType);
                    }
                }
            }
        } catch (IOException ex) {
            Log.e("tag", "I/O Exception", ex);
        }
    }

    private void copyFile(String TARGET_BASE_PATH, String filename) {
        AssetManager assetManager = ctx.getAssets();

        InputStream in;
        OutputStream out;
        String newFileName = null;
        try {
            // Log.i("tag", "copyFile() "+filename);
            in = assetManager.open(filename);
            newFileName = TARGET_BASE_PATH + "/" + filename;
            out = new FileOutputStream(newFileName);
            byte[] buffer = new byte[8092];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();
        } catch (Exception e) {
            Log.e("tag", "Exception in copyFile() of " + newFileName);
            Log.e("tag", "Exception in copyFile() " + e.toString());
        }

    }
}