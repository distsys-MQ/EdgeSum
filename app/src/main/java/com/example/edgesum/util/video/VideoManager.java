package com.example.edgesum.util.video;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import com.example.edgesum.model.Video;
import com.example.edgesum.util.devicestorage.DeviceExternalStorage;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VideoManager {
    private final static String TAG = VideoManager.class.getSimpleName();

    private VideoManager() {
    }

    public static List<Video> getAllVideoFromExternalStorageFolder(Context context, File file) {
        Log.v(TAG, "getAllVideoFromExternalStorageFolder");
        String[] projection = {MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE
        };
        if (DeviceExternalStorage.externalStorageIsReadable()) {
            String selection = MediaStore.Video.Media.DATA + " LIKE ? ";
            String[] selectionArgs = new String[]{"%" + file.getAbsolutePath() + "%"};
            Log.d("VideoManager", file.getAbsolutePath());
            return getVideosFromExternalStorage(context, projection, selection, selectionArgs, null);
        }
        return new ArrayList<>();
    }

    public static List<Video> getVideosFromDir(Context context, File dir) {
        if (!dir.isDirectory()) {
            Log.e(TAG, String.format("%s is not a directory", dir.getAbsolutePath()));
            return null;
        }

        File[] videoFiles = dir.listFiles();
        if (videoFiles == null) {
            Log.e(TAG, String.format("Could not access contents of %s", dir.getAbsolutePath()));
            return null;
        }

        List<String> vidPaths = Arrays.stream(videoFiles).map(File::getAbsolutePath).collect(Collectors.toList());
        List<Video> videos = new ArrayList<>();
        for (String vidPath : vidPaths) {
            videos.add(getVideoFromPath(context, vidPath));
        }
        return videos;
    }

    public static Video getVideoFromFile(Context context, File file) {
        String[] projection = {MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE
        };
        String selection = MediaStore.Video.Media.DATA + "=?";
        String[] selectionArgs = new String[]{file.getAbsolutePath()};
        Cursor videoCursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null);

        if (videoCursor == null || !videoCursor.moveToFirst()) {
            Log.e(TAG, "videoCursor is null");
            return null;
        }
        Video video = videoFromCursor(videoCursor);
        videoCursor.close();
        return video;
    }

    private static Video getVideoFromPath(Context context, String path) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.TITLE, FilenameUtils.getBaseName(path));
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "player");
        values.put(MediaStore.Images.Media.DESCRIPTION, "");
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        }
        values.put(MediaStore.Video.Media.DATA, path);
        context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        return getVideoFromFile(context, new File(path));
    }


    public static List<Video> getAllVideosFromExternalStorage(Context context, String[] projection) {
        Log.v(TAG, "getAllVideosFromExternalStorage");
        if (DeviceExternalStorage.externalStorageIsReadable()) {
            return getVideosFromExternalStorage(context, projection, null, null, null);
        }
        return new ArrayList<>();
    }

    private static List<Video> getVideosFromExternalStorage(Context context, String[] projection, String selection,
                                                            String[] selectionArgs, String sortOrder) {
        Cursor videoCursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder);

        if (videoCursor != null) {
            List<Video> videos = new ArrayList<>(videoCursor.getCount());
            getVideosFromCursor(videoCursor, videos);
            videoCursor.close();
            Log.d(TAG, String.format("%d videos return", videos.size()));
            return videos;
        } else {
            return null;
        }
    }

    private static void getVideosFromCursor(Cursor videoCursor, List<Video> videos) {
        boolean cursorIsNotEmpty = videoCursor.moveToFirst();
        if (cursorIsNotEmpty) {
            do {
                Video video = videoFromCursor(videoCursor);
                if (video != null) {
                    videos.add(video);
                    Log.d(TAG, video.toString());
                } else {
                    Log.e(TAG, "Video is null");
                }
            } while (videoCursor.moveToNext());
        }
    }

    private static Video videoFromCursor(Cursor cursor) {
        Log.v(TAG, "videoFromCursor");
        Video video = null;
        try {
            String id = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media._ID));
            String name = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME));
            String data = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA));
            BigInteger size = new BigInteger(cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.SIZE)));
            String mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE));
            video = new Video(id, name, data, mimeType, size);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return video;
    }
}
