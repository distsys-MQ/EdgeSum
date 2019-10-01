package com.example.myfirstapp.data;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.myfirstapp.model.Video;

import java.util.List;

public class VideoViewModel extends ViewModel {

    private VideosRepository repository;

    private LiveData<List<Video>> videos;

    public VideoViewModel(Application application, VideosRepository videosRepository) {
        super();
        repository = videosRepository;
        videos = repository.getVideos();

    }


    public LiveData<List<Video>> getVideos() {
        return videos;
    }

    public void insert(Video video) {
        repository.insert(video);
    }

    public void remove(int position) {
        repository.delete(position);
    }
}
