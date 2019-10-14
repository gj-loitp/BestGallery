package com.eagle.gallery.pro.task;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.eagle.gallery.pro.models.Directory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class BaseLoadPhotoTask extends BaseAsyncTask<Void, ArrayList<Directory>> {
    private Context mContext;

    public BaseLoadPhotoTask(Callback<ArrayList<Directory>> callback, Context context) {
        super(callback);
        mContext = context.getApplicationContext();
    }

    private static boolean isNotImageFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return true;
        }

        File file = new File(path);
        return !file.exists() || file.length() == 0;
    }

    @Override
    protected ArrayList<Directory> doInBackground(Void... voids) {
        long startTime = System.currentTimeMillis();

        ArrayList<Directory> imageFolderModels = new ArrayList<>();

        HashMap<String, Directory> folderMap = new HashMap<>();

        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media.DATA},
                    MediaStore.Images.Media.MIME_TYPE + "=? or " + MediaStore.Images.Media.MIME_TYPE + "=? or " + MediaStore.Images.Media.MIME_TYPE + "=?",
                    new String[]{"image/jpeg", "image/png", "image/jpg"},
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
            );

            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    String imagePath = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));

                    if (isNotImageFile(imagePath)) {
                        continue;
                    }

                    String folderPath = null;
                    // 其他图片目录
                    File folder = new File(imagePath).getParentFile();
                    if (folder != null) {
                        folderPath = folder.getAbsolutePath();
                    }

                    if (TextUtils.isEmpty(folderPath)) {
                        int end = imagePath.lastIndexOf(File.separator);
                        if (end != -1) {
                            folderPath = imagePath.substring(0, end);
                        }
                    }

                    if (!TextUtils.isEmpty(folderPath)) {
                        if (folderMap.containsKey(folderPath)) {
                            Directory directory = folderMap.get(folderPath);
                            directory.setMediaCnt(directory.getMediaCnt() + 1);
                        } else {
                            String folderName = folderPath.substring(folderPath.lastIndexOf(File.separator) + 1);
                            if (TextUtils.isEmpty(folderName)) {
                                folderName = "/";
                            }
                            Directory directory = new Directory();
                            directory.setId(System.currentTimeMillis());
                            directory.setPath(folderPath);
                            directory.setName(folderName);
                            directory.setTmb(imagePath);
                            directory.setMediaCnt(1);
                            directory.setModified(folder != null ? folder.lastModified() : 0);
                            folderMap.put(folderPath, directory);
                        }
                    }
                }

                // 添加其他图片目录
                imageFolderModels.addAll(folderMap.values());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        Log.w("PhotoTask", "waste time is :" + (System.currentTimeMillis() - startTime));
        return imageFolderModels;
    }

    public BaseLoadPhotoTask perform() {
        if (Build.VERSION.SDK_INT >= 11) {
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            execute();
        }
        return this;
    }
}
