package com.example.edgesum.util.video;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.MediaInformation;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.file.FileManager;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class FfmpegTools {
    private static final String TAG = FfmpegTools.class.getSimpleName();
    private static final char SEGMENT_SEPARATOR = '_';
    public static final int SEGMENT_NUM = 4; // TODO Make into preference value

    public static void executeFfmpeg(ArrayList<String> ffmpegArgs) {
        Log.i(TAG, String.format("Running ffmpeg with:\n  %s", TextUtils.join(" ", ffmpegArgs)));
        FFmpeg.execute(ffmpegArgs.toArray(new String[0]));
    }

    public static Double getDuration(String filePath) {
        MediaInformation info = FFmpeg.getMediaInformation(filePath);

        if (info != null && info.getDuration() != null) {
            return info.getDuration() / 1000.0;
        } else {
            Log.e(TAG, String.format("ffmpeg-mobile error, could not retrieve duration of %s", filePath));
            return 0.0;
        }
    }

    private static List<String> getChildPaths(File dir) {
        File[] files = dir.listFiles();

        if (files == null) {
            Log.e(TAG, String.format("Could not access contents of %s", dir.getAbsolutePath()));
            return null;
        }
        return Arrays.stream(files).map(File::getAbsolutePath).sorted(String::compareTo).collect(Collectors.toList());
    }

    // Get base video name from split segment's name
    public static String getBaseName(String segmentName) {
        return segmentName.substring(0, segmentName.lastIndexOf(SEGMENT_SEPARATOR));
    }

    public static List<Video> splitAndReturn(Context context, String filePath, int segNum) {
        if (segNum < 2) {
            return new ArrayList<>(Collections.singletonList(
                    VideoManager.getVideoFromPath(context, filePath)
            ));
        }
        splitVideo(filePath, segNum);
        String baseVideoName = FilenameUtils.getBaseName(filePath);
        String segmentDirPath = FileManager.getSegmentDirPath(baseVideoName);

        return VideoManager.getVideosFromDir(context, segmentDirPath);
    }

    private static void splitVideo(String filePath, int segNum) {
        if (segNum < 2) {
            return;
        }

        // Round segment time up to ensure that the number of split videos doesn't exceed segNum
        int segTime = (int) Math.ceil(FfmpegTools.getDuration(filePath) / segNum);
        String outDir = FileManager.getSegmentDirPath(FilenameUtils.getBaseName(filePath));

        ArrayList<String> ffmpegArgs = new ArrayList<>(Arrays.asList(
                "-y",
                "-i", filePath,
                "-map", "0",
                "-c", "copy",
                "-f", "segment",
                "-reset_timestamps", "1", // Necessary for freezedetect, otherwise timestamps will be incorrect
                "-segment_time", String.valueOf(segTime),
                String.format(Locale.ENGLISH, "%s/%s%s%%03d.%s",
                        outDir,
                        FilenameUtils.getBaseName(filePath),
                        SEGMENT_SEPARATOR,
                        FilenameUtils.getExtension(filePath))
        ));
        FfmpegTools.executeFfmpeg(ffmpegArgs);
    }

    private static void makePathsFile(List<String> vidPaths, String filename) {
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(filename));

            for (String vidPath : vidPaths) {
                writer.println(String.format("file '%s'", vidPath));
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String mergeVideos(List<String> vidPaths, String output) {
        Log.d(TAG, String.format("Merging %s", output));

        if (vidPaths == null || vidPaths.size() == 0) {
            Log.d(TAG, "No split videos to merge");
            return null;
        }
        if (vidPaths.size() == 1) { // Only one video, no need to merge so just copy
            try {
                FileManager.copy(new File(vidPaths.get(0)), new File(output));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String pathFilename = String.format("%s/paths.txt",
                FileManager.getSegmentDirPath(FilenameUtils.getBaseName(output)));
        makePathsFile(vidPaths, pathFilename);

        ArrayList<String> ffmpegArgs = new ArrayList<>(Arrays.asList(
                "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", pathFilename,
                "-c", "copy",
                output
        ));

        FfmpegTools.executeFfmpeg(ffmpegArgs);
        return output;
    }

    public static String mergeVideos(String parentName) {
        String baseName = FilenameUtils.getBaseName(parentName);
        List<String> vidPaths = getChildPaths(new File(FileManager.getSegmentSumDirPath(baseName)));

        if (vidPaths != null) {
            return mergeVideos(vidPaths, String.format("%s/%s", FileManager.getSummarisedDirPath(), parentName));
        }
        return null;
    }
}
